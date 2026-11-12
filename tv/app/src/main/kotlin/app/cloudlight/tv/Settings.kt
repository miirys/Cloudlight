package app.cloudlight.tv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Resolution and frame rate to ask GeForce NOW for. */
// GeForce NOW's WebRTC streams stop at 1440p; 4K needs NVIDIA's native protocol.
enum class StreamMode(val label: String, val width: Int, val height: Int, val fps: Int) {
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

/** How decoded pictures are handed to the screen. */
enum class FramePacing(val label: String, val swapInterval: Int) {
    SMOOTH("Smooth", 1),
    FASTEST("Lowest delay", 0),
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

    var mode by mutableStateOf(load("mode", StreamMode.QHD120))
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

    var framePacing by mutableStateOf(load("framePacing", FramePacing.SMOOTH))
        private set

    fun cycleFramePacing(step: Int) {
        framePacing = cycle(framePacing, step)
        prefs.edit().putString("framePacing", framePacing.name).apply()
    }

    fun cycleVideoPath(step: Int) {
        videoPath = cycle(videoPath, step)
        prefs.edit().putString("videoPath", videoPath.name).apply()
    }

    fun markDirectFailed() {
        directFailed = true
        prefs.edit().putBoolean("directFailed", true).apply()
    }

    /**
     * Set when a 4K request with the TV identity got a rig that never started a WebRTC
     * stream; 4K is then asked for as the web client only.
     */
    var tvIdentityFailed by mutableStateOf(prefs.getBoolean("tvIdentityFailed", false))
        private set

    fun markTvIdentityFailed() {
        tvIdentityFailed = true
        prefs.edit().putBoolean("tvIdentityFailed", true).apply()
    }

    /** Set after a crash in the video decoder on the compatible path: stream in H.264. */
    var safeCodec by mutableStateOf(prefs.getBoolean("safeCodec", false))
        private set
    /** Set after a crash while sound was starting: stream without sound. */
    var noAudio by mutableStateOf(prefs.getBoolean("noAudio", false))
        private set

    /**
     * After a crash during a stream, turns off the part that was starting, one step at a
     * time. Returns what changed, in words, or null if there was nothing left to change.
     */
    fun recoverFrom(path: String, step: String): String? {
        val video = step.startsWith("decoder") || step == "decoding" || step == "video"
        // A crash on the direct path: go back to the standard (compatible) path.
        val canCompat = path == "direct" && videoPath == VideoPath.DIRECT
        val e = prefs.edit()
        val change = when {
            video && canCompat -> { videoPath = VideoPath.AUTO; e.putString("videoPath", VideoPath.AUTO.name); "the standard video output" }
            video && !safeCodec && codec == VideoCodec.AUTO -> { safeCodec = true; e.putBoolean("safeCodec", true); "the H.264 codec" }
            !video && !noAudio -> { noAudio = true; e.putBoolean("noAudio", true); "streaming without sound" }
            canCompat -> { videoPath = VideoPath.AUTO; e.putString("videoPath", VideoPath.AUTO.name); "the standard video output" }
            !safeCodec && codec == VideoCodec.AUTO -> { safeCodec = true; e.putBoolean("safeCodec", true); "the H.264 codec" }
            !noAudio -> { noAudio = true; e.putBoolean("noAudio", true); "streaming without sound" }
            tenBit -> { tenBit = false; e.putBoolean("tenBit", false).putBoolean("tenBitFix", true); "8-bit colour" }
            else -> null
        }
        e.apply()
        return change
    }

    /** What the automatic fixes changed, or null when none are on. */
    val fixes: String?
        get() = listOfNotNull(
            if (safeCodec && codec == VideoCodec.AUTO) "H.264" else null,
            if (noAudio) "No sound" else null,
        ).joinToString(", ").ifEmpty { null }

    fun clearFixes() {
        directFailed = false
        safeCodec = false
        noAudio = false
        if (prefs.getBoolean("tenBitFix", false)) tenBit = true
        tvIdentityFailed = false
        prefs.edit().remove("directFailed").remove("safeCodec").remove("noAudio").remove("tenBitFix").remove("tvIdentityFailed")
            .putBoolean("tenBit", tenBit).apply()
    }

    /** Clears the automatic fixes once for [tag], after an update removed the cause they guessed at. */
    fun clearFixesOnce(tag: String) {
        if (prefs.getString("fixesCleared", null) == tag) return
        clearFixes()
        prefs.edit().putString("fixesCleared", tag).apply()
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

    /** Switch the TV to its fastest refresh rate at the current resolution while the app is open. */
    var maxRefresh by mutableStateOf(prefs.getBoolean("maxRefresh", true))
        private set

    fun toggleMaxRefresh() {
        maxRefresh = !maxRefresh
        prefs.edit().putBoolean("maxRefresh", maxRefresh).apply()
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
