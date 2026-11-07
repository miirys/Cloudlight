package app.cloudlight.tv.stream

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import app.cloudlight.tv.input.Btn
import app.cloudlight.tv.input.PadState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Sends controllers to the game. Runs on its own high-priority thread so a busy interface
 * never delays a button press.
 *
 * Start is held back for a moment: a quick tap goes to the game as usual, holding it opens
 * the stream menu instead. The Guide button opens the menu straight away.
 */
class GameInput(private val onMenu: () -> Unit) {

    private val thread = HandlerThread("game-input", Process.THREAD_PRIORITY_URGENT_DISPLAY).apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())

    @Volatile var engine: StreamEngine? = null

    /** False while the stream menu is open: the game then sees idle controllers. */
    @Volatile var enabled = false
        set(value) {
            field = value
            handler.post { if (!value) neutralAll() }
        }

    /** Left stick drives the mouse, A and B click, right stick scrolls. */
    @Volatile var controllerMouse = false
        set(value) {
            field = value
            handler.post {
                if (value) {
                    neutralAll()
                    handler.removeCallbacks(mouseTick)
                    handler.post(mouseTick)
                } else {
                    releaseMouseButtons()
                }
            }
        }

    @Volatile var mouseSpeed = 1.0f

    /** How long Start must be held to open the menu. */
    @Volatile var holdMs = 650L

    private class Pad(val slot: Int) {
        var state = PadState.Idle
        var sentAt = 0L
        var startAt = 0L
        var startConsumed = false
        var startTapUntil = 0L
        var lastSent: IntArray? = null
    }

    private val pads = HashMap<String, Pad>()
    private var mouseLeft = false
    private var mouseRight = false
    private var scrollAcc = 0f
    private var moveAccX = 0f
    private var moveAccY = 0f

    private val connectedMask: Int get() = pads.values.fold(0) { m, p -> m or (1 shl p.slot) }

    /** Called from [app.cloudlight.tv.input.PadHub.raw] on whatever thread the report came in. */
    fun report(id: String, state: PadState?) {
        handler.post { apply(id, state) }
    }

    private fun apply(id: String, state: PadState?) {
        if (state == null) {
            val gone = pads.remove(id) ?: return
            engine?.sendGamepad(gone.slot, connectedMask, 0, 0, 0, 0, 0, 0, 0)
            return
        }
        val pad = pads[id] ?: run {
            val used = pads.values.map { it.slot }.toSet()
            val slot = (0..3).firstOrNull { it !in used } ?: return
            Pad(slot).also { pads[id] = it }
        }
        val prev = pad.state
        pad.state = state
        val now = SystemClock.uptimeMillis()

        if (state.has(Btn.GUIDE) && !prev.has(Btn.GUIDE)) {
            main.post(onMenu)
            return
        }
        if (state.has(Btn.MENU) && !prev.has(Btn.MENU)) {
            pad.startAt = now
            pad.startConsumed = false
            handler.postDelayed({ checkHold(id, pad) }, holdMs)
        } else if (!state.has(Btn.MENU) && prev.has(Btn.MENU)) {
            // Released before the hold time: it was a tap, so the game gets it now.
            if (!pad.startConsumed && enabled && !controllerMouse) pad.startTapUntil = now + 70
            pad.startAt = 0
            handler.postDelayed({ send(pad, force = true) }, 75)
        }
        if (!enabled || controllerMouse) return
        send(pad, force = false)
    }

    private fun checkHold(id: String, pad: Pad) {
        if (pads[id] !== pad || pad.startAt == 0L || pad.startConsumed) return
        if (SystemClock.uptimeMillis() - pad.startAt >= holdMs - 5 && pad.state.has(Btn.MENU)) {
            pad.startConsumed = true
            main.post(onMenu)
        }
    }

    private fun send(pad: Pad, force: Boolean) {
        val e = engine ?: return
        if (!enabled || controllerMouse) return
        val s = pad.state
        var b = xinput(s.buttons and Btn.MENU.inv())
        if (SystemClock.uptimeMillis() < pad.startTapUntil) b = b or InputEncoder.X_START
        val v = intArrayOf(b, trigger(s.lt), trigger(s.rt), axis(s.lx), axis(-s.ly), axis(s.rx), axis(-s.ry))
        if (!force && v.contentEquals(pad.lastSent)) return
        pad.lastSent = v
        pad.sentAt = SystemClock.uptimeMillis()
        e.sendGamepad(pad.slot, connectedMask, v[0], v[1], v[2], v[3], v[4], v[5], v[6])
    }

    /** Games treat a silent controller as unplugged, so repeat the last state now and then. */
    private val keepAlive = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            if (enabled && !controllerMouse) {
                for (p in pads.values) if (now - p.sentAt >= 100) send(p, force = true)
            }
            handler.postDelayed(this, 50)
        }
    }

    init {
        handler.post(keepAlive)
    }

    private fun neutralAll() {
        val e = engine ?: return
        for (p in pads.values) {
            p.lastSent = null
            e.sendGamepad(p.slot, connectedMask, 0, 0, 0, 0, 0, 0, 0)
        }
    }

    private val mouseTick = object : Runnable {
        override fun run() {
            if (!controllerMouse) return
            val e = engine
            val s = pads.values.firstOrNull()?.state
            if (e != null && s != null && enabled) {
                fun curve(v: Float): Float {
                    val d = (abs(v) - 0.12f).coerceAtLeast(0f) / 0.88f
                    return sign(v) * d * d
                }
                moveAccX += curve(s.lx) * 22f * mouseSpeed
                moveAccY += curve(s.ly) * 22f * mouseSpeed
                val dx = moveAccX.toInt()
                val dy = moveAccY.toInt()
                moveAccX -= dx
                moveAccY -= dy
                e.sendMouseMove(dx, dy)
                val left = s.has(Btn.A) || s.rt > 0.5f
                val right = s.has(Btn.B) || s.lt > 0.5f
                if (left != mouseLeft) {
                    mouseLeft = left
                    e.sendMouseButton(left, 1)
                }
                if (right != mouseRight) {
                    mouseRight = right
                    e.sendMouseButton(right, 3)
                }
                scrollAcc += -curve(s.ry) * 0.5f
                if (abs(scrollAcc) >= 1f) {
                    val n = scrollAcc.toInt()
                    scrollAcc -= n
                    e.sendWheel(n * 120)
                }
            }
            handler.postDelayed(this, 8)
        }
    }

    private fun releaseMouseButtons() {
        val e = engine ?: return
        if (mouseLeft) e.sendMouseButton(false, 1)
        if (mouseRight) e.sendMouseButton(false, 3)
        mouseLeft = false
        mouseRight = false
    }

    fun reset() {
        handler.post {
            pads.clear()
            mouseLeft = false
            mouseRight = false
        }
    }

    companion object {
        private fun axis(v: Float) = (v.coerceIn(-1f, 1f) * 32767f).roundToInt()
        private fun trigger(v: Float) = (v.coerceIn(0f, 1f) * 255f).roundToInt()

        fun xinput(b: Int): Int {
            var x = 0
            if (b and Btn.UP != 0) x = x or InputEncoder.X_UP
            if (b and Btn.DOWN != 0) x = x or InputEncoder.X_DOWN
            if (b and Btn.LEFT != 0) x = x or InputEncoder.X_LEFT
            if (b and Btn.RIGHT != 0) x = x or InputEncoder.X_RIGHT
            if (b and Btn.MENU != 0) x = x or InputEncoder.X_START
            if (b and Btn.VIEW != 0) x = x or InputEncoder.X_BACK
            if (b and Btn.LS != 0) x = x or InputEncoder.X_LS
            if (b and Btn.RS != 0) x = x or InputEncoder.X_RS
            if (b and Btn.LB != 0) x = x or InputEncoder.X_LB
            if (b and Btn.RB != 0) x = x or InputEncoder.X_RB
            if (b and Btn.A != 0) x = x or InputEncoder.X_A
            if (b and Btn.B != 0) x = x or InputEncoder.X_B
            if (b and Btn.X != 0) x = x or InputEncoder.X_X
            if (b and Btn.Y != 0) x = x or InputEncoder.X_Y
            return x
        }
    }
}
