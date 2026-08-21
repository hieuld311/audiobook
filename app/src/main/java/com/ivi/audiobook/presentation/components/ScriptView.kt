package com.ivi.audiobook.presentation.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivi.audiobook.domain.model.LyricLine
import kotlin.math.abs

/**
 * Scrolling synced script: the active line sits at full opacity while past/upcoming lines fade
 * out with distance, giving a perspective/depth cue toward the current line rather than a flat
 * list. Display-only — lines aren't tappable.
 */
@Composable
fun ScriptView(
    lyrics: List<LyricLine>,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    val currentIndex = remember(lyrics, positionMs) {
        lyrics.indexOfLast { it.startMs <= positionMs }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val edgeSpace = maxHeight / 2
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = edgeSpace),
        ) {
            itemsIndexed(lyrics, key = { index, _ -> index }) { index, line ->
                val distance = abs(index - currentIndex)
                val lineAlpha = when {
                    index == currentIndex -> 1f
                    distance == 1 -> 0.55f
                    distance == 2 -> 0.32f
                    else -> 0.16f
                }
                Text(
                    text = line.text,
                    color = Color.White,
                    style = if (index == currentIndex) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(lineAlpha)
                        .padding(vertical = 10.dp, horizontal = 32.dp),
                )
            }
        }
    }
}
