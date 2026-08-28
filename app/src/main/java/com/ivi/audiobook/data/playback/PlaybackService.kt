package com.ivi.audiobook.data.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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
     * throws UnsupportedOperationException. There's only one built-in file, discovered the same
     * way as [PlaybackController.start] rather than a hardcoded name, so resumption always points
     * back at it, starting from 0.
     */
    private inner class ResumptionCallback : MediaSession.Callback {
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                val info = BuiltInAudioAsset.resolve(this@PlaybackService)
                val item = MediaItem.Builder()
                    .setUri(Uri.parse(info.assetUri))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(info.title).setArtist(info.author).build())
                    .build()
                future.set(MediaSession.MediaItemsWithStartPosition(listOf(item), 0, 0))
            }
            return future
        }
    }
}
