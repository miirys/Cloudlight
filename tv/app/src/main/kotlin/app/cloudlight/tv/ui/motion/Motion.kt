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

/** When the current page appeared, so its parts can enter one after another. */
class RevealEpoch(val startedAt: Long)

val LocalReveal = compositionLocalOf { RevealEpoch(0L) }

/** Starts a new staggered entrance for everything inside that uses [reveal]. */
@Composable
fun RevealPage(key: Any?, content: @Composable () -> Unit) {
    val epoch = remember(key) { RevealEpoch(SystemClock.uptimeMillis()) }
    CompositionLocalProvider(LocalReveal provides epoch, content = content)
}

/**
 * Staggered entrance: the element waits [index] beats, then rises a little, fades in and
 * settles from just under full size. Sections of a page enter top to bottom this way, so a
 * new page builds itself in front of you instead of popping in all at once. Elements that are
 * composed well after the page appeared (scrolled into view later) show immediately.
 */
fun Modifier.reveal(index: Int, distance: Dp = 18.dp): Modifier = composed {
    val epoch = LocalReveal.current
    val late = remember { SystemClock.uptimeMillis() - epoch.startedAt > 700 }
    val p = remember { Animatable(if (late) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!late) {
            delay(40L + (index * 45L).coerceAtMost(450L))
            p.animateTo(1f, Motion.springOf(response = 0.62f, damping = 0.84f))
        }
    }
    val px = with(LocalDensity.current) { distance.toPx() }
    graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * px
        val s = 0.97f + 0.03f * v
        scaleX = s
        scaleY = s
    }
}
