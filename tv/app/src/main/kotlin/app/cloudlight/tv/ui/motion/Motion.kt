package app.cloudlight.tv.ui.motion

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.Spring
import androidx.compose.ui.unit.IntOffset
import kotlin.math.PI
import kotlin.math.pow

/**
 * The app's motion language. Everything moves on springs described the way iOS does:
 * a response time (how long a swing takes) and a damping fraction (1 = no overshoot).
 * Nothing in the app appears instantly; even small state changes settle in.
 */
object Motion {
    /** Converts an iOS-style response (seconds) to a Compose spring stiffness. */
    fun stiffness(response: Float) = (2 * PI / response).pow(2).toFloat()

    fun <T> springOf(response: Float, damping: Float, visibility: T? = null): SpringSpec<T> =
        androidx.compose.animation.core.spring(dampingRatio = damping, stiffness = stiffness(response), visibilityThreshold = visibility)

    /** Focus lift on tiles and buttons: quick with a hint of bounce. */
    fun <T> focus(): SpringSpec<T> = springOf(response = 0.32f, damping = 0.72f)

    /** Press feedback: fast and firm. */
    fun <T> press(): SpringSpec<T> = springOf(response = 0.18f, damping = 0.9f)

    /** Screens and large panels: a calm glide that lands without bounce. */
    fun <T> page(): SpringSpec<T> = springOf(response = 0.5f, damping = 0.92f)

    /** Scrolling a rail or list to keep the focused item in place. */
    val scroll: SpringSpec<Float> = springOf(response = 0.42f, damping = 0.95f)

    /** Colours and glow: soft, never overshoots. */
    fun <T> fade(): SpringSpec<T> = springOf(response = 0.36f, damping = 1f)

    val pageOffset: SpringSpec<IntOffset> = springOf(response = 0.5f, damping = 0.92f, visibility = IntOffset(1, 1))

    val gentle: AnimationSpec<Float> = androidx.compose.animation.core.spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = stiffness(0.6f))
}

/**
 * When the current page appeared, and from which side it arrived, so its parts can enter one
 * after another from that side. [dx] is -1 (from the left), 1 (from the right) or 0 (from below).
 */
class RevealEpoch(val startedAt: Long, val dx: Int = 0)

val LocalReveal = compositionLocalOf { RevealEpoch(0L) }

/** Starts a new staggered entrance for everything inside that uses [reveal]. */
@Composable
fun RevealPage(key: Any?, dx: Int = 0, content: @Composable () -> Unit) {
    val epoch = remember(key) { RevealEpoch(SystemClock.uptimeMillis(), dx) }
    CompositionLocalProvider(LocalReveal provides epoch, content = content)
}

/**
 * Staggered entrance: the element waits [index] beats, then glides in from the side the page
 * came from, growing from 94% and coming into focus as it lands. Parts of a page arrive one
 * after another, so a new page assembles itself instead of popping in at once. Elements
 * composed well after the page appeared (scrolled into view later) show immediately.
 */
fun Modifier.reveal(index: Int, distance: Dp = 28.dp, soft: Boolean = false): Modifier =
    revealAt(55L * index, distance, soft)

/** [reveal] with an exact delay, for grids where rows and columns stagger differently. */
fun Modifier.revealAt(delayMs: Long, distance: Dp = 28.dp, soft: Boolean = false): Modifier = composed {
    val epoch = LocalReveal.current
    val late = remember { SystemClock.uptimeMillis() - epoch.startedAt > 900 }
    val p = remember { Animatable(if (late) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!late) {
            delay(60L + delayMs.coerceIn(0L, 650L))
            p.animateTo(1f, Motion.springOf(response = 0.72f, damping = 0.8f))
        }
    }
    val px = with(LocalDensity.current) { distance.toPx() }
    val dx = epoch.dx
    graphicsLayer {
        val v = p.value
        val rest = 1f - v
        // Opacity leads the motion so nothing is seen sliding while still transparent.
        alpha = (v * 1.6f).coerceIn(0f, 1f)
        translationX = dx * rest * px * 1.8f
        translationY = rest * px * (if (dx == 0) 1f else 0.35f)
        val s = 0.94f + 0.06f * v
        scaleX = s
        scaleY = s
        transformOrigin = TransformOrigin(if (dx < 0) 0f else if (dx > 0) 1f else 0.5f, 1f)
        renderEffect = if (soft && rest > 0.02f) BlurEffect(rest * 18f, rest * 18f) else null
    }
}
