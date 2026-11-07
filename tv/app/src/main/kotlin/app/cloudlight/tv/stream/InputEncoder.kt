package app.cloudlight.tv.stream

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds GeForce NOW input packets. Each event is a small binary record; from input protocol
 * version 3 on, records are wrapped with a microsecond timestamp and a framing byte that says
 * how the server should read what follows.
 */
class InputEncoder {

    /** Set from the server's handshake on the input channel. */
    @Volatile var version = 2

    private val sequence = IntArray(4) { 1 }

    private fun nowUs() = System.nanoTime() / 1000

    private fun le(size: Int) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

    /** Keeps the input path alive; sent bare, never wrapped. */
    fun heartbeat(): ByteArray = le(4).putInt(HEARTBEAT).array()

    /** [vk] is a Windows virtual-key code, [scan] the USB HID usage of the key. */
    fun key(down: Boolean, vk: Int, scan: Int, modifiers: Int): ByteArray {
        val b = le(18).putInt(if (down) KEY_DOWN else KEY_UP)
        b.order(ByteOrder.BIG_ENDIAN)
        b.putShort(vk.toShort()).putShort(modifiers.toShort()).putShort(scan.toShort()).putLong(nowUs())
        return single(b.array())
    }

    fun mouseMove(dx: Int, dy: Int): ByteArray {
        val b = le(22).putInt(MOUSE_MOVE)
        b.order(ByteOrder.BIG_ENDIAN)
        b.putShort(dx.coerceIn(-32768, 32767).toShort()).putShort(dy.coerceIn(-32768, 32767).toShort())
        b.putShort(0).putInt(0).putLong(nowUs())
        return sized(b.array())
    }

    /** [button]: 1 left, 2 middle, 3 right, 4 back, 5 forward. */
    fun mouseButton(down: Boolean, button: Int): ByteArray {
        val b = le(18).putInt(if (down) MOUSE_DOWN else MOUSE_UP)
        b.put(button.toByte()).put(0)
        b.order(ByteOrder.BIG_ENDIAN)
        b.putInt(0).putLong(nowUs())
        return single(b.array())
    }

    /** One wheel notch is 120, positive away from the user. */
    fun wheel(delta: Int): ByteArray {
        val b = le(22).putInt(WHEEL)
        b.order(ByteOrder.BIG_ENDIAN)
        b.putShort(0).putShort(delta.coerceIn(-32768, 32767).toShort()).putShort(0).putInt(0).putLong(nowUs())
        return single(b.array())
    }

    /**
     * One XInput-style controller report. Sticks are -32768..32767 with up positive, triggers
     * 0..255. [connected] is the bit mask of every pad slot in use.
     */
    fun gamepad(
        slot: Int,
        connected: Int,
        buttons: Int,
        lt: Int,
        rt: Int,
        lx: Int,
        ly: Int,
        rx: Int,
        ry: Int,
        partiallyReliable: Boolean,
    ): ByteArray {
        val b = le(38)
        b.putInt(GAMEPAD).putShort(26).putShort((slot and 3).toShort()).putShort(connected.toShort())
        b.putShort(20).putShort(buttons.toShort()).putShort(((lt and 0xff) or ((rt and 0xff) shl 8)).toShort())
        b.putShort(lx.toShort()).putShort(ly.toShort()).putShort(rx.toShort()).putShort(ry.toShort())
        b.putShort(0).putShort(85).putShort(0).putLong(nowUs())
        val payload = b.array()
        if (version <= 2) return payload
        if (!partiallyReliable) return sized(payload)
        val s = slot and 3
        val seq = synchronized(sequence) { sequence[s].also { sequence[s] = (it + 1) and 0xffff } }
        val w = ByteBuffer.allocate(16 + payload.size).order(ByteOrder.BIG_ENDIAN)
        w.put(0x23).putLong(nowUs()).put(0x26).put(s.toByte()).putShort(seq.toShort())
        w.put(0x21).putShort(payload.size.toShort()).put(payload)
        return w.array()
    }

    /** A lone event: [0x23][timestamp][0x22][record]. */
    private fun single(payload: ByteArray): ByteArray {
        if (version <= 2) return payload
        return ByteBuffer.allocate(10 + payload.size).order(ByteOrder.BIG_ENDIAN)
            .put(0x23).putLong(nowUs()).put(0x22).put(payload).array()
    }

    /** A sized event: [0x23][timestamp][0x21][length][record]. */
    private fun sized(payload: ByteArray): ByteArray {
        if (version <= 2) return payload
        return ByteBuffer.allocate(12 + payload.size).order(ByteOrder.BIG_ENDIAN)
            .put(0x23).putLong(nowUs()).put(0x21).putShort(payload.size.toShort()).put(payload).array()
    }

    companion object {
        const val HEARTBEAT = 2
        const val KEY_DOWN = 3
        const val KEY_UP = 4
        const val MOUSE_MOVE = 7
        const val MOUSE_DOWN = 8
        const val MOUSE_UP = 9
        const val WHEEL = 10
        const val GAMEPAD = 12

        // XInput button bits, as the server expects them.
        const val X_UP = 0x0001
        const val X_DOWN = 0x0002
        const val X_LEFT = 0x0004
        const val X_RIGHT = 0x0008
        const val X_START = 0x0010
        const val X_BACK = 0x0020
        const val X_LS = 0x0040
        const val X_RS = 0x0080
        const val X_LB = 0x0100
        const val X_RB = 0x0200
        const val X_GUIDE = 0x0400
        const val X_A = 0x1000
        const val X_B = 0x2000
        const val X_X = 0x4000
        const val X_Y = 0x8000

        const val MOD_SHIFT = 0x01
        const val MOD_CTRL = 0x02
        const val MOD_ALT = 0x04
        const val MOD_META = 0x08
        const val MOD_CAPS = 0x10
        const val MOD_NUM = 0x20
    }
}
