package app.cloudlight.tv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Stream modes the Zidoo can decode. Both mandatory targets use HEVC. */
enum class StreamMode(val label: String, val width: Int, val height: Int, val fps: Int) {
    UHD60("4K · 60 fps", 3840, 2160, 60),
    QHD120("1440p · 120 fps", 2560, 1440, 120),
    FHD120("1080p · 120 fps", 1920, 1080, 120),
    FHD60("1080p · 60 fps", 1920, 1080, 60),
}

/** Saved user choices, readable from Compose. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("cloudlight", Context.MODE_PRIVATE)

    var mode by mutableStateOf(
        StreamMode.entries.firstOrNull { it.name == prefs.getString("mode", null) } ?: StreamMode.UHD60,
    )
        private set

    var bitrateMbps by mutableIntStateOf(prefs.getInt("bitrate", 100))
        private set

    var showStats by mutableStateOf(prefs.getBoolean("stats", false))
        private set

    fun cycleMode(step: Int) {
        val all = StreamMode.entries
        mode = all[(mode.ordinal + step).mod(all.size)]
        prefs.edit().putString("mode", mode.name).apply()
    }

    fun stepBitrate(step: Int) {
        val i = BITRATES.indexOf(bitrateMbps).let { if (it < 0) BITRATES.indexOf(100) else it }
        bitrateMbps = BITRATES[(i + step).mod(BITRATES.size)]
        prefs.edit().putInt("bitrate", bitrateMbps).apply()
    }

    fun toggleStats() {
        showStats = !showStats
        prefs.edit().putBoolean("stats", showStats).apply()
    }

    companion object {
        val BITRATES = listOf(30, 50, 75, 100, 120, 150)
    }
}
