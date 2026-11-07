package app.cloudlight.tv.input

/** Buttons in Xbox layout, as bit flags. Every controller source is normalised to these. */
object Btn {
    const val A = 1 shl 0
    const val B = 1 shl 1
    const val X = 1 shl 2
    const val Y = 1 shl 3
    const val LB = 1 shl 4
    const val RB = 1 shl 5
    const val VIEW = 1 shl 6
    const val MENU = 1 shl 7
    const val LS = 1 shl 8
    const val RS = 1 shl 9
    const val GUIDE = 1 shl 10
    const val UP = 1 shl 11
    const val DOWN = 1 shl 12
    const val LEFT = 1 shl 13
    const val RIGHT = 1 shl 14
    const val SHARE = 1 shl 15

    val names = listOf(
        A to "A", B to "B", X to "X", Y to "Y", LB to "LB", RB to "RB", VIEW to "View", MENU to "Menu",
        LS to "LS", RS to "RS", GUIDE to "Guide", UP to "Up", DOWN to "Down", LEFT to "Left", RIGHT to "Right",
        SHARE to "Share",
    )

    fun describe(buttons: Int) = names.filter { buttons and it.first != 0 }.joinToString(" ") { it.second }
}

/**
 * One controller snapshot. Sticks are -1..1 with Y pointing down (screen convention),
 * triggers 0..1. Immutable so it can be handed between threads freely.
 */
data class PadState(
    val buttons: Int = 0,
    val lx: Float = 0f,
    val ly: Float = 0f,
    val rx: Float = 0f,
    val ry: Float = 0f,
    val lt: Float = 0f,
    val rt: Float = 0f,
) {
    fun has(b: Int) = buttons and b != 0

    companion object {
        val Idle = PadState()
    }
}

/** A connected controller as the app sees it, for the controller settings page. */
data class PadInfo(
    val id: String,
    val name: String,
    val route: String,
    val state: PadState,
    val reportsPerSecond: Int,
)
