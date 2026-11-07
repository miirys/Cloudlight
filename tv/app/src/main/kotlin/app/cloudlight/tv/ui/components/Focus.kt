package app.cloudlight.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.dp
import app.cloudlight.tv.ui.motion.Motion
import app.cloudlight.tv.ui.theme.LocalType
import app.cloudlight.tv.ui.theme.Palette

/**
 * The one focusable building block. Focus lifts the element on a spring and brightens its
 * glass, press sinks it, and a hovering mouse moves focus to it, so remote, controller and
 * mouse all drive the same highlight.
 */
@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    focusedScale: Float = 1.05f,
    color: Color = Palette.glass,
    focusedColor: Color = Palette.white,
    border: Boolean = true,
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
            pressed -> focusedScale - 0.035f
            focused -> focusedScale
            else -> 1f
        },
        animationSpec = if (pressed) Motion.press() else Motion.focus(),
        label = "scale",
    )
    val fill by animateColorAsState(if (focused) focusedColor else color, Motion.fade(), label = "fill")
    val ring by animateFloatAsState(if (focused) 0f else 1f, Motion.fade(), label = "ring")
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(fill, shape)
            .then(if (border) Modifier.border(0.75.dp, Palette.hairline.copy(alpha = Palette.hairline.alpha * ring), shape) else Modifier)
            .focusRequester(requester)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content(focused)
    }
}

/** Foreground colour for content sitting on a [Pressable]: black on the white focus fill. */
@Composable
fun onFocusColor(focused: Boolean, idle: Color = Palette.text): Color {
    val c by animateColorAsState(if (focused) Palette.black else idle, Motion.fade(), label = "fg")
    return c
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    Pressable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        focusedScale = 1.07f,
        color = if (primary) Palette.glassHeavy else Palette.glass,
        focusRequester = focusRequester,
    ) { focused ->
        val fg = onFocusColor(focused)
        Row(Modifier.padding(horizontal = 18.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Image(icon, null, Modifier.size(13.dp), colorFilter = ColorFilter.tint(fg))
                Spacer(Modifier.width(7.dp))
            }
            BasicText(text, style = LocalType.current.label.copy(color = fg))
        }
    }
}

/** A frosted panel. The blurred art behind it does the frosting; this adds tint and edge. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(16.dp), strong: Boolean = false) =
    background(if (strong) Palette.glassStrong else Palette.glass, shape)
        .border(0.75.dp, Palette.hairline, shape)

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
