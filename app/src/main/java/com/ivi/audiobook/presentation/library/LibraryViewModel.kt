package com.ivi.audiobook.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivi.audiobook.data.library.UsbVolumeObserver
import com.ivi.audiobook.domain.model.Book
import com.ivi.audiobook.domain.model.LibraryQuery
import com.ivi.audiobook.domain.model.SortOrder
import com.ivi.audiobook.domain.model.SourceFilter
import com.ivi.audiobook.domain.usecase.ObserveBooks
import com.ivi.audiobook.domain.usecase.ScanLibrary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val query: LibraryQuery = LibraryQuery(),
    val isScanning: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val observeBooks: ObserveBooks,
    private val scanLibrary: ScanLibrary,
    private val usbVolumeObserver: UsbVolumeObserver,
) : ViewModel() {

    private val query = MutableStateFlow(LibraryQuery())
    private val isScanning = MutableStateFlow(false)

    val uiState: StateFlow<LibraryUiState> = combine(
        query.flatMapLatest { observeBooks(it) },
        query,
        isScanning,
    ) { books, currentQuery, scanning ->
        val visibleBooks = if (currentQuery.hideFinished) {
            books.filter { it.progressFraction < 0.98f }
        } else {
            books
        }
        LibraryUiState(books = visibleBooks, query = currentQuery, isScanning = scanning)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        refresh()
        // Rescan automatically when a USB/SD volume is attached or detached, instead of only at
        // launch or on the manual refresh button.
        usbVolumeObserver.events()
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    fun refresh() = scan(forced = false)

    /** The header bar's refresh button: an explicit "clean up my library" action. Unlike the
     * gentle auto-rescan, this actually removes books whose files are confirmed gone (mounted
     * volume, file missing) instead of just hiding them — see LibraryScanner.scan()'s doc. */
    fun resetLibrary() = scan(forced = true)

    private fun scan(forced: Boolean) {
        viewModelScope.launch {
            isScanning.value = true
            scanLibrary(forced)
            isScanning.value = false
        }
    }

    fun onSearchTextChange(text: String) = query.update { it.copy(searchText = text) }
    fun onSortOrderChange(order: SortOrder) = query.update { it.copy(sortOrder = order) }
    fun onSourceChange(source: SourceFilter) = query.update { it.copy(source = source) }
    fun onHideFinishedChange(hideFinished: Boolean) = query.update { it.copy(hideFinished = hideFinished) }
}
