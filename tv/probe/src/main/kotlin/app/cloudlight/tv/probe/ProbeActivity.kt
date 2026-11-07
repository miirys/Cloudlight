package app.cloudlight.tv.probe

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class ProbeActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var status: TextView
    private lateinit var reportView: TextView
    private lateinit var logView: TextView
    private lateinit var preview: SurfaceView
    private lateinit var firstButton: Button

    @Volatile private var report = ""
    @Volatile private var benchResult = ""
    @Volatile private var previewReady = false

    private val logLines = ArrayDeque<String>()
    private var logDirty = false
    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val server = ReportServer { fullText() }
    private var usbReader: UsbPadReader? = null
    private var benchRunning = false

    // Event counters, reset every second to show rates.
    private val padEvents = ConcurrentHashMap<String, AtomicInteger>()
    private val mouseSamples = AtomicInteger(0)
    private val capturedSamples = AtomicInteger(0)
    private var lastPadLog = 0L
    private var lastMouseLog = 0L
    private var lastCaptureLog = 0L
    private var capturedDx = 0f
    private var capturedDy = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        setContentView(root)
        server.start()
        refreshReport()
        log("Probe started. Press buttons on every controller; move every mouse.")
        main.post(ticker)
        registerUsbReceiver()
        if (Build.VERSION.SDK_INT >= 31) {
            // Ask for raw, un-batched input so the rates below are the device's real rates.
            root.requestUnbufferedDispatch(InputDevice.SOURCE_CLASS_JOYSTICK or InputDevice.SOURCE_CLASS_POINTER)
        }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        usbReader?.stop()
        server.stop()
        try {
            unregisterReceiver(usbReceiver)
        } catch (t: Throwable) {
        }
        super.onDestroy()
    }

    // ---- UI -------------------------------------------------------------------------------

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun buildUi() {
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(16, 16, 20))
            isFocusable = true
            isFocusableInTouchMode = true
        }
        root.setOnCapturedPointerListener { _, e -> onCaptured(e) }

        preview = SurfaceView(this)
        preview.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) { previewReady = true }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) { previewReady = false }
        })
        root.addView(preview, FrameLayout.LayoutParams(dp(320), dp(180), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(24); rightMargin = dp(40)
        })

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(40), dp(24), dp(40), dp(20))
        }
        column.addView(text("Cloudlight Probe", 26f, bold = true))
        status = text("", 14f).apply { setTextColor(Color.rgb(180, 167, 245)) }
        column.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(340)
        })

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        firstButton = button("Refresh report") { refreshReport() }
        buttons.addView(firstButton)
        buttons.addView(button("Decode test") { runBench() })
        buttons.addView(button("Try 1080p 120 Hz") { tryMode(1080, 119f) })
        buttons.addView(button("Try 4K 60 Hz") { tryMode(2160, 59f) })
        buttons.addView(button("Mouse capture") { startCapture() })
        buttons.addView(button("Read USB pad") { toggleUsbRead() })
        buttons.addView(button("Save report") { saveReport() })
        val buttonScroll = HorizontalScrollView(this).apply { addView(buttons); isHorizontalScrollBarEnabled = false }
        column.addView(buttonScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12); bottomMargin = dp(12); rightMargin = dp(340)
        })

        val split = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        reportView = text("", 12f, mono = true)
        val reportScroll = ScrollView(this).apply {
            addView(reportView)
            isFocusable = true
            background = focusBackground(0)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        logView = text("", 12f, mono = true).apply { setTextColor(Color.rgb(200, 200, 210)) }
        val logBox = FrameLayout(this).apply {
            addView(logView)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(24, 24, 30))
        }
        split.addView(reportScroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.3f))
        split.addView(logBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { leftMargin = dp(16) })
        column.addView(split, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.post { firstButton.requestFocus() }
    }

    private fun text(s: String, size: Float, bold: Boolean = false, mono: Boolean = false) = TextView(this).apply {
        text = s
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        typeface = when {
            mono -> Typeface.MONOSPACE
            bold -> Typeface.DEFAULT_BOLD
            else -> Typeface.DEFAULT
        }
    }

    private fun focusBackground(fill: Int): StateListDrawable {
        val focused = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(if (fill == 0) Color.rgb(28, 28, 36) else Color.rgb(180, 167, 245))
            setStroke(dp(2), Color.rgb(180, 167, 245))
        }
        val normal = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(if (fill == 0) Color.rgb(22, 22, 28) else Color.rgb(40, 40, 50))
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(Color.rgb(16, 16, 20), Color.WHITE),
        ))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        background = focusBackground(1)
        setPadding(dp(18), dp(10), dp(18), dp(10))
        stateListAnimator = null
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(10)
        }
        setOnClickListener { action() }
    }

    // ---- Report ---------------------------------------------------------------------------

    private fun refreshReport() {
        report = Report.build(this)
        reportView.text = fullReportBody()
        log("Report refreshed")
    }

    private fun fullReportBody(): String = buildString {
        append(report)
        if (benchResult.isNotEmpty()) append("== Decode test ==\n").append(benchResult).append('\n')
    }

    private fun fullText(): String = buildString {
        append(fullReportBody())
        append("== Event log (newest last) ==\n")
        synchronized(logLines) { logLines.forEach { append(it).append('\n') } }
    }

    private fun saveReport() {
        val dir = getExternalFilesDir(null) ?: filesDir
        val file = File(dir, "cloudlight-probe-report.txt")
        file.writeText(fullText())
        log("Saved to ${file.absolutePath}")
    }

    // ---- Display modes --------------------------------------------------------------------

    private fun tryMode(height: Int, minHz: Float) {
        val d = Report.currentDisplay(this)
        val mode = d.supportedModes.filter { it.physicalHeight == height && it.refreshRate >= minHz }
            .minByOrNull { it.refreshRate }
        if (mode == null) {
            log("No ${height}p mode at ${minHz.toInt() + 1} Hz or more is offered by this display")
            return
        }
        val attrs = window.attributes
        attrs.preferredDisplayModeId = mode.modeId
        window.attributes = attrs
        log("Asked for mode #${mode.modeId} ${mode.physicalWidth}x${mode.physicalHeight} @ ${Report.fmt(mode.refreshRate)} Hz")
        main.postDelayed({
            val now = Report.currentDisplay(this).mode
            val ok = now.modeId == mode.modeId
            log("Display is now ${now.physicalWidth}x${now.physicalHeight} @ ${Report.fmt(now.refreshRate)} Hz (${if (ok) "switched" else "DID NOT switch"})")
        }, 4000)
    }

    // ---- Decode benchmark -----------------------------------------------------------------

    private fun runBench() {
        if (benchRunning) return
        benchRunning = true
        log("Decode test running; this takes about 30 seconds")
        val surface = if (previewReady) preview.holder.surface else null
        thread(name = "decode-bench") {
            val result = DecodeBench(this) { log(it) }.runAll(surface)
            main.post {
                benchResult = result
                reportView.text = fullReportBody()
                benchRunning = false
                log("Decode test finished")
            }
        }
    }

    // ---- Mouse / pointer capture ---------------------------------------------------------

    private fun startCapture() {
        root.requestFocus()
        root.requestPointerCapture()
        log("Mouse capture requested: move the mouse, press its buttons. Press Esc, B or Back to release.")
    }

    private fun stopCapture() {
        root.releasePointerCapture()
        firstButton.requestFocus()
    }

    override fun onPointerCaptureChanged(hasCapture: Boolean) {
        log("Pointer capture ${if (hasCapture) "ON (raw relative mouse)" else "off"}")
    }

    private fun onCaptured(e: MotionEvent): Boolean {
        val samples = e.historySize + 1
        capturedSamples.addAndGet(samples)
        for (i in 0 until e.historySize) {
            capturedDx += e.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, i).let { if (it == 0f) e.getHistoricalX(i) else it }
            capturedDy += e.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, i).let { if (it == 0f) e.getHistoricalY(i) else it }
        }
        capturedDx += e.getAxisValue(MotionEvent.AXIS_RELATIVE_X).let { if (it == 0f) e.x else it }
        capturedDy += e.getAxisValue(MotionEvent.AXIS_RELATIVE_Y).let { if (it == 0f) e.y else it }
        val now = SystemClock.uptimeMillis()
        if (e.actionMasked != MotionEvent.ACTION_MOVE || now - lastCaptureLog > 200) {
            lastCaptureLog = now
            log("captured ${MotionEvent.actionToString(e.actionMasked)} sum dx=${"%.0f".format(capturedDx)} dy=${"%.0f".format(capturedDy)} buttons=${e.buttonState} wheel=${e.getAxisValue(MotionEvent.AXIS_VSCROLL)} from \"${e.device?.name}\"")
        }
        return true
    }

    // ---- USB pad -------------------------------------------------------------------------

    private val usbAction by lazy { "$packageName.USB_PERMISSION" }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                usbAction -> {
                    val device = usbDeviceExtra(intent)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    log("USB permission ${if (granted) "granted" else "denied"} for ${device?.deviceName}")
                    if (granted && device != null) startUsbReader(device)
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    log("USB attached: ${usbDeviceExtra(intent)?.let { "${Report.hex4(it.vendorId)}:${Report.hex4(it.productId)} ${it.deviceName}" }}")
                    refreshReport()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    log("USB detached: ${usbDeviceExtra(intent)?.deviceName}")
                    refreshReport()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun usbDeviceExtra(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(usbAction)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
        else registerReceiver(usbReceiver, filter)
    }

    private fun toggleUsbRead() {
        usbReader?.let {
            it.stop()
            usbReader = null
            log("USB reading stopped (Android gets the device back)")
            return
        }
        val usb = getSystemService(UsbManager::class.java) ?: return log("No USB host support")
        val device = usb.deviceList.values.firstOrNull { Report.isPadCandidate(it) }
            ?: return log("No USB gamepad or 8BitDo dongle found. Plug the dongle in, then Refresh report.")
        if (usb.hasPermission(device)) {
            startUsbReader(device)
        } else {
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(this, 0, Intent(usbAction).setPackage(packageName), flags)
            usb.requestPermission(device, pi)
            log("Asking permission for ${Report.hex4(device.vendorId)}:${Report.hex4(device.productId)}; accept the dialog")
        }
    }

    private fun startUsbReader(device: UsbDevice) {
        val usb = getSystemService(UsbManager::class.java) ?: return
        val reader = UsbPadReader(usb, device) { log(it) }
        if (reader.start()) {
            usbReader = reader
            log("Reading the pad directly over USB. Move sticks and press buttons, then press Read USB pad again to stop.")
        }
    }

    // ---- Controller and mouse events -------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val dev = event.device
        if (event.repeatCount == 0) {
            val name = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")
            val action = if (event.action == KeyEvent.ACTION_DOWN) "down" else "up"
            log("key $name $action  \"${dev?.name}\" ${dev?.let { Report.hex4(it.vendorId) + ":" + Report.hex4(it.productId) } ?: ""} src=${Report.sourceNames(event.source)} scan=${event.scanCode}")
        }
        val releaseKeys = setOf(KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK)
        if (root.hasPointerCapture() && event.keyCode in releaseKeys) {
            if (event.action == KeyEvent.ACTION_UP) stopCapture()
            return true
        }
        // Gamepad A activates the focused button, like OK on a remote.
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            if (event.action == KeyEvent.ACTION_UP) currentFocus?.performClick()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val src = event.source
        val now = SystemClock.uptimeMillis()
        if (src and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) {
            val name = event.device?.name ?: "device ${event.deviceId}"
            padEvents.getOrPut(name) { AtomicInteger(0) }.addAndGet(event.historySize + 1)
            if (now - lastPadLog > 120) {
                lastPadLog = now
                fun a(axis: Int) = "%+.2f".format(event.getAxisValue(axis))
                log("pad \"$name\" LX ${a(MotionEvent.AXIS_X)} LY ${a(MotionEvent.AXIS_Y)} RX ${a(MotionEvent.AXIS_Z)} RY ${a(MotionEvent.AXIS_RZ)} " +
                    "LT ${a(MotionEvent.AXIS_LTRIGGER)}/${a(MotionEvent.AXIS_BRAKE)} RT ${a(MotionEvent.AXIS_RTRIGGER)}/${a(MotionEvent.AXIS_GAS)} " +
                    "hat ${a(MotionEvent.AXIS_HAT_X)},${a(MotionEvent.AXIS_HAT_Y)}")
            }
        } else if (src and InputDevice.SOURCE_CLASS_POINTER != 0) {
            mouseSamples.addAndGet(event.historySize + 1)
            if (event.actionMasked != MotionEvent.ACTION_HOVER_MOVE && event.actionMasked != MotionEvent.ACTION_MOVE || now - lastMouseLog > 200) {
                lastMouseLog = now
                log("mouse ${MotionEvent.actionToString(event.actionMasked)} x=${event.x.toInt()} y=${event.y.toInt()} rel=${event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)},${event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)} buttons=${event.buttonState} \"${event.device?.name}\"")
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    // ---- Log and status --------------------------------------------------------------------

    private fun log(line: String) {
        val stamped = "${clock.format(Date())}  $line"
        synchronized(logLines) {
            logLines.addLast(stamped)
            while (logLines.size > 400) logLines.removeFirst()
            logDirty = true
        }
    }

    private val ticker = object : Runnable {
        private var lastRates = 0L
        override fun run() {
            val now = SystemClock.uptimeMillis()
            if (now - lastRates >= 1000) {
                lastRates = now
                val pads = padEvents.entries.joinToString { "${it.key}: ${it.value.getAndSet(0)}/s" }.ifEmpty { "none moving" }
                val ip = Report.localAddresses().firstOrNull()?.substringBefore(' ')
                val url = if (ip != null) "Report on your PC: http://$ip:${ReportServer.PORT}" else "No network address"
                val usbRate = usbReader?.reports?.getAndSet(0)?.let { "  |  USB pad reports $it/s" } ?: ""
                status.text = "$url\nPad events: $pads  |  mouse ${mouseSamples.getAndSet(0)}/s  |  captured mouse ${capturedSamples.getAndSet(0)}/s$usbRate"
            }
            val lines = synchronized(logLines) {
                if (!logDirty) null else {
                    logDirty = false
                    logLines.toList().takeLast(48)
                }
            }
            if (lines != null) logView.text = lines.joinToString("\n")
            main.postDelayed(this, 100)
        }
    }
}
