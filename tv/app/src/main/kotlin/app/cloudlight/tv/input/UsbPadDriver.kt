package app.cloudlight.tv.input

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Our own USB controller driver. Some TV kernels don't recognise 2.4 GHz dongles such as
 * 8BitDo's, so we claim the dongle ourselves and read its reports directly. Handles
 * Xbox 360 style (XInput), Xbox One style (GIP) and plain HID pads, and survives the dongle
 * re-enumerating when the controller sleeps, wakes or changes mode.
 */
class UsbPadDriver(
    private val context: Context,
    private val onState: (id: String, name: String, state: PadState) -> Unit,
    private val onGone: (id: String) -> Unit,
) {
    private val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val readers = ConcurrentHashMap<String, Reader>()
    private val asked = HashSet<String>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED, ACTION_PERMISSION -> scan()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val dev = device(intent) ?: return
                    readers.remove(dev.deviceName)?.stop()
                    asked.remove(dev.deviceName)
                }
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        scan()
    }

    fun stop() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Throwable) {
        }
        readers.values.forEach { it.stop() }
        readers.clear()
    }

    /** Opens every pad-like device that is plugged in and not open yet. */
    fun scan() {
        for (dev in usb.deviceList.values) {
            if (readers.containsKey(dev.deviceName) || kind(dev) == null) continue
            if (!usb.hasPermission(dev)) {
                if (asked.add(dev.deviceName)) {
                    val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                    val pi = PendingIntent.getBroadcast(
                        context, 0, Intent(ACTION_PERMISSION).setPackage(context.packageName), flags,
                    )
                    usb.requestPermission(dev, pi)
                }
                continue
            }
            val reader = Reader(dev)
            if (reader.open()) readers[dev.deviceName] = reader
        }
    }

    private enum class Kind { XINPUT, GIP, HID }

    private class Pick(val kind: Kind, val intf: UsbInterface, val inEp: UsbEndpoint, val outEp: UsbEndpoint?)

    private fun kind(dev: UsbDevice): Pick? {
        var hid: Pick? = null
        for (i in 0 until dev.interfaceCount) {
            val intf = dev.getInterface(i)
            var inEp: UsbEndpoint? = null
            var outEp: UsbEndpoint? = null
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.type != UsbConstants.USB_ENDPOINT_XFER_INT) continue
                if (ep.direction == UsbConstants.USB_DIR_IN && inEp == null) inEp = ep
                if (ep.direction == UsbConstants.USB_DIR_OUT && outEp == null) outEp = ep
            }
            if (inEp == null) continue
            val c = intf.interfaceClass
            val sub = intf.interfaceSubclass
            val proto = intf.interfaceProtocol
            if (c == 0xFF && sub == 0x5D && proto == 0x01) return Pick(Kind.XINPUT, intf, inEp, outEp)
            if (c == 0xFF && sub == 0x47 && proto == 0xD0) return Pick(Kind.GIP, intf, inEp, outEp)
            // Boot keyboards and mice (protocol 1 and 2) are left to Android.
            if (c == UsbConstants.USB_CLASS_HID && proto == 0 && hid == null) hid = Pick(Kind.HID, intf, inEp, outEp)
        }
        // Only take over HID devices Android did not already turn into an input device.
        if (hid != null && (dev.vendorId == VENDOR_8BITDO || !androidHasInputFor(dev))) return hid
        return null
    }

    private fun androidHasInputFor(dev: UsbDevice): Boolean =
        android.view.InputDevice.getDeviceIds().any { id ->
            val d = android.view.InputDevice.getDevice(id) ?: return@any false
            d.vendorId == dev.vendorId && d.productId == dev.productId
        }

    private inner class Reader(private val dev: UsbDevice) {
        @Volatile private var running = false
        private var conn: UsbDeviceConnection? = null
        private var pick: Pick? = null
        private var thread: Thread? = null
        private var hid: HidGamepad? = null
        private val id = "usb:" + dev.deviceName
        private val name = (dev.productName ?: "USB controller").trim() + " (USB)"

        fun open(): Boolean {
            val p = kind(dev) ?: return false
            val c = usb.openDevice(dev) ?: return false
            if (!c.claimInterface(p.intf, true)) {
                c.close()
                return false
            }
            conn = c
            pick = p
            when (p.kind) {
                Kind.XINPUT -> {
                    // Third-party 360 pads (8BitDo included) stay silent until the host sends
                    // this vendor request, as Linux's xpad does. Then light player 1.
                    val buf = ByteArray(20)
                    c.controlTransfer(0xC1, 0x01, 0x0100, 0x0000, buf, buf.size, 200)
                    p.outEp?.let { c.bulkTransfer(it, byteArrayOf(0x01, 0x03, 0x02), 3, 200) }
                }
                Kind.GIP -> p.outEp?.let { c.bulkTransfer(it, byteArrayOf(0x05, 0x20, 0x00, 0x01, 0x00), 5, 200) }
                Kind.HID -> {
                    val desc = ByteArray(1024)
                    val n = c.controlTransfer(0x81, 0x06, 0x2200, p.intf.id, desc, desc.size, 500)
                    hid = if (n > 0) HidGamepad.parse(desc, n, dev.vendorId == VENDOR_8BITDO) else null
                    if (hid == null) {
                        Log.w(TAG, "${dev.deviceName}: HID descriptor unreadable or not a gamepad ($n bytes)")
                        stop()
                        return false
                    }
                    // SET_IDLE 0: report only on change, which keeps the 8BitDo dongle quiet.
                    c.controlTransfer(0x21, 0x0A, 0, p.intf.id, null, 0, 200)
                }
            }
            running = true
            thread = Thread({ loop(c, p) }, "usb-pad").apply { start() }
            Log.i(TAG, "${dev.deviceName}: reading ${p.kind} pad \"$name\"")
            return true
        }

        private fun loop(c: UsbDeviceConnection, p: Pick) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
            val buf = ByteArray(maxOf(64, p.inEp.maxPacketSize))
            var last: PadState? = null
            var errors = 0
            while (running) {
                val n = c.bulkTransfer(p.inEp, buf, buf.size, 250)
                if (n <= 0) {
                    // A timeout is normal (no change). A run of hard errors means it is gone.
                    if (n < 0 && usb.deviceList[dev.deviceName] == null && ++errors > 3) break
                    continue
                }
                errors = 0
                val s = when (p.kind) {
                    Kind.XINPUT -> xinput(buf, n)
                    Kind.GIP -> gip(buf, n)
                    Kind.HID -> hid?.decode(buf, n)
                } ?: continue
                if (s != last) {
                    last = s
                    onState(id, name, s)
                }
            }
            onGone(id)
        }

        fun stop() {
            running = false
            thread?.join(400)
            thread = null
            pick?.let { conn?.releaseInterface(it.intf) }
            conn?.close()
            conn = null
            onGone(id)
        }
    }

    companion object {
        private const val TAG = "CloudlightUsb"
        private const val ACTION_PERMISSION = "app.cloudlight.tv.USB_PERMISSION"
        const val VENDOR_8BITDO = 0x2DC8

        private fun device(intent: Intent): UsbDevice? =
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)

        private fun s16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) or (b[i + 1].toInt() shl 8)).toShort().toInt()

        private fun axis(v: Int) = (v / 32767f).coerceIn(-1f, 1f)

        /** Xbox 360 input report: 00 14, buttons, triggers, four int16 sticks (Y up). */
        fun xinput(b: ByteArray, n: Int): PadState? {
            if (n < 14 || b[0].toInt() != 0x00 || b[1].toInt() != 0x14) return null
            val b2 = b[2].toInt() and 0xFF
            val b3 = b[3].toInt() and 0xFF
            var btn = 0
            if (b2 and 0x01 != 0) btn = btn or Btn.UP
            if (b2 and 0x02 != 0) btn = btn or Btn.DOWN
            if (b2 and 0x04 != 0) btn = btn or Btn.LEFT
            if (b2 and 0x08 != 0) btn = btn or Btn.RIGHT
            if (b2 and 0x10 != 0) btn = btn or Btn.MENU
            if (b2 and 0x20 != 0) btn = btn or Btn.VIEW
            if (b2 and 0x40 != 0) btn = btn or Btn.LS
            if (b2 and 0x80 != 0) btn = btn or Btn.RS
            if (b3 and 0x01 != 0) btn = btn or Btn.LB
            if (b3 and 0x02 != 0) btn = btn or Btn.RB
            if (b3 and 0x04 != 0) btn = btn or Btn.GUIDE
            if (b3 and 0x10 != 0) btn = btn or Btn.A
            if (b3 and 0x20 != 0) btn = btn or Btn.B
            if (b3 and 0x40 != 0) btn = btn or Btn.X
            if (b3 and 0x80 != 0) btn = btn or Btn.Y
            return PadState(
                buttons = btn,
                lx = axis(s16(b, 6)), ly = -axis(s16(b, 8)),
                rx = axis(s16(b, 10)), ry = -axis(s16(b, 12)),
                lt = (b[4].toInt() and 0xFF) / 255f, rt = (b[5].toInt() and 0xFF) / 255f,
            )
        }

        /** Xbox One (GIP) input report, command 0x20. */
        fun gip(b: ByteArray, n: Int): PadState? {
            if (n < 18 || b[0].toInt() != 0x20) return null
            val b4 = b[4].toInt() and 0xFF
            val b5 = b[5].toInt() and 0xFF
            var btn = 0
            if (b4 and 0x04 != 0) btn = btn or Btn.MENU
            if (b4 and 0x08 != 0) btn = btn or Btn.VIEW
            if (b4 and 0x10 != 0) btn = btn or Btn.A
            if (b4 and 0x20 != 0) btn = btn or Btn.B
            if (b4 and 0x40 != 0) btn = btn or Btn.X
            if (b4 and 0x80 != 0) btn = btn or Btn.Y
            if (b5 and 0x01 != 0) btn = btn or Btn.UP
            if (b5 and 0x02 != 0) btn = btn or Btn.DOWN
            if (b5 and 0x04 != 0) btn = btn or Btn.LEFT
            if (b5 and 0x08 != 0) btn = btn or Btn.RIGHT
            if (b5 and 0x10 != 0) btn = btn or Btn.LB
            if (b5 and 0x20 != 0) btn = btn or Btn.RB
            if (b5 and 0x40 != 0) btn = btn or Btn.LS
            if (b5 and 0x80 != 0) btn = btn or Btn.RS
            val lt = ((b[6].toInt() and 0xFF) or ((b[7].toInt() and 0x03) shl 8)) / 1023f
            val rt = ((b[8].toInt() and 0xFF) or ((b[9].toInt() and 0x03) shl 8)) / 1023f
            return PadState(
                buttons = btn,
                lx = axis(s16(b, 10)), ly = -axis(s16(b, 12)),
                rx = axis(s16(b, 14)), ry = -axis(s16(b, 16)),
                lt = lt, rt = rt,
            )
        }
    }
}
