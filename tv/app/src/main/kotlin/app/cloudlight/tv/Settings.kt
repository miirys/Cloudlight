package app.cloudlight.tv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Resolution and frame rate to ask GeForce NOW for. */
enum class StreamMode(val label: String, val width: Int, val height: Int, val fps: Int) {
    UHD120("4K at 120 fps", 3840, 2160, 120),
    UHD60("4K at 60 fps", 3840, 2160, 60),
    QHD120("1440p at 120 fps", 2560, 1440, 120),
    QHD60("1440p at 60 fps", 2560, 1440, 60),
    FHD120("1080p at 120 fps", 1920, 1080, 120),
    FHD60("1080p at 60 fps", 1920, 1080, 60),
    HD60("720p at 60 fps", 1280, 720, 60),
}

enum class VideoCodec(val label: String, val sdpName: String) {
    AUTO("Automatic", "AUTO"),
    HEVC("HEVC (H.265)", "H265"),
    AV1("AV1", "AV1"),
    H264("H.264", "H264"),
}

/** How decoded video reaches the screen. */
enum class VideoPath(val label: String) {
    AUTO("Automatic"),
    DIRECT("Direct"),
    COMPATIBLE("Compatible"),
}

enum class StatsMode(val label: String) { OFF("Off"), COMPACT("Compact"), DETAILED("Detailed") }

enum class StatsCorner(val label: String) { TOP_LEFT("Top left"), TOP_RIGHT("Top right"), BOTTOM_LEFT("Bottom left"), BOTTOM_RIGHT("Bottom right") }

/** Languages GeForce NOW can launch games in. */
enum class GameLanguage(val label: String, val code: String) {
    EN_US("English (US)", "en_US"),
    EN_GB("English (UK)", "en_GB"),
    DE("Deutsch", "de_DE"),
    FR("Français", "fr_FR"),
    ES("Español", "es_ES"),
    IT("Italiano", "it_IT"),
    PT_BR("Português (Brasil)", "pt_BR"),
    NL("Nederlands", "nl_NL"),
    PL("Polski", "pl_PL"),
    RU("Русский", "ru_RU"),
    TR("Türkçe", "tr_TR"),
    JA("日本語", "ja_JP"),
    KO("한국어", "ko_KR"),
    ZH("简体中文", "zh_CN"),
}

/** Saved user choices, readable from Compose. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("cloudlight", Context.MODE_PRIVATE)

    private inline fun <reified E : Enum<E>> load(key: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == prefs.getString(key, null) } ?: default

    private inline fun <reified E : Enum<E>> cycle(current: E, step: Int): E {
        val all = enumValues<E>()
        return all[(current.ordinal + step).mod(all.size)]
    }

    var mode by mutableStateOf(load("mode", StreamMode.UHD60))
        private set
    var bitrateMbps by mutableIntStateOf(prefs.getInt("bitrate", 75))
        private set
    var codec by mutableStateOf(load("codec", VideoCodec.AUTO))
        private set
    var stats by mutableStateOf(load("statsMode", if (prefs.getBoolean("stats", false)) StatsMode.COMPACT else StatsMode.OFF))
        private set
    var statsCorner by mutableStateOf(load("statsCorner", StatsCorner.TOP_RIGHT))
        private set
    var language by mutableStateOf(load("language", GameLanguage.EN_US))
        private set
    var lowLatency by mutableStateOf(prefs.getBoolean("reflex", true))
        private set
    var tenBit by mutableStateOf(prefs.getBoolean("tenBit", false))
        private set
    var volume by mutableIntStateOf(prefs.getInt("volume", 100))
        private set
    var mouseSpeed by mutableFloatStateOf(prefs.getFloat("mouseSpeed", 1f))
        private set
    var holdToOpenMs by mutableIntStateOf(prefs.getInt("holdMs", 650))
        private set
    var sessionTimer by mutableStateOf(prefs.getBoolean("timer", true))
        private set
    var videoPath by mutableStateOf(load("videoPath", VideoPath.AUTO))
        private set
    /** Set when the direct path took the app down on this device; Automatic then avoids it. */
    var directFailed by mutableStateOf(prefs.getBoolean("directFailed", false))
        private set

    fun cycleVideoPath(step: Int) {
        videoPath = cycle(videoPath, step)
        prefs.edit().putString("videoPath", videoPath.name).apply()
    }

    fun markDirectFailed() {
        directFailed = true
        prefs.edit().putBoolean("directFailed", true).apply()
    }

    /** Name of the chosen server location, or empty for automatic. */
    var region by mutableStateOf(prefs.getString("region", "") ?: "")
        private set

    fun chooseRegion(name: String) {
        region = name
        prefs.edit().putString("region", name).apply()
    }

    fun cycleMode(step: Int) {
        mode = cycle(mode, step)
        prefs.edit().putString("mode", mode.name).apply()
    }

    fun stepBitrate(step: Int) {
        val i = BITRATES.indexOf(bitrateMbps).let { if (it < 0) BITRATES.indexOf(75) else it }
        bitrateMbps = BITRATES[(i + step).coerceIn(0, BITRATES.lastIndex)]
        prefs.edit().putInt("bitrate", bitrateMbps).apply()
    }

    fun cycleCodec(step: Int) {
        codec = cycle(codec, step)
        prefs.edit().putString("codec", codec.name).apply()
    }

    fun cycleStats(step: Int = 1) = setStatsMode(cycle(stats, step))

    fun setStatsMode(m: StatsMode) {
        stats = m
        prefs.edit().putString("statsMode", m.name).apply()
    }

    fun cycleCorner(step: Int) {
        statsCorner = cycle(statsCorner, step)
        prefs.edit().putString("statsCorner", statsCorner.name).apply()
    }

    fun cycleLanguage(step: Int) {
        language = cycle(language, step)
        prefs.edit().putString("language", language.name).apply()
    }

    fun toggleLowLatency() {
        lowLatency = !lowLatency
        prefs.edit().putBoolean("reflex", lowLatency).apply()
    }

    fun toggleTenBit() {
        tenBit = !tenBit
        prefs.edit().putBoolean("tenBit", tenBit).apply()
    }

    fun toggleTimer() {
        sessionTimer = !sessionTimer
        prefs.edit().putBoolean("timer", sessionTimer).apply()
    }

    fun stepVolume(step: Int) {
        volume = (volume + step * 10).coerceIn(0, 100)
        prefs.edit().putInt("volume", volume).apply()
    }

    fun stepMouseSpeed(step: Int) {
        val i = MOUSE_SPEEDS.indexOfFirst { it >= mouseSpeed - 0.01f }.let { if (it < 0) 3 else it }
        mouseSpeed = MOUSE_SPEEDS[(i + step).coerceIn(0, MOUSE_SPEEDS.lastIndex)]
        prefs.edit().putFloat("mouseSpeed", mouseSpeed).apply()
    }

    fun stepHold(step: Int) {
        val i = HOLD_TIMES.indexOf(holdToOpenMs).let { if (it < 0) 1 else it }
        holdToOpenMs = HOLD_TIMES[(i + step).coerceIn(0, HOLD_TIMES.lastIndex)]
        prefs.edit().putInt("holdMs", holdToOpenMs).apply()
    }

    companion object {
        val BITRATES = listOf(15, 20, 30, 40, 50, 60, 75, 90, 100, 120, 150)
        val MOUSE_SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f)
        val HOLD_TIMES = listOf(400, 650, 1000, 1500)
    }
}
