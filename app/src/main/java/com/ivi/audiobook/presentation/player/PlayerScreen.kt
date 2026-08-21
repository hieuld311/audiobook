package com.ivi.audiobook.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivi.audiobook.presentation.components.PlaybackControlBar
import com.ivi.audiobook.presentation.components.ScriptView

@Composable
fun PlayerScreen(
    bookId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(bookId) { viewModel.load(bookId) }

    fun closeAndGoBack() {
        viewModel.closePlayer()
        onBack()
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (uiState.lyrics.isNotEmpty()) {
            ScriptView(
                lyrics = uiState.lyrics,
                positionMs = uiState.positionMs,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = uiState.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = uiState.author ?: "Unknown author",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            IconButton(
                onClick = ::closeAndGoBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f)),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close player and back to library", tint = Color.White)
            }
        }

        PlaybackControlBar(
            positionMs = uiState.positionMs,
            durationMs = uiState.durationMs,
            isPlaying = uiState.isPlaying,
            controlsEnabled = true,
            onSeekPreview = viewModel::seekPreview,
            onSeekFinished = viewModel::seekFinished,
            onTogglePlayback = viewModel::togglePlayPause,
            onPreviousBook = viewModel::playPreviousBook,
            onNextBook = viewModel::playNextBook,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        )
    }
}
