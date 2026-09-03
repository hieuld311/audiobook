package com.ivi.audiobook.data.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.ivi.audiobook.domain.model.LyricLine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackUiState(
    val title: String = "",
    val author: String? = null,
    val coverPath: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val lyrics: List<LyricLine> = emptyList(),
    val isPlaybackEnded: Boolean = false,
)

private const val POSITION_POLL_INTERVAL_MS = 500L

/**
 * Single app-wide connection to [PlaybackService]'s [MediaController], scoped to the one built-in
 * audio file (discovered via [BuiltInAudioAsset], not a hardcoded name). No persistence layer:
 * position always starts at 0 on a fresh app launch.
 */
@Singleton
class PlaybackController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controller: MediaController? = null
    private val controllerReady = CompletableDeferred<MediaController>()
    private var pollJob: Job? = null
    private var started = false

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                _uiState.value = _uiState.value.copy(isPlaybackEnded = true)
            }
        }
    }

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener(
            {
                val c = future.get().also { it.addListener(playerListener) }
                controller = c
                controllerReady.complete(c)
            },
            MoreExecutors.directExecutor(),
        )
    }

    /** Loads and auto-plays the built-in file. Idempotent — later calls are a no-op so
     * re-entering the screen doesn't restart playback from 0. */
    fun start() {
        if (started) return
        started = true

        scope.launch {
            val info = BuiltInAudioAsset.resolve(context)
            _uiState.value = _uiState.value.copy(
                title = info.title,
                author = info.author,
                coverPath = info.coverPath,
                lyrics = info.lyrics,
            )

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(info.assetUri))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(info.title).setArtist(info.author).build())
                .build()

            val c = controllerReady.await()
            c.setMediaItem(mediaItem)
            c.prepare()
            c.play()
            startPositionPolling()
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    // Mirrors CoWatch's previewSeek/finishSeek split: previewSeek only moves the UI position and
    // pauses once at the start of the gesture; the real seek only lands in finishSeek, which
    // always resumes playback on release since there's no play/pause button in the compact bar.
    private var isSeekPreviewActive = false

    fun previewSeek(positionMs: Long) {
        val c = controller ?: return
        if (!isSeekPreviewActive) {
            isSeekPreviewActive = true
            c.pause()
        }
        val bounded = positionMs.coerceIn(0, _uiState.value.durationMs.coerceAtLeast(0))
        _uiState.value = _uiState.value.copy(positionMs = bounded)
    }

    fun finishSeek() {
        if (!isSeekPreviewActive) return
        val position = _uiState.value.positionMs
        controller?.seekTo(position)
        isSeekPreviewActive = false
        controller?.play()
    }

    private fun startPositionPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                controller?.let { c ->
                    val position = c.currentPosition
                    val duration = c.duration.coerceAtLeast(0)
                    _uiState.value = _uiState.value.copy(
                        positionMs = position,
                        durationMs = if (duration > 0) duration else _uiState.value.durationMs,
                        isPlaying = c.isPlaying,
                    )
                }
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }
    }
}
