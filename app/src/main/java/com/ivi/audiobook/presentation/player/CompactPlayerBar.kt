package com.ivi.audiobook.presentation.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ivi.audiobook.R
import com.ivi.audiobook.data.playback.PlaybackUiState
import com.ivi.audiobook.domain.model.LyricLine
import java.io.File

// Matches the sibling cluster-widget app that shares this same physical strip display (Ptop,
// 3840x203, com.ivi.widgetptop) — same pure-black canvas, same cyan accent (#00D7DB), same
// secondary-text gray and track color, so this bar reads as native to that display rather than a
// mismatched guest. That app's widget content box is 1701dp x 160dp with 24dp vertical padding
// within its half of the screen; this bar targets the same footprint.
private val StripAccentCyan = Color(0xFF00D7DB)
private val StripSecondaryText = Color(0xFFB7B7C2)
private val StripTrackColor = Color(0xFF393B4A)

// Exact PHUD layout areas as specified: the cover+title+progress block is bounded in 567x208,
// the lyric preview in 1279x208 next to it.
private val LEFT_BLOCK_WIDTH = 567.dp
private val LEFT_BLOCK_HEIGHT = 208.dp
private val RIGHT_BLOCK_WIDTH = 1279.dp
private val RIGHT_BLOCK_HEIGHT = 208.dp
private val COVER_BACKDROP_SIZE = 128.dp
private val COVER_SIZE = 96.5.dp
private val PROGRESS_TRACK_HEIGHT = 6.dp
private val PROGRESS_TOUCH_HEIGHT = 28.dp

@Composable
fun CompactPlayerBar(
    uiState: PlaybackUiState,
    onSeekPreview: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color.Black)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.size(LEFT_BLOCK_WIDTH, LEFT_BLOCK_HEIGHT), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverThumbnail(uiState.coverPath)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = uiState.title,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = uiState.author ?: "Unknown author",
                        color = StripSecondaryText,
                        fontSize = 22.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            CompactProgressBar(
                positionMs = uiState.positionMs,
                durationMs = uiState.durationMs,
                onSeekPreview = onSeekPreview,
                onSeekFinished = onSeekFinished,
            )
        }

        Spacer(Modifier.width(16.dp))

        CompactScriptPreview(
            lyrics = uiState.lyrics,
            positionMs = uiState.positionMs,
            modifier = Modifier.size(RIGHT_BLOCK_WIDTH, RIGHT_BLOCK_HEIGHT),
        )
    }
}

// Bounded in the same circular glow backdrop CoWatch uses behind its play button
// (img_button_play_background_n) so the cover reads as native to that asset family rather than a
// plain square thumbnail.
@Composable
private fun CoverThumbnail(coverPath: String?) {
    Box(modifier = Modifier.size(COVER_BACKDROP_SIZE), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.img_button_play_background_n),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        if (coverPath != null) {
            AsyncImage(
                model = File(coverPath),
                contentDescription = null,
                modifier = Modifier.size(COVER_SIZE).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(modifier = Modifier.size(COVER_SIZE).clip(CircleShape).background(StripTrackColor))
        }
    }
}

// Same track/filled-track bitmap assets and clipRect-reveal technique as VideoSeekBar, plus the
// same tap/drag gesture split (onSeekPreview during the gesture, onSeekFinished on release) —
// just at the compact strip's thinner scale, with a taller invisible touch target since the
// visible track itself is only 6dp.
@Composable
private fun CompactProgressBar(
    positionMs: Long,
    durationMs: Long,
    onSeekPreview: (Long) -> Unit,
    onSeekFinished: () -> Unit,
) {
    var widthPx by remember { mutableIntStateOf(1) }
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    fun positionFromX(x: Float): Long {
        if (durationMs <= 0L || widthPx <= 0) return 0L
        val frac = (x / widthPx).coerceIn(0f, 1f)
        return (durationMs * frac).toLong()
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(formatClock(positionMs), color = StripSecondaryText, fontSize = 14.sp)
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f).height(PROGRESS_TOUCH_HEIGHT), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PROGRESS_TRACK_HEIGHT)
                    .onSizeChanged { widthPx = it.width.coerceAtLeast(1) },
            ) {
                Image(
                    painter = painterResource(R.drawable.img_general_progress_bar_track),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
                Image(
                    painter = painterResource(R.drawable.img_general_progress_bar_filled_track),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            clipRect(right = size.width * fraction) {
                                this@drawWithContent.drawContent()
                            }
                        },
                    contentScale = ContentScale.FillBounds,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .pointerInput(durationMs, widthPx) {
                        detectTapGestures(
                            onTap = { offset ->
                                onSeekPreview(positionFromX(offset.x))
                                onSeekFinished()
                            },
                        )
                    }
                    .pointerInput(durationMs, widthPx) {
                        detectDragGestures(
                            onDragStart = { offset -> onSeekPreview(positionFromX(offset.x)) },
                            onDrag = { change, _ -> change.consume(); onSeekPreview(positionFromX(change.position.x)) },
                            onDragEnd = { onSeekFinished() },
                            onDragCancel = { onSeekFinished() },
                        )
                    },
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(formatClock(durationMs), color = StripSecondaryText, fontSize = 14.sp)
    }
}

/**
 * A compact, non-scrolling teleprompter preview: the current line at full brightness in the
 * accent color, followed by the next few upcoming lines fading out — there's no room in a 160dp
 * strip for the centered past/future layout the full-screen ScriptView uses. Our LRC data only
 * carries line-level timestamps (not per-word), so the whole current line highlights together
 * rather than a word-by-word karaoke sweep.
 */
@Composable
private fun CompactScriptPreview(lyrics: List<LyricLine>, positionMs: Long, modifier: Modifier = Modifier) {
    if (lyrics.isEmpty()) return

    val currentIndex = remember(lyrics, positionMs) {
        lyrics.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)
    }

    // AnimatedContent crossfades+slides the whole visible window on every line change, rather than
    // the lines just jump-cutting to their new text each time positionMs crosses a line boundary.
    AnimatedContent(
        targetState = currentIndex,
        transitionSpec = {
            (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 })
                .togetherWith(fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 4 })
        },
        modifier = modifier,
        label = "lyric_line_transition",
    ) { index ->
        val visibleLines = lyrics.drop(index).take(4)
        Column(verticalArrangement = Arrangement.Center) {
            visibleLines.forEachIndexed { lineOffset, line ->
                val isCurrent = lineOffset == 0
                Text(
                    text = line.text,
                    color = if (isCurrent) StripAccentCyan else Color.White,
                    fontSize = if (isCurrent) 22.sp else 18.sp,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(1f - lineOffset * 0.28f).padding(vertical = 2.dp),
                )
            }
        }
    }
}

private fun formatClock(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
