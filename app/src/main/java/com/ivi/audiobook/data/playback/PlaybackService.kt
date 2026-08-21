package com.ivi.audiobook.data.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.ivi.audiobook.domain.repository.BookRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var bookRepository: BookRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(ResumptionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    /**
     * Without this, any play() request that reaches the session while no media item is loaded
     * (e.g. the system's media-resumption affordance) hits Media3's default callback, which
     * throws UnsupportedOperationException. We resume the most recently opened book instead.
     */
    private inner class ResumptionCallback : MediaSession.Callback {
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                val book = bookRepository.getMostRecentlyOpenedBook()
                val result = if (book != null) {
                    val item = MediaItem.Builder()
                        .setUri(Uri.fromFile(File(book.filePath)))
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(book.title).setArtist(book.author).build())
                        .build()
                    MediaSession.MediaItemsWithStartPosition(listOf(item), 0, book.positionMs)
                } else {
                    MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
                }
                future.set(result)
            }
            return future
        }
    }
}
