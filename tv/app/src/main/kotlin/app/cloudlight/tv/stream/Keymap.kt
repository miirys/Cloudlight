package app.cloudlight.tv.stream

import android.view.KeyEvent

/**
 * Android key codes to what a Windows game machine expects: a virtual-key code plus the
 * key's USB HID usage. Layout-independent, like a physical keyboard plugged into the server.
 */
object Keymap {

    data class Key(val vk: Int, val scan: Int)

    private val map = HashMap<Int, Key>(160).apply {
        // Letters: VK 'A'.., HID 0x04..
        for (i in 0 until 26) put(KeyEvent.KEYCODE_A + i, Key(0x41 + i, 0x04 + i))
        // Digits on the main row: VK '0'.., HID 1..9 = 0x1E.., 0 = 0x27.
        put(KeyEvent.KEYCODE_0, Key(0x30, 0x27))
        for (i in 1..9) put(KeyEvent.KEYCODE_0 + i, Key(0x30 + i, 0x1D + i))
        // Function keys.
        for (i in 0 until 12) put(KeyEvent.KEYCODE_F1 + i, Key(0x70 + i, 0x3A + i))

        put(KeyEvent.KEYCODE_ENTER, Key(0x0D, 0x28))
        put(KeyEvent.KEYCODE_ESCAPE, Key(0x1B, 0x29))
        put(KeyEvent.KEYCODE_DEL, Key(0x08, 0x2A))
        put(KeyEvent.KEYCODE_TAB, Key(0x09, 0x2B))
        put(KeyEvent.KEYCODE_SPACE, Key(0x20, 0x2C))
        put(KeyEvent.KEYCODE_MINUS, Key(0xBD, 0x2D))
        put(KeyEvent.KEYCODE_EQUALS, Key(0xBB, 0x2E))
        put(KeyEvent.KEYCODE_LEFT_BRACKET, Key(0xDB, 0x2F))
        put(KeyEvent.KEYCODE_RIGHT_BRACKET, Key(0xDD, 0x30))
        put(KeyEvent.KEYCODE_BACKSLASH, Key(0xDC, 0x31))
        put(KeyEvent.KEYCODE_SEMICOLON, Key(0xBA, 0x33))
        put(KeyEvent.KEYCODE_APOSTROPHE, Key(0xDE, 0x34))
        put(KeyEvent.KEYCODE_GRAVE, Key(0xC0, 0x35))
        put(KeyEvent.KEYCODE_COMMA, Key(0xBC, 0x36))
        put(KeyEvent.KEYCODE_PERIOD, Key(0xBE, 0x37))
        put(KeyEvent.KEYCODE_SLASH, Key(0xBF, 0x38))
        put(KeyEvent.KEYCODE_CAPS_LOCK, Key(0x14, 0x39))
        put(KeyEvent.KEYCODE_SYSRQ, Key(0x2C, 0x46))
        put(KeyEvent.KEYCODE_SCROLL_LOCK, Key(0x91, 0x47))
        put(KeyEvent.KEYCODE_BREAK, Key(0x13, 0x48))
        put(KeyEvent.KEYCODE_INSERT, Key(0x2D, 0x49))
        put(KeyEvent.KEYCODE_MOVE_HOME, Key(0x24, 0x4A))
        put(KeyEvent.KEYCODE_PAGE_UP, Key(0x21, 0x4B))
        put(KeyEvent.KEYCODE_FORWARD_DEL, Key(0x2E, 0x4C))
        put(KeyEvent.KEYCODE_MOVE_END, Key(0x23, 0x4D))
        put(KeyEvent.KEYCODE_PAGE_DOWN, Key(0x22, 0x4E))
        put(KeyEvent.KEYCODE_DPAD_RIGHT, Key(0x27, 0x4F))
        put(KeyEvent.KEYCODE_DPAD_LEFT, Key(0x25, 0x50))
        put(KeyEvent.KEYCODE_DPAD_DOWN, Key(0x28, 0x51))
        put(KeyEvent.KEYCODE_DPAD_UP, Key(0x26, 0x52))
        put(KeyEvent.KEYCODE_NUM_LOCK, Key(0x90, 0x53))
        put(KeyEvent.KEYCODE_NUMPAD_DIVIDE, Key(0x6F, 0x54))
        put(KeyEvent.KEYCODE_NUMPAD_MULTIPLY, Key(0x6A, 0x55))
        put(KeyEvent.KEYCODE_NUMPAD_SUBTRACT, Key(0x6D, 0x56))
        put(KeyEvent.KEYCODE_NUMPAD_ADD, Key(0x6B, 0x57))
        put(KeyEvent.KEYCODE_NUMPAD_ENTER, Key(0x0D, 0x58))
        put(KeyEvent.KEYCODE_NUMPAD_0, Key(0x60, 0x62))
        for (i in 1..9) put(KeyEvent.KEYCODE_NUMPAD_0 + i, Key(0x60 + i, 0x58 + i))
        put(KeyEvent.KEYCODE_NUMPAD_DOT, Key(0x6E, 0x63))
        put(KeyEvent.KEYCODE_MENU, Key(0x5D, 0x65))

        put(KeyEvent.KEYCODE_CTRL_LEFT, Key(0xA2, 0xE0))
        put(KeyEvent.KEYCODE_SHIFT_LEFT, Key(0xA0, 0xE1))
        put(KeyEvent.KEYCODE_ALT_LEFT, Key(0xA4, 0xE2))
        put(KeyEvent.KEYCODE_META_LEFT, Key(0x5B, 0xE3))
        put(KeyEvent.KEYCODE_CTRL_RIGHT, Key(0xA3, 0xE4))
        put(KeyEvent.KEYCODE_SHIFT_RIGHT, Key(0xA1, 0xE5))
        put(KeyEvent.KEYCODE_ALT_RIGHT, Key(0xA5, 0xE6))
        put(KeyEvent.KEYCODE_META_RIGHT, Key(0x5C, 0xE7))
    }

    fun of(keyCode: Int): Key? = map[keyCode]

    fun modifiers(e: KeyEvent): Int {
        var m = 0
        if (e.isShiftPressed) m = m or InputEncoder.MOD_SHIFT
        if (e.isCtrlPressed) m = m or InputEncoder.MOD_CTRL
        if (e.isAltPressed) m = m or InputEncoder.MOD_ALT
        if (e.isMetaPressed) m = m or InputEncoder.MOD_META
        if (e.isCapsLockOn) m = m or InputEncoder.MOD_CAPS
        if (e.isNumLockOn) m = m or InputEncoder.MOD_NUM
        return m
    }

    private const val SHIFTED_DIGITS = ")!@#$%^&*("
    private val punctuation = mapOf(
        ' ' to (KeyEvent.KEYCODE_SPACE to false),
        '-' to (KeyEvent.KEYCODE_MINUS to false), '_' to (KeyEvent.KEYCODE_MINUS to true),
        '=' to (KeyEvent.KEYCODE_EQUALS to false), '+' to (KeyEvent.KEYCODE_EQUALS to true),
        '[' to (KeyEvent.KEYCODE_LEFT_BRACKET to false), '{' to (KeyEvent.KEYCODE_LEFT_BRACKET to true),
        ']' to (KeyEvent.KEYCODE_RIGHT_BRACKET to false), '}' to (KeyEvent.KEYCODE_RIGHT_BRACKET to true),
        '\\' to (KeyEvent.KEYCODE_BACKSLASH to false), '|' to (KeyEvent.KEYCODE_BACKSLASH to true),
        ';' to (KeyEvent.KEYCODE_SEMICOLON to false), ':' to (KeyEvent.KEYCODE_SEMICOLON to true),
        '\'' to (KeyEvent.KEYCODE_APOSTROPHE to false), '"' to (KeyEvent.KEYCODE_APOSTROPHE to true),
        '`' to (KeyEvent.KEYCODE_GRAVE to false), '~' to (KeyEvent.KEYCODE_GRAVE to true),
        ',' to (KeyEvent.KEYCODE_COMMA to false), '<' to (KeyEvent.KEYCODE_COMMA to true),
        '.' to (KeyEvent.KEYCODE_PERIOD to false), '>' to (KeyEvent.KEYCODE_PERIOD to true),
        '/' to (KeyEvent.KEYCODE_SLASH to false), '?' to (KeyEvent.KEYCODE_SLASH to true),
        '\n' to (KeyEvent.KEYCODE_ENTER to false), '\t' to (KeyEvent.KEYCODE_TAB to false),
    )

    /** The key (and whether Shift is held) that types [c] on a US keyboard, or null. */
    fun forChar(c: Char): Pair<Key, Boolean>? {
        val (code, shift) = when (c) {
            in 'a'..'z' -> (KeyEvent.KEYCODE_A + (c - 'a')) to false
            in 'A'..'Z' -> (KeyEvent.KEYCODE_A + (c - 'A')) to true
            in '0'..'9' -> (KeyEvent.KEYCODE_0 + (c - '0')) to false
            in SHIFTED_DIGITS -> (KeyEvent.KEYCODE_0 + SHIFTED_DIGITS.indexOf(c)) to true
            else -> punctuation[c] ?: return null
        }
        return (map[code] ?: return null) to shift
    }

    val SHIFT = Key(0xA0, 0xE1)
    val BACKSPACE = Key(0x08, 0x2A)

    // Handy keys for the in-game menu.
    val ESCAPE = Key(0x1B, 0x29)
    val ENTER = Key(0x0D, 0x28)
    val TAB = Key(0x09, 0x2B)
    val ALT = Key(0xA4, 0xE2)
    val WIN = Key(0x5B, 0xE3)
    val F11 = Key(0x7A, 0x44)
}
