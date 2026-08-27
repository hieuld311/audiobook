package com.ivi.audiobook.presentation.player

import androidx.lifecycle.ViewModel
import com.ivi.audiobook.data.playback.PlaybackController
import com.ivi.audiobook.data.playback.PlaybackUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackController: PlaybackController,
) : ViewModel() {

    val uiState: StateFlow<PlaybackUiState> = playbackController.uiState

    fun start() = playbackController.start()
    fun togglePlayPause() = playbackController.togglePlayPause()
    fun seekPreview(positionMs: Long) = playbackController.previewSeek(positionMs)
    fun seekFinished() = playbackController.finishSeek()
}
