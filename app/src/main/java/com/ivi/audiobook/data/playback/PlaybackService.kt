package com.ivi.audiobook.data.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

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
     * throws UnsupportedOperationException. There's only one built-in file, so resumption always
     * points back at it, starting from 0.
     */
    private inner class ResumptionCallback : MediaSession.Callback {
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val item = MediaItem.Builder()
                .setUri(Uri.parse(BUILT_IN_ASSET_URI))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(BUILT_IN_TITLE).setArtist(BUILT_IN_AUTHOR).build())
                .build()
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(listOf(item), 0, 0))
        }
    }
}
