package app.cloudlight.tv.input

/**
 * Reads any HID gamepad from its report descriptor. Used when a dongle presents itself as a
 * plain HID device (8BitDo's DirectInput / Switch modes) and the box's kernel ignores it.
 */
class HidGamepad private constructor(private val fields: List<Field>, private val usesReportIds: Boolean, private val is8BitDo: Boolean) {

    private class Field(
        val reportId: Int,
        val bitOffset: Int,
        val bitSize: Int,
        val page: Int,
        val usage: Int,
        val logMin: Long,
        val logMax: Long,
    )

    private val hasZ = fields.any { it.page == PAGE_DESKTOP && it.usage == U_Z }
    private val hasRz = fields.any { it.page == PAGE_DESKTOP && it.usage == U_RZ }

    val buttonCount = fields.count { it.page == PAGE_BUTTON }
    val axisCount = fields.count { it.page == PAGE_DESKTOP && it.usage in U_X..U_RZ }

    fun decode(data: ByteArray, length: Int): PadState? {
        var offsetBits = 0
        var id = 0
        if (usesReportIds) {
            if (length < 1) return null
            id = data[0].toInt() and 0xFF
            offsetBits = 8
        }
        var buttons = 0
        var lx = 0f
        var ly = 0f
        var rx = 0f
        var ry = 0f
        var lt = 0f
        var rt = 0f
        var matched = false
        for (f in fields) {
            if (f.reportId != id) continue
            val start = offsetBits + f.bitOffset
            if (start + f.bitSize > length * 8) continue
            matched = true
            val raw = bits(data, start, f.bitSize, f.logMin < 0)
            when (f.page) {
                PAGE_BUTTON -> if (raw != 0L) buttons = buttons or buttonBit(f.usage)
                PAGE_DESKTOP -> when (f.usage) {
                    U_X -> lx = stick(raw, f)
                    U_Y -> ly = stick(raw, f)
                    U_Z -> if (hasRz) rx = stick(raw, f) else lt = trigger(raw, f)
                    U_RZ -> if (hasZ) ry = stick(raw, f) else rt = trigger(raw, f)
                    U_RX -> if (hasZ && hasRz) lt = trigger(raw, f) else rx = stick(raw, f)
                    U_RY -> if (hasZ && hasRz) rt = trigger(raw, f) else ry = stick(raw, f)
                    U_HAT -> buttons = buttons or hat((raw - f.logMin).toInt(), (f.logMax - f.logMin + 1).toInt())
                    U_DPAD_UP -> if (raw != 0L) buttons = buttons or Btn.UP
                    U_DPAD_DOWN -> if (raw != 0L) buttons = buttons or Btn.DOWN
                    U_DPAD_RIGHT -> if (raw != 0L) buttons = buttons or Btn.RIGHT
                    U_DPAD_LEFT -> if (raw != 0L) buttons = buttons or Btn.LEFT
                }
                PAGE_SIMULATION -> when (f.usage) {
                    U_BRAKE -> lt = trigger(raw, f)
                    U_ACCELERATOR -> rt = trigger(raw, f)
                }
            }
        }
        return if (matched) PadState(buttons, lx, ly, rx, ry, lt, rt) else null
    }

    private fun buttonBit(n: Int): Int = if (is8BitDo) {
        // 8BitDo's DirectInput layout: A B _ X Y _ LB RB LT RT Select Start Home LS RS.
        when (n) {
            1 -> Btn.A; 2 -> Btn.B; 4 -> Btn.X; 5 -> Btn.Y; 7 -> Btn.LB; 8 -> Btn.RB
            11 -> Btn.VIEW; 12 -> Btn.MENU; 13 -> Btn.GUIDE; 14 -> Btn.LS; 15 -> Btn.RS
            else -> 0
        }
    } else {
        when (n) {
            1 -> Btn.A; 2 -> Btn.B; 3 -> Btn.X; 4 -> Btn.Y; 5 -> Btn.LB; 6 -> Btn.RB
            9 -> Btn.VIEW; 10 -> Btn.MENU; 11 -> Btn.LS; 12 -> Btn.RS; 13 -> Btn.GUIDE
            else -> 0
        }
    }

    private fun stick(raw: Long, f: Field): Float {
        val span = (f.logMax - f.logMin).toFloat()
        if (span <= 0f) return 0f
        return (((raw - f.logMin) / span) * 2f - 1f).coerceIn(-1f, 1f)
    }

    private fun trigger(raw: Long, f: Field): Float {
        val span = (f.logMax - f.logMin).toFloat()
        if (span <= 0f) return 0f
        return ((raw - f.logMin) / span).coerceIn(0f, 1f)
    }

    private fun hat(v: Int, positions: Int): Int {
        if (positions != 8 || v !in 0..7) return 0
        return when (v) {
            0 -> Btn.UP
            1 -> Btn.UP or Btn.RIGHT
            2 -> Btn.RIGHT
            3 -> Btn.DOWN or Btn.RIGHT
            4 -> Btn.DOWN
            5 -> Btn.DOWN or Btn.LEFT
            6 -> Btn.LEFT
            else -> Btn.UP or Btn.LEFT
        }
    }

    companion object {
        private const val PAGE_DESKTOP = 0x01
        private const val PAGE_SIMULATION = 0x02
        private const val PAGE_BUTTON = 0x09
        private const val U_X = 0x30
        private const val U_Y = 0x31
        private const val U_Z = 0x32
        private const val U_RX = 0x33
        private const val U_RY = 0x34
        private const val U_RZ = 0x35
        private const val U_HAT = 0x39
        private const val U_DPAD_UP = 0x90
        private const val U_DPAD_DOWN = 0x91
        private const val U_DPAD_RIGHT = 0x92
        private const val U_DPAD_LEFT = 0x93
        private const val U_ACCELERATOR = 0xC4
        private const val U_BRAKE = 0xC5

        private fun bits(data: ByteArray, start: Int, size: Int, signed: Boolean): Long {
            var v = 0L
            for (i in 0 until size) {
                val bit = start + i
                if ((data[bit ushr 3].toInt() ushr (bit and 7)) and 1 != 0) v = v or (1L shl i)
            }
            if (signed && size in 1..63 && (v ushr (size - 1)) and 1L != 0L) v -= 1L shl size
            return v
        }

        /** Parses a HID report descriptor. Returns null when it describes no gamepad inputs. */
        fun parse(desc: ByteArray, length: Int, is8BitDo: Boolean): HidGamepad? {
            data class Globals(
                var page: Int = 0, var logMin: Long = 0, var logMax: Long = 0,
                var size: Int = 0, var count: Int = 0, var reportId: Int = 0,
            )
            var g = Globals()
            val stack = ArrayDeque<Globals>()
            val usages = ArrayList<Int>()
            var usageMin = -1
            var usageMax = -1
            val offsets = HashMap<Int, Int>()
            val fields = ArrayList<Field>()
            var anyReportId = false
            var i = 0
            while (i < length) {
                val prefix = desc[i].toInt() and 0xFF
                if (prefix == 0xFE) { // long item
                    if (i + 1 >= length) break
                    i += 3 + (desc[i + 1].toInt() and 0xFF)
                    continue
                }
                val size = when (prefix and 3) { 3 -> 4; else -> prefix and 3 }
                val type = (prefix ushr 2) and 3
                val tag = prefix ushr 4
                if (i + size >= length) break
                var u = 0L
                for (k in 0 until size) u = u or ((desc[i + 1 + k].toLong() and 0xFF) shl (8 * k))
                val s = if (size in 1..3 && (u ushr (size * 8 - 1)) and 1L != 0L) u - (1L shl (size * 8)) else if (size == 4) u.toInt().toLong() else u
                i += 1 + size
                when (type) {
                    0 -> when (tag) { // main
                        8 -> { // input
                            val constant = u and 1L != 0L
                            val variable = u and 2L != 0L
                            val start = offsets[g.reportId] ?: 0
                            if (!constant && variable) {
                                for (n in 0 until g.count) {
                                    val usage = when {
                                        usages.isNotEmpty() -> usages[minOf(n, usages.size - 1)]
                                        usageMin >= 0 -> minOf(usageMin + n, if (usageMax >= 0) usageMax else Int.MAX_VALUE)
                                        else -> -1
                                    }
                                    if (usage < 0) continue
                                    val page = if (usage > 0xFFFF) usage ushr 16 else g.page
                                    fields += Field(g.reportId, start + n * g.size, g.size, page, usage and 0xFFFF, g.logMin, g.logMax)
                                }
                            }
                            offsets[g.reportId] = start + g.size * g.count
                            usages.clear(); usageMin = -1; usageMax = -1
                        }
                        9, 11, 10, 12 -> { usages.clear(); usageMin = -1; usageMax = -1 }
                    }
                    1 -> when (tag) { // global
                        0 -> g.page = u.toInt()
                        1 -> g.logMin = s
                        2 -> g.logMax = if (g.logMin < 0) s else u
                        7 -> g.size = u.toInt()
                        8 -> { g.reportId = u.toInt(); anyReportId = true }
                        9 -> g.count = u.toInt()
                        10 -> stack.addLast(g.copy())
                        11 -> stack.removeLastOrNull()?.let { g = it }
                    }
                    2 -> when (tag) { // local
                        0 -> usages += u.toInt()
                        1 -> usageMin = u.toInt()
                        2 -> usageMax = u.toInt()
                    }
                }
            }
            val useful = fields.filter { it.page == PAGE_BUTTON || it.page == PAGE_DESKTOP || it.page == PAGE_SIMULATION }
            if (useful.none { it.page == PAGE_BUTTON } && useful.none { it.page == PAGE_DESKTOP }) return null
            return HidGamepad(useful, anyReportId, is8BitDo)
        }
    }
}
