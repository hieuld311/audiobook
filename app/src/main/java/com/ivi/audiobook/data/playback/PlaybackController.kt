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
import com.ivi.audiobook.data.lyrics.Mp4LyricsExtractor
import com.ivi.audiobook.domain.model.Book
import com.ivi.audiobook.domain.model.LyricLine
import com.ivi.audiobook.domain.repository.BookRepository
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackUiState(
    val bookId: Long? = null,
    val title: String = "",
    val author: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val lyrics: List<LyricLine> = emptyList(),
)

private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
private const val POSITION_POLL_INTERVAL_MS = 500L

/**
 * Single app-wide connection to [PlaybackService]'s [MediaController]. Owns playback UI state and
 * mirrors Helium's progress-persistence pattern: position is saved periodically while playing and
 * on every seek, with the "touched" timestamp bumped only when the position actually changes.
 */
@Singleton
class PlaybackController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controller: MediaController? = null
    private val controllerReady = CompletableDeferred<MediaController>()
    private var currentBookId: Long? = null
    private var lastSavedPositionMs: Long = -1
    private var pollJob: Job? = null

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
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

    fun playBook(book: Book) {
        currentBookId = book.id
        lastSavedPositionMs = book.positionMs
        _uiState.value = PlaybackUiState(
            bookId = book.id,
            title = book.title,
            author = book.author,
            durationMs = book.durationMs,
            positionMs = book.positionMs,
        )

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(File(book.filePath)))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(book.title).setArtist(book.author).build())
            .build()

        // The MediaController connects asynchronously; right after a fresh process start it may
        // not be ready yet. Awaiting it here (instead of a null-checked no-op) avoids silently
        // dropping the load — the book would never actually reach the player, and the position
        // poll loop would then overwrite the UI's correct initial position with the empty
        // player's 0.
        scope.launch {
            val c = controllerReady.await()
            if (currentBookId != book.id) return@launch
            c.setMediaItem(mediaItem, book.positionMs)
            c.prepare()
            c.play()
            bookRepository.markOpened(book.id)
            startPositionPolling()
        }

        // Only files with an embedded LRC-format ©lyr atom (music files tagged that way, not
        // audiobooks in general) will produce anything here; everything else just gets an empty
        // list and the script view has nothing to show.
        scope.launch {
            val lyrics = withContext(Dispatchers.IO) { Mp4LyricsExtractor.extractLyrics(book.filePath) } ?: emptyList()
            if (currentBookId == book.id) {
                _uiState.value = _uiState.value.copy(lyrics = lyrics)
            }
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
            // Pausing is the moment most likely to be followed by the app/process being killed
            // (user backgrounds it to do something else), so this write must actually land
            // before returning control — fire-and-forget here risks losing it to process death.
            val id = currentBookId
            val position = c.currentPosition
            lastSavedPositionMs = position
            if (id != null) {
                runBlocking { bookRepository.updatePlaybackProgress(id, position, true) }
            }
        } else {
            c.play()
        }
    }

    /** Leaving the Player screen via the close button stops playback outright (not just a
     * background pause) and blocks until the final position is saved, same durability guarantee
     * as [togglePlayPause]'s pause path. */
    fun closePlayer() {
        val c = controller ?: return
        pollJob?.cancel()
        val id = currentBookId
        val position = c.currentPosition
        c.stop()
        if (id != null) {
            runBlocking { bookRepository.updatePlaybackProgress(id, position, true) }
        }
        // Reset so reopening this same book (or any book) goes through playBook() again instead
        // of PlayerViewModel.load()'s "already this bookId" guard silently no-op'ing against a
        // now-stopped player.
        currentBookId = null
        _uiState.value = PlaybackUiState()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0))
        persistPosition(positionMs, touched = true)
    }

    // Mirrors CoWatch's previewSeek/finishSeek split: previewSeek only moves the UI position and
    // pauses once at the start of the gesture; the real seek only lands in finishSeek. A tap
    // (no drag) just calls both back-to-back. Doing a real seekTo() on every drag frame would
    // spam the player over IPC and fight the position-poll loop for no benefit.
    private var isSeekPreviewActive = false
    private var resumePlaybackAfterSeek = false

    fun previewSeek(positionMs: Long) {
        val c = controller ?: return
        if (!isSeekPreviewActive) {
            resumePlaybackAfterSeek = c.isPlaying
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
        if (resumePlaybackAfterSeek) controller?.play()
        resumePlaybackAfterSeek = false
        persistPosition(position, touched = true)
    }

    fun playPreviousBook() = playAdjacentBook(-1)
    fun playNextBook() = playAdjacentBook(1)

    private fun playAdjacentBook(direction: Int) {
        val id = currentBookId ?: return
        val c = controller
        // Persist the outgoing book's final position before switching — same durability
        // guarantee as closePlayer(), since we're leaving this book behind just as surely.
        if (c != null) {
            runBlocking { bookRepository.updatePlaybackProgress(id, c.currentPosition, true) }
        }
        scope.launch {
            val ids = bookRepository.getOrderedBookIds()
            val index = ids.indexOf(id)
            if (index == -1) return@launch
            val newIndex = index + direction
            if (newIndex !in ids.indices) return@launch
            val book = bookRepository.getBook(ids[newIndex]) ?: return@launch
            playBook(book)
        }
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
                    if (c.isPlaying && position - lastSavedPositionMs >= PROGRESS_SAVE_INTERVAL_MS) {
                        persistPosition(position, touched = true)
                    }
                }
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }
    }

    private fun persistPosition(positionMs: Long, touched: Boolean) {
        val id = currentBookId ?: return
        lastSavedPositionMs = positionMs
        scope.launch { bookRepository.updatePlaybackProgress(id, positionMs, touched) }
    }
}
