package app.cloudlight.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.BasicText
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cloudlight.tv.ui.motion.Motion
import app.cloudlight.tv.ui.theme.LocalType
import app.cloudlight.tv.ui.theme.Palette

/**
 * The one focusable building block. Focus lifts the element on a spring, press sinks it,
 * and a mouse hovering over it moves focus there, so remote, controller and mouse all
 * drive the same highlight.
 */
@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    focusedScale: Float = 1.06f,
    color: Color = Palette.surface,
    focusedColor: Color = Palette.surfaceHigh,
    focusRequester: FocusRequester? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val requester = focusRequester ?: remember { FocusRequester() }
    LaunchedEffect(hovered) {
        if (hovered && !focused) runCatching { requester.requestFocus() }
    }
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> focusedScale - 0.04f
            focused -> focusedScale
            else -> 1f
        },
        animationSpec = if (pressed) Motion.press() else Motion.focus(),
        label = "scale",
    )
    val lift by animateDpAsState(if (focused) 18.dp else 0.dp, Motion.focus(), label = "lift")
    val ring by animateFloatAsState(if (focused) 1f else 0f, Motion.fade(), label = "ring")
    val fill by animateColorAsState(if (focused) focusedColor else color, Motion.fade(), label = "fill")
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(lift, shape, ambientColor = Palette.lavender, spotColor = Palette.lavender)
            .background(fill, shape)
            .border(2.dp, Palette.lavender.copy(alpha = 0.85f * ring), shape)
            .focusRequester(requester)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        content(focused)
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    Pressable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        focusedScale = 1.08f,
        color = if (primary) Palette.lavenderDeep else Palette.surface,
        focusedColor = if (primary) Palette.lavender else Palette.text,
        focusRequester = focusRequester,
    ) { focused ->
        val fg by animateColorAsState(
            if (focused) Palette.ink else Palette.text, Motion.fade(), label = "fg",
        )
        BasicText(
            text,
            Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
            style = LocalType.current.label.copy(color = fg),
        )
    }
}

/**
 * Keeps the focused item at a fixed place in a scrolling list (a "pivot"), and glides there
 * on a spring instead of jumping, which is what makes long lists feel calm on a TV.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PivotScroll(leadingFraction: Float, content: @Composable () -> Unit) {
    val spec = remember(leadingFraction) {
        object : BringIntoViewSpec {
            @Suppress("OVERRIDE_DEPRECATION")
            override val scrollAnimationSpec: AnimationSpec<Float> = Motion.scroll

            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val target = containerSize * leadingFraction
                return if (size > containerSize - target) offset - (containerSize - size) / 2f else offset - target
            }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

fun Modifier.screenPadding(horizontal: Dp = 64.dp) = padding(horizontal = horizontal)
