package app.cloudlight.tv.probe

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Reads raw reports from a USB gamepad without any kernel driver. This is the route the real
 * app will use when the box's kernel does not recognise a controller dongle.
 */
class UsbPadReader(
    private val usb: UsbManager,
    private val device: UsbDevice,
    private val onLog: (String) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    private var connection: UsbDeviceConnection? = null
    private var claimed: UsbInterface? = null

    val reports = AtomicInteger(0)

    @Volatile
    var lastReport: String = ""
        private set

    fun start(): Boolean {
        val (intf, inEp, outEp) = pickInterface() ?: run {
            onLog("USB: no interface with an interrupt IN endpoint on ${device.deviceName}")
            return false
        }
        val conn = usb.openDevice(device) ?: run {
            onLog("USB: openDevice failed (permission?)")
            return false
        }
        if (!conn.claimInterface(intf, true)) {
            onLog("USB: claimInterface ${intf.id} failed")
            conn.close()
            return false
        }
        connection = conn
        claimed = intf
        onLog("USB: claimed interface ${intf.id} (class ${Report.hex2(intf.interfaceClass)}/${Report.hex2(intf.interfaceSubclass)}/${Report.hex2(intf.interfaceProtocol)}), reading endpoint ${Report.hex2(inEp.address)}")

        // Xbox 360 style pads. Third-party ones (8BitDo included) wait for this vendor control
        // request before they send any input; Linux's xpad driver sends the same one. Then
        // light the player-1 LED, which also tells the dongle a host driver is attached.
        if (intf.interfaceClass == 0xFF && intf.interfaceSubclass == 0x5D) {
            val magic = ByteArray(20)
            val got = conn.controlTransfer(0xC1, 0x01, 0x0100, 0x0000, magic, magic.size, 200)
            onLog("USB: 360 start-up handshake answered with $got bytes${if (got > 0) ": " + hex(magic, got) else ""}")
            if (outEp != null) {
                val led = byteArrayOf(0x01, 0x03, 0x02)
                val n = conn.bulkTransfer(outEp, led, led.size, 200)
                onLog("USB: player 1 LED command sent ($n bytes)")
            }
        }

        // Xbox One style pads stay silent until they get the start packet.
        if (intf.interfaceClass == 0xFF && intf.interfaceSubclass == 0x47 && outEp != null) {
            val init = byteArrayOf(0x05, 0x20, 0x00, 0x01, 0x00)
            val n = conn.bulkTransfer(outEp, init, init.size, 200)
            onLog("USB: sent Xbox One start packet ($n bytes)")
        }

        running.set(true)
        thread = Thread({ loop(conn, inEp) }, "usb-pad-reader").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        return true
    }

    private fun loop(conn: UsbDeviceConnection, ep: UsbEndpoint) {
        val buf = ByteArray(maxOf(64, ep.maxPacketSize))
        var lastLog = 0L
        var lastData = SystemClock.uptimeMillis()
        var silentWarned = false
        var total = 0
        while (running.get()) {
            val n = conn.bulkTransfer(ep, buf, buf.size, 100)
            val now = SystemClock.uptimeMillis()
            if (n > 0) {
                reports.incrementAndGet()
                total++
                lastData = now
                silentWarned = false
                val hex = hex(buf, n)
                lastReport = hex
                if (total == 1 || now - lastLog > 150) {
                    lastLog = now
                    val decoded = decode360(buf, n)
                    onLog(if (decoded != null) "USB pad: $decoded" else "USB report ($n B): $hex")
                }
            } else if (!silentWarned && now - lastData > 3000) {
                silentWarned = true
                onLog("USB: no reports for 3 s. Is the controller switched on and paired to the dongle (2.4G mode, not Bluetooth)?")
            }
        }
    }

    private fun hex(b: ByteArray, n: Int) = buildString(n * 3) {
        for (i in 0 until n) {
            if (i > 0) append(' ')
            append(Report.hex2(b[i].toInt()))
        }
    }

    /** Decodes a standard 20-byte Xbox 360 input report, or returns null for anything else. */
    private fun decode360(b: ByteArray, n: Int): String? {
        if (n < 14 || b[0].toInt() != 0x00 || b[1].toInt() != 0x14) return null
        fun s16(i: Int) = ((b[i].toInt() and 0xFF) or (b[i + 1].toInt() shl 8)).toShort().toInt()
        val b2 = b[2].toInt() and 0xFF
        val b3 = b[3].toInt() and 0xFF
        val names = listOf(
            b2 and 0x01 to "Up", b2 and 0x02 to "Down", b2 and 0x04 to "Left", b2 and 0x08 to "Right",
            b2 and 0x10 to "Start", b2 and 0x20 to "Back", b2 and 0x40 to "LS", b2 and 0x80 to "RS",
            b3 and 0x01 to "LB", b3 and 0x02 to "RB", b3 and 0x04 to "Guide",
            b3 and 0x10 to "A", b3 and 0x20 to "B", b3 and 0x40 to "X", b3 and 0x80 to "Y",
        ).filter { it.first != 0 }.joinToString(" ") { it.second }.ifEmpty { "-" }
        return "buttons [$names] LT ${b[4].toInt() and 0xFF} RT ${b[5].toInt() and 0xFF} " +
            "LX ${s16(6)} LY ${s16(8)} RX ${s16(10)} RY ${s16(12)}"
    }

    fun stop() {
        running.set(false)
        thread?.join(300)
        thread = null
        claimed?.let { connection?.releaseInterface(it) }
        connection?.close()
        connection = null
        claimed = null
    }

    private fun pickInterface(): Triple<UsbInterface, UsbEndpoint, UsbEndpoint?>? {
        val candidates = (0 until device.interfaceCount).map { device.getInterface(it) }
            .sortedBy {
                // Prefer Xbox-style pad interfaces, then generic HID, then anything else.
                when {
                    it.interfaceClass == 0xFF && (it.interfaceSubclass == 0x5D || it.interfaceSubclass == 0x47) && it.interfaceProtocol != 0x03 -> 0
                    it.interfaceClass == UsbConstants.USB_CLASS_HID -> 1
                    else -> 2
                }
            }
        for (intf in candidates) {
            var inEp: UsbEndpoint? = null
            var outEp: UsbEndpoint? = null
            for (i in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(i)
                if (ep.type != UsbConstants.USB_ENDPOINT_XFER_INT) continue
                if (ep.direction == UsbConstants.USB_DIR_IN && inEp == null) inEp = ep
                if (ep.direction == UsbConstants.USB_DIR_OUT && outEp == null) outEp = ep
            }
            if (inEp != null) return Triple(intf, inEp, outEp)
        }
        return null
    }
}
