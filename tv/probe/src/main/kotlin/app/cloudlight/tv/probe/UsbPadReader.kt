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
        while (running.get()) {
            val n = conn.bulkTransfer(ep, buf, buf.size, 100)
            if (n > 0) {
                reports.incrementAndGet()
                val hex = buildString(n * 3) {
                    for (i in 0 until n) {
                        if (i > 0) append(' ')
                        append(Report.hex2(buf[i].toInt()))
                    }
                }
                lastReport = hex
                val now = SystemClock.uptimeMillis()
                if (now - lastLog > 250) {
                    lastLog = now
                    onLog("USB report ($n B): $hex")
                }
            } else if (n < 0 && !running.get()) {
                break
            }
        }
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
