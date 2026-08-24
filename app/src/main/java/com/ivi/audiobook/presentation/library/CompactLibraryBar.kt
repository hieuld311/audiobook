package com.ivi.audiobook.presentation.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ivi.audiobook.R
import com.ivi.audiobook.domain.model.Book
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val StripSecondaryText = Color(0xFFB7B7C2)
private val StripTrackColor = Color(0xFF393B4A)

private val LEFT_BLOCK_WIDTH = 567.dp
private val LEFT_BLOCK_HEIGHT = 208.dp
private val RAIL_WIDTH = 1279.dp
private val RAIL_HEIGHT = 208.dp

private val FOCUSED_BACKDROP_SIZE = 116.dp
private val FOCUSED_COVER_SIZE = 88.dp
private val SIDE_COVER_SIZE = 80.dp
private val CARD_GAP = 24.dp

private const val FOCUS_SETTLE_THRESHOLD = 0.32f
private const val FOCUS_SETTLE_ANIMATION_MS = 320
private const val VISIBLE_WINDOW = 8
private val REFRESH_BUTTON_SIZE = 28.dp

/**
 * Same 567x208 / 1279x208 split as the compact Player bar: left shows the focused book's
 * title/author, right is a book-cover carousel with the focused cover bounded in
 * img_button_play_background_n, same as the player's cover treatment.
 */
@Composable
fun CompactLibraryBar(
    books: List<Book>,
    focusedIndex: Int,
    onFocusChanged: (Int) -> Unit,
    onOpenBook: (Long) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color.Black)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(LEFT_BLOCK_WIDTH, LEFT_BLOCK_HEIGHT)) {
            val focused = books.getOrNull(focusedIndex)
            if (focused != null) {
                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                    Text(
                        text = focused.title,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = focused.author ?: "Unknown author",
                        color = StripSecondaryText,
                        fontSize = 22.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // No matching icon in res/ for refresh (unlike the player's close button) — falls back
            // to the Material icon the old LibraryHeaderBar used.
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Rescan library",
                tint = StripSecondaryText,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(REFRESH_BUTTON_SIZE)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRefresh,
                    ),
            )
        }

        Spacer(Modifier.width(16.dp))

        BookRail(
            books = books,
            focusedIndex = focusedIndex,
            onFocusChanged = onFocusChanged,
            onOpenBook = onOpenBook,
            modifier = Modifier.size(RAIL_WIDTH, RAIL_HEIGHT),
        )
    }
}

// Ported from CoWatch's VideoRail (com.ivi.common.ui.pidlibrary): a fully custom, non-lazy drag
// carousel — no LazyRow/SnapFlingBehavior — built on a running raw drag offset plus an
// Animatable "settle" offset that commits at most one slot per gesture (a manual threshold check
// on release, not fling velocity). Simplified from CoWatch's two-tier focused/side card sizing to
// one uniform slot distance, since round covers vary far less in size than CoWatch's video
// thumbnails; and linear/clamped at the ends rather than circular, since the library isn't a
// looping list.
@Composable
private fun BookRail(
    books: List<Book>,
    focusedIndex: Int,
    onFocusChanged: (Int) -> Unit,
    onOpenBook: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val slotDistancePx = with(density) { (SIDE_COVER_SIZE + CARD_GAP).toPx() }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val settleOffsetPx = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun settleToRest() {
        scope.launch {
            settleOffsetPx.animateTo(0f, tween(FOCUS_SETTLE_ANIMATION_MS, easing = FastOutSlowInEasing))
            dragOffsetPx = 0f
        }
    }

    fun animateFocusTo(targetIndex: Int) {
        val clamped = targetIndex.coerceIn(0, books.lastIndex)
        if (clamped == focusedIndex) {
            settleToRest()
            return
        }
        val delta = clamped - focusedIndex
        scope.launch {
            settleOffsetPx.animateTo(
                targetValue = -delta * slotDistancePx - dragOffsetPx,
                animationSpec = tween(FOCUS_SETTLE_ANIMATION_MS, easing = FastOutSlowInEasing),
            )
            onFocusChanged(clamped)
            dragOffsetPx = 0f
            settleOffsetPx.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(books.size, slotDistancePx) {
                detectHorizontalDragGestures(
                    onDragStart = { scope.launch { settleOffsetPx.stop() } },
                    onDragEnd = {
                        val totalOffsetPx = dragOffsetPx + settleOffsetPx.value
                        val rawSlots = -totalOffsetPx / slotDistancePx
                        val delta = when {
                            rawSlots >= FOCUS_SETTLE_THRESHOLD -> 1
                            rawSlots <= -FOCUS_SETTLE_THRESHOLD -> -1
                            else -> 0
                        }
                        animateFocusTo(focusedIndex + delta)
                    },
                    onDragCancel = { settleToRest() },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetPx += dragAmount
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val totalOffsetPx = dragOffsetPx + settleOffsetPx.value
        val windowStart = (focusedIndex - VISIBLE_WINDOW).coerceAtLeast(0)
        val windowEnd = (focusedIndex + VISIBLE_WINDOW).coerceAtMost(books.lastIndex)
        for (index in windowStart..windowEnd) {
            val book = books[index]
            val relativeSlot = index - focusedIndex
            val xPx = relativeSlot * slotDistancePx + totalOffsetPx
            val isFocused = index == focusedIndex
            RailCard(
                book = book,
                isFocused = isFocused,
                onClick = { if (isFocused) onOpenBook(book.id) else animateFocusTo(index) },
                modifier = Modifier.offset { IntOffset(x = xPx.roundToInt(), y = 0) },
            )
        }
    }
}

@Composable
private fun RailCard(book: Book, isFocused: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val coverSize = if (isFocused) FOCUSED_COVER_SIZE else SIDE_COVER_SIZE
    Box(
        modifier = modifier
            .size(if (isFocused) FOCUSED_BACKDROP_SIZE else SIDE_COVER_SIZE)
            .alpha(if (isFocused) 1f else 0.6f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isFocused) {
            Image(
                painter = painterResource(R.drawable.img_button_play_background_n),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        if (book.coverPath != null) {
            AsyncImage(
                model = File(book.coverPath),
                contentDescription = null,
                modifier = Modifier.size(coverSize).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(modifier = Modifier.size(coverSize).clip(CircleShape).background(StripTrackColor))
        }
    }
}
