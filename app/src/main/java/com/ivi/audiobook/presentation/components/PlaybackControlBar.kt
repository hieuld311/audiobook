package com.ivi.audiobook.presentation.components

// Ported from CoWatch's com.ivi.common.ui.player.PlaybackControlBar — same bottom-docked panel
// structure (background image + overlaid seek bar/time labels + transport row) and the same
// TransportControlsLayout centering trick (fixed-size center slot, two half-width side Rows) so
// the play button stays visually centered regardless of how many side buttons exist. CoWatch's
// video-specific bits (SeekFramePreview, video title overlay, speed cycling, PiP collapse,
// leading/trailing extension slots, and the hold-to-rewind/fast-forward gesture) are dropped;
// previous/next are tap-only, moving to the previous/next book in the library. Back-to-library is
// a separate X button elsewhere on screen, not part of this bar.
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ivi.audiobook.R

val CONTROL_BAR_HEIGHT = 154.dp
private val CONTROL_BAR_BACKGROUND_HEIGHT = 134.dp
private val TIMELINE_HEIGHT = 48.dp
private val TRANSPORT_ICON_SIZE = 40.dp
private val TRANSPORT_SLOT_SIZE = 124.dp

@Composable
fun PlaybackControlBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    controlsEnabled: Boolean,
    onSeekPreview: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPreviousBook: () -> Unit,
    onNextBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().height(CONTROL_BAR_HEIGHT)) {
        ControlBarBackground(modifier = Modifier.align(Alignment.BottomCenter)) {
            TransportControls(
                isPlaying = isPlaying,
                controlsEnabled = controlsEnabled,
                onTogglePlayback = onTogglePlayback,
                onPreviousBook = onPreviousBook,
                onNextBook = onNextBook,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        PlaybackTimeline(
            positionMs = positionMs,
            durationMs = durationMs,
            enabled = controlsEnabled && durationMs > 0L,
            onSeekPreview = onSeekPreview,
            onSeekFinished = onSeekFinished,
            modifier = Modifier.align(Alignment.TopCenter).zIndex(10f),
        )
    }
}

@Composable
private fun ControlBarBackground(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CONTROL_BAR_BACKGROUND_HEIGHT),
    ) {
        Image(
            painter = painterResource(R.drawable.img_media_control_background),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(CONTROL_BAR_BACKGROUND_HEIGHT),
            contentScale = ContentScale.FillBounds,
        )
        content()
    }
}

@Composable
private fun PlaybackTimeline(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeekPreview: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().height(TIMELINE_HEIGHT)) {
        VideoSeekBar(
            positionMs = positionMs,
            durationMs = durationMs,
            enabled = enabled,
            onSeekPreview = onSeekPreview,
            onSeekFinished = onSeekFinished,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(SEEK_BAR_VISUAL_HEIGHT),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 28.dp)
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ControlTime(formatDurationClock(positionMs))
            ControlTime(text = formatDurationClock(durationMs), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun TransportControls(
    isPlaying: Boolean,
    controlsEnabled: Boolean,
    onTogglePlayback: () -> Unit,
    onPreviousBook: () -> Unit,
    onNextBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TransportControlsLayout(
        modifier = modifier,
        leftControls = {
            PressStateIconButton(
                normalDrawable = R.drawable.ico_media_prev_n,
                pressedDrawable = R.drawable.ico_media_prev_p,
                contentDescription = "Previous book",
                layoutSize = TRANSPORT_SLOT_SIZE,
                iconSize = TRANSPORT_ICON_SIZE,
                onClick = onPreviousBook,
                enabled = controlsEnabled,
            )
        },
        centerControl = {
            PrimaryPlaybackButton(
                isPlaying = isPlaying,
                onClick = onTogglePlayback,
                buttonSize = TRANSPORT_SLOT_SIZE,
                enabled = controlsEnabled,
            )
        },
        rightControls = {
            PressStateIconButton(
                normalDrawable = R.drawable.ico_media_next_n,
                pressedDrawable = R.drawable.ico_media_next_p,
                contentDescription = "Next book",
                layoutSize = TRANSPORT_SLOT_SIZE,
                iconSize = TRANSPORT_ICON_SIZE,
                onClick = onNextBook,
                enabled = controlsEnabled,
            )
        },
    )
}

@Composable
private fun TransportControlsLayout(
    leftControls: @Composable RowScope.() -> Unit,
    centerControl: @Composable () -> Unit,
    rightControls: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().height(TRANSPORT_SLOT_SIZE)) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.5f)
                .padding(end = TRANSPORT_SLOT_SIZE / 2),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = leftControls,
        )
        Box(modifier = Modifier.align(Alignment.Center).size(TRANSPORT_SLOT_SIZE), contentAlignment = Alignment.Center) {
            centerControl()
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.5f)
                .padding(start = TRANSPORT_SLOT_SIZE / 2),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            content = rightControls,
        )
    }
}

@Composable
private fun ControlTime(text: String, textAlign: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
        style = MaterialTheme.typography.labelSmall,
        textAlign = textAlign,
        modifier = Modifier.width(52.dp),
    )
}

private fun formatDurationClock(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
