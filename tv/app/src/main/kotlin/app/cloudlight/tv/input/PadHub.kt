package app.cloudlight.tv.input

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** What a controller press means in the menus. */
enum class NavAction { UP, DOWN, LEFT, RIGHT, CONFIRM_DOWN, CONFIRM_UP, BACK, MENU, TAB_PREV, TAB_NEXT, ALT }

/**
 * Turns raw controller state from every source (our USB driver, Bluetooth pads that Android
 * reads) into menu actions. Direction repeat is timer driven with a gentle acceleration, so
 * holding a stick glides through a list instead of stuttering or racing.
 */
class PadHub(private val dispatch: (NavAction) -> Unit) {

    private val main = Handler(Looper.getMainLooper())
    private val sources = ConcurrentHashMap<String, Source>()

    private val _pads = MutableStateFlow<List<PadInfo>>(emptyList())
    val pads: StateFlow<List<PadInfo>> = _pads

    private val _actions = MutableSharedFlow<NavAction>(extraBufferCapacity = 16)
    /** Actions that are not plain focus moves (tabs, menu), for screens that listen for them. */
    val actions: SharedFlow<NavAction> = _actions

    /**
     * True while a stream is running: the pad then goes to the game, not the menus. When it
     * turns false again, the menus start from the pads' current state, so a button still held
     * from the game never counts as a fresh press.
     */
    @Volatile var captured = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) {
                main.post {
                    for (src in sources.values) latest[src.id]?.let { src.state = it }
                    publish()
                }
            }
        }

    /**
     * Receives every report on the thread it arrived on, without waiting for the main
     * thread. The stream uses it to forward controllers with the least delay.
     */
    @Volatile var raw: ((id: String, state: PadState?) -> Unit)? = null

    private val latest = ConcurrentHashMap<String, PadState>()

    /** Every connected controller's current state, for a stream that is just starting. */
    fun snapshot(): Map<String, PadState> = HashMap(latest)

    private inner class Source(val id: String, var name: String, val route: String) {
        var state = PadState.Idle
        var dir: NavAction? = null
        var held = 0
        var count = 0
        var windowStart = SystemClock.uptimeMillis()
        var rate = 0
        val repeat = object : Runnable {
            override fun run() {
                val d = dir ?: return
                emit(d)
                held++
                // 380 ms before the first repeat, then 120 ms easing down to 55 ms.
                main.postDelayed(this, maxOf(55L, 120L - held * 8L))
            }
        }
    }

    /** Called from any thread with a new snapshot for one controller. */
    fun update(id: String, name: String, route: String, state: PadState) {
        latest[id] = state
        raw?.invoke(id, state)
        // While streaming, the menus don't need every report; one in eight keeps the
        // controller page's live view and the report rate honest without busying the UI.
        if (captured && sources.containsKey(id) && (++skip and 7) != 0) return
        main.post { apply(id, name, route, state) }
    }

    @Volatile private var skip = 0

    fun gone(id: String) {
        latest.remove(id)
        raw?.invoke(id, null)
        main.post {
            sources.remove(id)?.let { main.removeCallbacks(it.repeat) }
            publish()
        }
    }

    private fun apply(id: String, name: String, route: String, s: PadState) {
        val src = sources.getOrPut(id) { Source(id, name, route) }
        val prev = src.state
        src.state = s
        src.count += if (captured) 8 else 1
        val now = SystemClock.uptimeMillis()
        if (now - src.windowStart >= 1000) {
            src.rate = (src.count * 1000 / (now - src.windowStart)).toInt()
            src.count = 0
            src.windowStart = now
        }
        if (!captured) {
            fun pressed(b: Int) = s.has(b) && !prev.has(b)
            fun released(b: Int) = !s.has(b) && prev.has(b)
            if (pressed(Btn.A)) emit(NavAction.CONFIRM_DOWN)
            if (released(Btn.A)) emit(NavAction.CONFIRM_UP)
            if (released(Btn.B)) emit(NavAction.BACK)
            if (pressed(Btn.MENU)) emit(NavAction.MENU)
            if (pressed(Btn.LB)) emit(NavAction.TAB_PREV)
            if (pressed(Btn.RB)) emit(NavAction.TAB_NEXT)
            if (pressed(Btn.X)) emit(NavAction.ALT)
            val d = direction(s, src.dir)
            if (d != src.dir) {
                main.removeCallbacks(src.repeat)
                src.dir = d
                src.held = 0
                if (d != null) {
                    emit(d)
                    main.postDelayed(src.repeat, 380)
                }
            }
        } else if (src.dir != null) {
            main.removeCallbacks(src.repeat)
            src.dir = null
        }
        publish()
    }

    /** D-pad wins; otherwise the left stick, with hysteresis so it never flickers at the edge. */
    private fun direction(s: PadState, current: NavAction?): NavAction? {
        when {
            s.has(Btn.UP) -> return NavAction.UP
            s.has(Btn.DOWN) -> return NavAction.DOWN
            s.has(Btn.LEFT) -> return NavAction.LEFT
            s.has(Btn.RIGHT) -> return NavAction.RIGHT
        }
        val ax = abs(s.lx)
        val ay = abs(s.ly)
        val keep = 0.35f
        val enter = 0.6f
        current?.let {
            val still = when (it) {
                NavAction.UP -> -s.ly > keep && ay >= ax * 0.7f
                NavAction.DOWN -> s.ly > keep && ay >= ax * 0.7f
                NavAction.LEFT -> -s.lx > keep && ax >= ay * 0.7f
                NavAction.RIGHT -> s.lx > keep && ax >= ay * 0.7f
                else -> false
            }
            if (still) return it
        }
        if (maxOf(ax, ay) < enter) return null
        return if (ax > ay) {
            if (s.lx > 0) NavAction.RIGHT else NavAction.LEFT
        } else {
            if (s.ly > 0) NavAction.DOWN else NavAction.UP
        }
    }

    private fun emit(a: NavAction) {
        dispatch(a)
        _actions.tryEmit(a)
    }

    private fun publish() {
        _pads.value = sources.values.map { PadInfo(it.id, it.name, it.route, it.state, it.rate) }
    }
}
