package app.cloudlight.tv.ui.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.Spring
import androidx.compose.ui.unit.IntOffset
import kotlin.math.PI
import kotlin.math.pow

/**
 * Cloudlight's motion language. Everything moves on springs described the way iOS does:
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
