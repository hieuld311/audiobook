package com.ivi.audiobook.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivi.audiobook.data.playback.PlaybackController
import com.ivi.audiobook.data.playback.PlaybackUiState
import com.ivi.audiobook.domain.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val bookRepository: BookRepository,
) : ViewModel() {

    val uiState: StateFlow<PlaybackUiState> = playbackController.uiState

    fun load(bookId: Long) {
        if (uiState.value.bookId == bookId) return
        viewModelScope.launch {
            bookRepository.getBook(bookId)?.let { playbackController.playBook(it) }
        }
    }

    fun togglePlayPause() = playbackController.togglePlayPause()
    fun seekPreview(positionMs: Long) = playbackController.previewSeek(positionMs)
    fun seekFinished() = playbackController.finishSeek()
    fun playNextBook() = playbackController.playNextBook()
    fun playPreviousBook() = playbackController.playPreviousBook()
    fun closePlayer() = playbackController.closePlayer()
}
