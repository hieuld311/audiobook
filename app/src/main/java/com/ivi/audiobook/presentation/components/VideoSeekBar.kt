package com.ivi.audiobook.presentation.components

// Ported from CoWatch's com.ivi.common.ui.player.VideoSeekBar — same track/fill/handle bitmap
// assets and the same onSeekPreview (during drag)/onSeekFinished (on release) split.
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ivi.audiobook.R
import kotlin.math.roundToInt

val SEEK_BAR_VISUAL_HEIGHT = 40.dp

private val SEEK_HANDLE_SIZE = 40.dp
private val SEEK_TOUCH_HEIGHT = 16.dp
private val SEEK_TRACK_HEIGHT = 6.dp

@Composable
fun VideoSeekBar(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeekPreview: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableIntStateOf(1) }
    var isPressing by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val handleSizePx = with(density) { SEEK_HANDLE_SIZE.toPx() }
    val handleRadiusPx = handleSizePx / 2f
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val handleCenterXPx = widthPx * progress

    fun positionFromX(x: Float): Long {
        if (durationMs <= 0L || widthPx <= 0) return 0L
        val fraction = (x / widthPx).coerceIn(0f, 1f)
        return (durationMs * fraction).toLong()
    }

    Box(modifier = modifier.height(SEEK_BAR_VISUAL_HEIGHT).onSizeChanged { widthPx = it.width.coerceAtLeast(1) }) {
        Box(modifier = Modifier.align(Alignment.Center).fillMaxWidth().height(SEEK_TRACK_HEIGHT)) {
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
                        clipRect(right = size.width * progress) {
                            this@drawWithContent.drawContent()
                        }
                    },
                contentScale = ContentScale.FillBounds,
            )
        }

        Image(
            painter = painterResource(if (isPressing || isDragging) R.drawable.img_general_slider_handle_p else R.drawable.img_general_slider_handle_n),
            contentDescription = null,
            modifier = Modifier
                .size(SEEK_HANDLE_SIZE)
                .offset { IntOffset(x = (handleCenterXPx - handleRadiusPx).roundToInt(), y = 0) },
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(SEEK_TOUCH_HEIGHT)
                .pointerInput(enabled, durationMs, widthPx) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            isPressing = true
                            tryAwaitRelease()
                            isPressing = false
                        },
                        onTap = { offset ->
                            onSeekPreview(positionFromX(offset.x))
                            onSeekFinished()
                        },
                    )
                }
                .pointerInput(enabled, durationMs, widthPx) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset -> isDragging = true; onSeekPreview(positionFromX(offset.x)) },
                        onDrag = { change, _ -> change.consume(); onSeekPreview(positionFromX(change.position.x)) },
                        onDragEnd = { isDragging = false; onSeekFinished() },
                        onDragCancel = { isDragging = false; onSeekFinished() },
                    )
                },
        )
    }
}
