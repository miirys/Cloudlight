package app.cloudlight.tv.probe

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Builds a plain-text hardware report: what the box can decode, display, and which
 * input devices Android and the USB host stack can see.
 */
object Report {

    private val VIDEO_TYPES = listOf("video/hevc", "video/av01", "video/avc", "video/x-vnd.on2.vp9")

    /** 8BitDo's USB vendor id. Their 2.4 GHz dongles enumerate under it in most modes. */
    const val VENDOR_8BITDO = 0x2DC8
    const val VENDOR_MICROSOFT = 0x045E

    fun build(activity: Activity): String = buildString {
        section("Device") { device(this) }
        section("Display") { display(this, activity) }
        section("Video decoders") { decoders(this) }
        section("Audio") { audio(this, activity) }
        section("Network") { network(this, activity) }
        section("Input devices (what Android created)") { inputDevices(this) }
        section("USB devices (what is physically plugged in)") { usbDevices(this, activity) }
        section("Kernel") { kernel(this) }
    }

    private inline fun StringBuilder.section(title: String, body: StringBuilder.() -> Unit) {
        append("== ").append(title).append(" ==\n")
        try {
            body()
        } catch (t: Throwable) {
            append("  (failed: ").append(t.javaClass.simpleName).append(": ").append(t.message).append(")\n")
        }
        append('\n')
    }

    private fun device(sb: StringBuilder) = with(sb) {
        append("  ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}, board ${Build.BOARD}, hw ${Build.HARDWARE})\n")
        append("  Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT}, build ${Build.DISPLAY}\n")
        if (Build.VERSION.SDK_INT >= 31) {
            append("  SoC ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}\n")
        }
        append("  ABIs ${Build.SUPPORTED_ABIS.joinToString()}\n")
        append("  CPU cores ${Runtime.getRuntime().availableProcessors()}, app heap ${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB\n")
    }

    @Suppress("DEPRECATION")
    fun currentDisplay(activity: Activity): Display =
        if (Build.VERSION.SDK_INT >= 30) activity.display!! else activity.windowManager.defaultDisplay

    @Suppress("DEPRECATION")
    private fun display(sb: StringBuilder, activity: Activity) = with(sb) {
        val d = currentDisplay(activity)
        val m = d.mode
        append("  Current mode #${m.modeId}: ${m.physicalWidth}x${m.physicalHeight} @ ${fmt(m.refreshRate)} Hz\n")
        append("  Supported modes:\n")
        d.supportedModes.sortedWith(compareBy({ -it.physicalHeight }, { -it.refreshRate })).forEach {
            val tag = when {
                it.physicalHeight == 1080 && it.refreshRate > 119f -> "   <- 1080p120"
                it.physicalHeight == 2160 && it.refreshRate in 59f..60.5f -> "   <- 4K60"
                else -> ""
            }
            append("    #${it.modeId} ${it.physicalWidth}x${it.physicalHeight} @ ${fmt(it.refreshRate)} Hz$tag\n")
        }
        val has120 = d.supportedModes.any { it.physicalHeight == 1080 && it.refreshRate > 119f }
        val has4k60 = d.supportedModes.any { it.physicalHeight == 2160 && it.refreshRate > 59f }
        append("  1080p120 mode offered: ${yes(has120)}   4K60 mode offered: ${yes(has4k60)}\n")
        append("  HDR display: ${yes(d.isHdr)}, wide color: ${yes(d.isWideColorGamut)}\n")
        d.hdrCapabilities?.let { caps ->
            val names = caps.supportedHdrTypes.map {
                when (it) {
                    Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                    Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                    Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                    4 -> "HDR10+"
                    else -> "type $it"
                }
            }
            append("  HDR types: ${names.ifEmpty { listOf("none") }.joinToString()}\n")
        }
    }

    private fun decoders(sb: StringBuilder) = with(sb) {
        val list = MediaCodecList(MediaCodecList.ALL_CODECS)
        for (info in list.codecInfos) {
            if (info.isEncoder) continue
            for (type in info.supportedTypes) {
                if (type.lowercase() !in VIDEO_TYPES) continue
                val caps = try {
                    info.getCapabilitiesForType(type)
                } catch (t: Throwable) {
                    continue
                }
                val v = caps.videoCapabilities ?: continue
                val hw = if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated
                else !(info.name.startsWith("OMX.google.") || info.name.startsWith("c2.android."))
                append("  ${info.name}  [$type]  ${if (hw) "hardware" else "software"}\n")
                if (Build.VERSION.SDK_INT >= 29 && info.isAlias) append("    alias of ${info.canonicalName}\n")
                append("    sizes ${v.supportedWidths} x ${v.supportedHeights}, fps ${v.supportedFrameRates}, bitrate ${v.bitrateRange.upper / 1_000_000} Mbps max\n")
                append("    1080p@120: ${yes(v.areSizeAndRateSupported(1920, 1080, 120.0))}" +
                    "   2160p@60: ${yes(v.areSizeAndRateSupported(3840, 2160, 60.0))}" +
                    "   1440p@120: ${yes(v.areSizeAndRateSupported(2560, 1440, 120.0))}\n")
                achievable(v, 1920, 1080)?.let { append("    measured 1080p fps range: $it\n") }
                achievable(v, 3840, 2160)?.let { append("    measured 2160p fps range: $it\n") }
                if (Build.VERSION.SDK_INT >= 29) {
                    val points = v.supportedPerformancePoints
                    if (!points.isNullOrEmpty()) {
                        val p120 = MediaCodecInfo.VideoCapabilities.PerformancePoint(1920, 1080, 120)
                        val p4k = MediaCodecInfo.VideoCapabilities.PerformancePoint(3840, 2160, 60)
                        append("    performance points cover 1080p120: ${yes(points.any { it.covers(p120) })}, 4K60: ${yes(points.any { it.covers(p4k) })}\n")
                    }
                }
                val features = listOf("low-latency", "adaptive-playback", "tunneled-playback", "partial-frame")
                    .filter { caps.isFeatureSupported(it) }
                append("    features: ${features.ifEmpty { listOf("none") }.joinToString()}; max instances ${caps.maxSupportedInstances}\n")
                val profiles = caps.profileLevels.joinToString { "p${it.profile}/l${it.level}" }
                append("    profiles: $profiles\n")
            }
        }
    }

    private fun achievable(v: MediaCodecInfo.VideoCapabilities, w: Int, h: Int): String? = try {
        v.getAchievableFrameRatesFor(w, h)?.let { "${fmt(it.lower.toFloat())}-${fmt(it.upper.toFloat())}" }
    } catch (t: Throwable) {
        null
    }

    private fun audio(sb: StringBuilder, ctx: Context) = with(sb) {
        val am = ctx.getSystemService(AudioManager::class.java)
        append("  output sample rate ${am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)}, frames/buffer ${am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)}\n")
        val pm = ctx.packageManager
        append("  low-latency audio: ${yes(pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY))}, pro audio: ${yes(pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO))}\n")
        for (dev in am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            val kind = when (dev.type) {
                AudioDeviceInfo.TYPE_HDMI -> "HDMI"
                AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
                AudioDeviceInfo.TYPE_LINE_DIGITAL -> "S/PDIF"
                else -> "type ${dev.type}"
            }
            append("  out: ${dev.productName} ($kind) channels ${dev.channelCounts.joinToString()} rates ${dev.sampleRates.joinToString()}\n")
        }
    }

    @SuppressLint("MissingPermission")
    private fun network(sb: StringBuilder, ctx: Context) = with(sb) {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps == null) {
            append("  no active network\n")
        } else {
            val transport = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                else -> "other"
            }
            append("  active: $transport, link estimate down ${caps.linkDownstreamBandwidthKbps / 1000} Mbps\n")
        }
        localAddresses().forEach { append("  address: $it\n") }
    }

    fun localAddresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nif -> nif.inetAddresses.toList().filterIsInstance<Inet4Address>().map { "${it.hostAddress} (${nif.name})" } }
    } catch (t: Throwable) {
        emptyList()
    }

    private val GAMEPAD_KEYS = intArrayOf(
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_MODE,
    )

    private fun inputDevices(sb: StringBuilder) = with(sb) {
        for (id in InputDevice.getDeviceIds()) {
            val dev = InputDevice.getDevice(id) ?: continue
            val external = if (Build.VERSION.SDK_INT >= 29) (if (dev.isExternal) "external" else "built-in") else "?"
            append("  #$id \"${dev.name}\"  ${hex4(dev.vendorId)}:${hex4(dev.productId)}  $external${if (dev.isVirtual) " virtual" else ""}\n")
            append("    sources: ${sourceNames(dev.sources)}; controller number ${dev.controllerNumber}\n")
            if (dev.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                dev.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            ) {
                val has = dev.hasKeys(*GAMEPAD_KEYS)
                val names = GAMEPAD_KEYS.indices.filter { has[it] }.map { KeyEvent.keyCodeToString(GAMEPAD_KEYS[it]).removePrefix("KEYCODE_") }
                append("    buttons: ${names.joinToString()}\n")
                val axes = dev.motionRanges.map { "${MotionEvent.axisToString(it.axis).removePrefix("AXIS_")}[${fmt(it.min)}..${fmt(it.max)} flat ${fmt(it.flat)} fuzz ${fmt(it.fuzz)}]" }
                append("    axes: ${axes.joinToString()}\n")
                if (Build.VERSION.SDK_INT >= 31) {
                    append("    rumble: ${yes(dev.vibratorManager.vibratorIds.isNotEmpty())}\n")
                }
            }
            append("    descriptor ${dev.descriptor}\n")
        }
    }

    fun sourceNames(sources: Int): String {
        val known = listOf(
            InputDevice.SOURCE_GAMEPAD to "gamepad", InputDevice.SOURCE_JOYSTICK to "joystick",
            InputDevice.SOURCE_DPAD to "dpad", InputDevice.SOURCE_KEYBOARD to "keyboard",
            InputDevice.SOURCE_MOUSE to "mouse", InputDevice.SOURCE_MOUSE_RELATIVE to "mouse-relative",
            InputDevice.SOURCE_TOUCHPAD to "touchpad", InputDevice.SOURCE_TOUCHSCREEN to "touchscreen",
            InputDevice.SOURCE_TRACKBALL to "trackball", InputDevice.SOURCE_STYLUS to "stylus",
        )
        return known.filter { (bits, _) -> sources and bits == bits }.joinToString(",") { it.second }
            .ifEmpty { "0x" + Integer.toHexString(sources) }
    }

    private fun usbDevices(sb: StringBuilder, ctx: Context) = with(sb) {
        val usb = ctx.getSystemService(UsbManager::class.java)
        if (usb == null) {
            append("  USB host API not available\n")
            return@with
        }
        val devices = usb.deviceList.values
        if (devices.isEmpty()) append("  none\n")
        val inputPairs = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
            .filter { !it.isVirtual }.map { it.vendorId to it.productId }.toSet()
        for (d in devices) {
            val names = listOfNotNull(safe { d.manufacturerName }, safe { d.productName }).joinToString(" ")
            append("  ${d.deviceName}  ${hex4(d.vendorId)}:${hex4(d.productId)}  \"$names\"  class ${d.deviceClass}\n")
            val tags = mutableListOf<String>()
            if (d.vendorId == VENDOR_8BITDO) tags += "8BitDo"
            if (d.vendorId == VENDOR_MICROSOFT) tags += "Microsoft id (Xbox mode)"
            for (i in 0 until d.interfaceCount) {
                val k = interfaceKind(d.getInterface(i).interfaceClass, d.getInterface(i).interfaceSubclass, d.getInterface(i).interfaceProtocol)
                if (k != null) tags += k
            }
            if (tags.isNotEmpty()) append("    looks like: ${tags.distinct().joinToString()}\n")
            val bound = (d.vendorId to d.productId) in inputPairs
            append("    Android input device for it: ${if (bound) "yes" else "NO (the kernel did not turn it into an input device)"}\n")
            for (i in 0 until d.interfaceCount) {
                val intf = d.getInterface(i)
                append("    if ${intf.id}: class ${hex2(intf.interfaceClass)} sub ${hex2(intf.interfaceSubclass)} proto ${hex2(intf.interfaceProtocol)}")
                val eps = (0 until intf.endpointCount).map { intf.getEndpoint(it) }.joinToString {
                    val dir = if (it.direction == UsbConstants.USB_DIR_IN) "in" else "out"
                    val type = when (it.type) {
                        UsbConstants.USB_ENDPOINT_XFER_INT -> "int"
                        UsbConstants.USB_ENDPOINT_XFER_BULK -> "bulk"
                        UsbConstants.USB_ENDPOINT_XFER_ISOC -> "iso"
                        else -> "ctl"
                    }
                    "${hex2(it.address)} $dir $type ${it.maxPacketSize}B/${it.interval}"
                }
                append(" endpoints [$eps]\n")
            }
        }
    }

    fun interfaceKind(cls: Int, sub: Int, proto: Int): String? = when {
        cls == 0xFF && sub == 0x5D && proto == 0x01 -> "XInput (Xbox 360 style) pad"
        cls == 0xFF && sub == 0x5D && proto == 0x03 -> "XInput headset/plug-in port"
        cls == 0xFF && sub == 0x47 && proto == 0xD0 -> "Xbox One style pad"
        cls == UsbConstants.USB_CLASS_HID && proto == 1 -> "HID keyboard"
        cls == UsbConstants.USB_CLASS_HID && proto == 2 -> "HID mouse"
        cls == UsbConstants.USB_CLASS_HID -> "HID (DirectInput / Switch style pad or other)"
        else -> null
    }

    fun isPadCandidate(d: UsbDevice): Boolean {
        if (d.vendorId == VENDOR_8BITDO) return true
        for (i in 0 until d.interfaceCount) {
            val intf = d.getInterface(i)
            val k = interfaceKind(intf.interfaceClass, intf.interfaceSubclass, intf.interfaceProtocol) ?: continue
            if (k.contains("pad")) return true
        }
        return false
    }

    private fun kernel(sb: StringBuilder) = with(sb) {
        append("  ${readFirstLine("/proc/version") ?: "kernel version not readable"}\n")
        append("  xpad module present: ${existsLabel("/sys/module/xpad")}\n")
        append("  hid-generic present: ${existsLabel("/sys/bus/hid/drivers/hid-generic")}\n")
    }

    private fun existsLabel(path: String): String = try {
        if (File(path).exists()) "yes" else "no or not visible to apps"
    } catch (t: Throwable) {
        "not visible to apps"
    }

    private fun readFirstLine(path: String): String? = try {
        File(path).bufferedReader().use { it.readLine() }
    } catch (t: Throwable) {
        null
    }

    private inline fun <T> safe(block: () -> T): T? = try {
        block()
    } catch (t: Throwable) {
        null
    }

    fun hex4(v: Int) = String.format("%04X", v and 0xFFFF)
    fun hex2(v: Int) = String.format("%02X", v and 0xFF)
    fun fmt(f: Float): String = if (f == f.toLong().toFloat()) f.toLong().toString() else String.format("%.2f", f)
    private fun yes(b: Boolean) = if (b) "YES" else "no"
}
