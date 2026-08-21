package com.ivi.audiobook.presentation.components

// Ported from CoWatch's com.ivi.common.ui.PressStateIconButton — same press/hold behavior,
// no CoWatch-specific dependencies to adapt.
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PressStateIconButton(
    @DrawableRes normalDrawable: Int,
    @DrawableRes pressedDrawable: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    layoutSize: Dp = 124.dp,
    iconSize: Dp = 40.dp,
    enabled: Boolean = true,
    onLongPressStart: (() -> Unit)? = null,
    onLongPressEnd: (() -> Unit)? = null,
) {
    val press = rememberPressAnimationState("PressStateIconScale")
    val showPressed = enabled && press.isPressed
    val drawable = if (showPressed) pressedDrawable else normalDrawable

    Box(modifier = modifier.size(layoutSize), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    scaleX = if (showPressed) 0.98f else press.scale
                    scaleY = if (showPressed) 0.98f else press.scale
                }
                .then(
                    if (onLongPressStart == null || onLongPressEnd == null) {
                        Modifier.clickable(
                            interactionSource = press.interactionSource,
                            indication = null,
                            enabled = enabled,
                            onClick = onClick,
                        )
                    } else {
                        Modifier.pointerInput(enabled, onClick, onLongPressStart, onLongPressEnd) {
                            if (!enabled) return@pointerInput
                            var longPressActive = false
                            detectTapGestures(
                                onPress = { offset ->
                                    val interaction = PressInteraction.Press(offset)
                                    press.interactionSource.emit(interaction)
                                    val released = tryAwaitRelease()
                                    press.interactionSource.emit(
                                        if (released) PressInteraction.Release(interaction) else PressInteraction.Cancel(interaction),
                                    )
                                    if (longPressActive) {
                                        longPressActive = false
                                        onLongPressEnd()
                                    }
                                },
                                onTap = { onClick() },
                                onLongPress = {
                                    longPressActive = true
                                    onLongPressStart()
                                },
                            )
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = drawable, animationSpec = tween(90), label = "PressStateIconCrossfade") { drawableRes ->
                Image(
                    painter = painterResource(drawableRes),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

data class PressAnimationState(
    val interactionSource: MutableInteractionSource,
    val isPressed: Boolean,
    val scale: Float,
)

@Composable
fun rememberPressAnimationState(label: String): PressAnimationState {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = if (isPressed) 80 else 140),
        label = label,
    )
    return PressAnimationState(interactionSource, isPressed, scale)
}
