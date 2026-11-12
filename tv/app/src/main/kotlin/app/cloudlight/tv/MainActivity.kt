package app.cloudlight.tv

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.util.Rational
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.cloudlight.tv.data.AppState
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import app.cloudlight.tv.input.Btn
import app.cloudlight.tv.input.NavAction
import app.cloudlight.tv.input.PadHub
import app.cloudlight.tv.input.PadState
import app.cloudlight.tv.input.UsbPadDriver
import app.cloudlight.tv.stream.Keymap
import app.cloudlight.tv.stream.Streamer
import app.cloudlight.tv.ui.screens.AppRoot
import app.cloudlight.tv.ui.theme.CloudlightTheme
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private lateinit var hub: PadHub
    private lateinit var usb: UsbPadDriver
    private lateinit var settings: Settings
    private lateinit var app: AppState
    private lateinit var streamer: Streamer
    private val scope = MainScope()

    /** Bluetooth (or kernel-recognised) pads that Android reads for us. */
    private class AndroidPad(val name: String) {
        var keys = 0
        var hat = 0
        var state = PadState.Idle
    }

    private val androidPads = HashMap<Int, AndroidPad>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        settings = Settings(this)
        hub = PadHub(::perform)
        usb = UsbPadDriver(
            this,
            onState = { id, name, s -> hub.update(id, name, "USB", s) },
            onGone = { id -> hub.gone(id) },
        )
        app = AppState(applicationContext, scope)
        streamer = Streamer(applicationContext, scope, app, settings, hub)
        CrashLog.onBackgroundFailure = { streamer.internalFailure(it) }
        // Earlier crashes came from the streaming engine failing to start, not from the parts
        // the automatic fixes turned off, so give those back.
        settings.clearFixesOnce("standard-video")
        val mark = CrashLog.takeStreamingMark(this)
        val found = CrashLog.take(this)
            ?: if (mark != null && mark.second != "live") CrashLog.Problem(System.currentTimeMillis(), "The app closed during a stream", "", native = true) else null
        // Keep the system's own account of the crash while it is still in the log.
        if (mark != null || found?.native == true) CrashLog.captureLog(this)
        app.problem = found?.let { p ->
            if (mark == null || mark.second == "live") return@let p
            // The app went down while a stream was starting: avoid the part that was starting.
            val change = settings.recoverFrom(mark.first, mark.second)
            p.copy(
                summary = "It happened while ${CrashLog.stepName(mark.second)} (${p.summary})" +
                    (change?.let { ". The next stream will use $it" } ?: ""),
            ).also { CrashLog.saveNote(this, it.summary) }
        }
        setContent {
            // The whole interface is laid out in a fixed 960 × 540 dp space and drawn at the
            // window's real pixel size, so on a 4K output every glyph and edge is 4K-sharp and
            // the layout looks identical at 1080p.
            val px = resources.displayMetrics.widthPixels.coerceAtLeast(1)
            LaunchedEffect(settings.maxRefresh, streamer.streamFps) { preferRefresh(settings.maxRefresh, streamer.streamFps) }
            CompositionLocalProvider(LocalDensity provides Density(px / 960f, 1f)) {
                CloudlightTheme {
                    AppRoot(hub, app, settings, streamer, onExit = { finish() })
                }
            }
        }
    }

    /**
     * Many TVs sit at 60 Hz even when they can do more, which caps both the menus and the
     * stream. Ask for the fastest mode at the current resolution (the system changes it back
     * when the app leaves the screen). During a stream, ask for the slowest mode that still
     * shows every frame evenly: the stream's own rate or a whole multiple of it, so a 120 fps
     * stream gets 120 Hz rather than 144. Turning the setting off hands the choice back.
     */
    private fun preferRefresh(fastest: Boolean, streamFps: Int) {
        runCatching {
            @Suppress("DEPRECATION")
            val display = (if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay) ?: return
            val now = display.mode
            val modes = display.supportedModes
                .filter { it.physicalWidth == now.physicalWidth && it.physicalHeight == now.physicalHeight }
            fun even(hz: Float): Boolean {
                val ratio = hz / streamFps
                return ratio > 0.98f && abs(ratio - Math.round(ratio)) < 0.02f
            }
            val target = when {
                !fastest -> 0
                streamFps > 0 && even(now.refreshRate) -> now.modeId
                streamFps > 0 && modes.any { even(it.refreshRate) } ->
                    modes.filter { even(it.refreshRate) }.minByOrNull { it.refreshRate }!!.modeId
                else -> modes.maxByOrNull { it.refreshRate }
                    ?.takeIf { it.refreshRate > now.refreshRate + 1f }
                    ?.modeId ?: return
            }
            if (window.attributes.preferredDisplayModeId == target) return
            window.attributes = window.attributes.also { it.preferredDisplayModeId = target }
        }
    }

    /**
     * Controller presses reach the menus as key events we make ourselves, and those can't
     * do what a real remote key does: take the window out of touch mode (where nothing shows
     * focus) or give it its first focus. So do that by hand before the press goes in.
     * Without it a controller did nothing until the remote had been used once.
     */
    private fun wakeFocus() {
        runCatching {
            val decor = window.decorView
            if (decor.isInTouchMode || currentFocus == null) decor.requestFocusFromTouch()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && hub.pads.value.isNotEmpty()) wakeFocus()
    }

    override fun onResume() {
        super.onResume()
        // Pick up sessions started on another device while the app was in the background.
        if (app.signedIn) app.refreshSessions()
    }

    override fun onDestroy() {
        streamer.leave()
        scope.cancel()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        usb.start()
    }

    /** Pressing Home during a game keeps it playing in a small window, like a video app. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!streamer.live || !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        streamer.inPip = isInPictureInPictureMode
        if (isInPictureInPictureMode && streamer.menuOpen) streamer.closeMenu()
    }

    override fun onStop() {
        // Going to the home screen leaves the game running in the cloud, ready to resume.
        when (streamer.phase) {
            null -> Unit
            is Streamer.Phase.Ended -> streamer.dismiss()
            else -> streamer.leave()
        }
        usb.stop()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Launched again because a controller dongle was plugged in.
        usb.scan()
    }

    /** Turns a controller action into what the menus understand: focus keys, select, back. */
    private fun perform(a: NavAction) {
        when (a) {
            NavAction.UP -> tap(KeyEvent.KEYCODE_DPAD_UP)
            NavAction.DOWN -> tap(KeyEvent.KEYCODE_DPAD_DOWN)
            NavAction.LEFT -> tap(KeyEvent.KEYCODE_DPAD_LEFT)
            NavAction.RIGHT -> tap(KeyEvent.KEYCODE_DPAD_RIGHT)
            NavAction.CONFIRM_DOWN -> inject(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
            NavAction.CONFIRM_UP -> inject(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
            NavAction.BACK -> onBackPressedDispatcher.onBackPressed()
            else -> Unit // Tabs and menu are read from PadHub.actions by the screens.
        }
    }

    private var confirmDownAt = 0L

    private fun tap(code: Int) {
        inject(KeyEvent.ACTION_DOWN, code)
        inject(KeyEvent.ACTION_UP, code)
    }

    private fun inject(action: Int, code: Int) {
        val now = SystemClock.uptimeMillis()
        val down = if (code == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (action == KeyEvent.ACTION_DOWN) confirmDownAt = now
            confirmDownAt
        } else {
            now
        }
        if (action == KeyEvent.ACTION_DOWN) wakeFocus()
        // Synthetic events come from the virtual keyboard, so dispatchKeyEvent lets them through.
        super.dispatchKeyEvent(
            KeyEvent(down, now, action, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_DPAD),
        )
    }

    private fun isPad(dev: InputDevice?): Boolean {
        if (dev == null || dev.isVirtual) return false
        val s = dev.sources
        return s and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            s and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }

    private fun padFor(dev: InputDevice) = androidPads.getOrPut(dev.id) { AndroidPad(dev.name) }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val dev = event.device
        if (isPad(dev)) {
            val bit = keyBit(event.keyCode)
            if (bit != 0) {
                if (event.repeatCount == 0) {
                    val p = padFor(dev!!)
                    p.keys = if (event.action == KeyEvent.ACTION_DOWN) p.keys or bit else p.keys and bit.inv()
                    push(dev.id, p)
                }
                return true
            }
        }
        if (streamer.phase == Streamer.Phase.Live && !streamer.menuOpen && toGame(event)) return true
        return super.dispatchKeyEvent(event)
    }

    /**
     * While a game plays, a keyboard types into it and a TV remote's arrows and OK act as
     * the arrow keys and Enter. Back on a remote, and Ctrl+G on a keyboard, open the menu.
     */
    private fun toGame(event: KeyEvent): Boolean {
        val dev = event.device ?: return false
        if (dev.isVirtual) return false
        val down = event.action == KeyEvent.ACTION_DOWN
        val code = event.keyCode
        when (code) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_SETTINGS,
            -> return false
        }
        val keyboard = dev.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
        if (keyboard && event.isCtrlPressed && code == KeyEvent.KEYCODE_G) {
            if (down && event.repeatCount == 0) streamer.openMenu()
            return true
        }
        val key = when {
            keyboard -> Keymap.of(code)
            code == KeyEvent.KEYCODE_DPAD_CENTER -> Keymap.ENTER
            else -> Keymap.of(code)
        } ?: return keyboard
        if (event.action == KeyEvent.ACTION_MULTIPLE) return true
        streamer.key(down, key, if (keyboard) Keymap.modifiers(event) else 0)
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val dev = event.device
        if (isPad(dev) && event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.action == MotionEvent.ACTION_MOVE) {
            val p = padFor(dev!!)
            fun axis(a: Int): Float {
                val v = event.getAxisValue(a)
                val flat = dev.getMotionRange(a, event.source)?.flat ?: 0f
                return if (abs(v) <= flat) 0f else v
            }
            val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            var hat = 0
            if (hx < -0.5f) hat = hat or Btn.LEFT
            if (hx > 0.5f) hat = hat or Btn.RIGHT
            if (hy < -0.5f) hat = hat or Btn.UP
            if (hy > 0.5f) hat = hat or Btn.DOWN
            p.hat = hat
            p.state = p.state.copy(
                lx = axis(MotionEvent.AXIS_X),
                ly = axis(MotionEvent.AXIS_Y),
                rx = axis(MotionEvent.AXIS_Z),
                ry = axis(MotionEvent.AXIS_RZ),
                lt = maxOf(axis(MotionEvent.AXIS_LTRIGGER), axis(MotionEvent.AXIS_BRAKE)),
                rt = maxOf(axis(MotionEvent.AXIS_RTRIGGER), axis(MotionEvent.AXIS_GAS)),
            )
            push(dev.id, p)
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun push(id: Int, p: AndroidPad) {
        p.state = p.state.copy(buttons = p.keys or p.hat)
        hub.update("android:$id", p.name, "Bluetooth", p.state)
    }

    private fun keyBit(code: Int) = when (code) {
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> Btn.A
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> Btn.B
        KeyEvent.KEYCODE_BUTTON_X -> Btn.X
        KeyEvent.KEYCODE_BUTTON_Y -> Btn.Y
        KeyEvent.KEYCODE_BUTTON_L1 -> Btn.LB
        KeyEvent.KEYCODE_BUTTON_R1 -> Btn.RB
        KeyEvent.KEYCODE_BUTTON_SELECT -> Btn.VIEW
        KeyEvent.KEYCODE_BUTTON_START -> Btn.MENU
        KeyEvent.KEYCODE_BUTTON_MODE -> Btn.GUIDE
        KeyEvent.KEYCODE_BUTTON_THUMBL -> Btn.LS
        KeyEvent.KEYCODE_BUTTON_THUMBR -> Btn.RS
        KeyEvent.KEYCODE_DPAD_UP -> Btn.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> Btn.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> Btn.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> Btn.RIGHT
        else -> 0
    }
}
