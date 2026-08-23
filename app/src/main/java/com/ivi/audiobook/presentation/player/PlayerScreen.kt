package com.ivi.audiobook.presentation.player

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PlayerScreen(
    bookId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(bookId) { viewModel.load(bookId) }

    BackHandler {
        viewModel.closePlayer()
        onBack()
    }

    CompactPlayerBar(
        uiState = uiState,
        onSeekPreview = viewModel::seekPreview,
        onSeekFinished = viewModel::seekFinished,
        modifier = modifier,
    )
}
