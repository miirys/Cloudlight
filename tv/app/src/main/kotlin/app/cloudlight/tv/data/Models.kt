package app.cloudlight.tv.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class Variant(val id: String, val store: String, val lastPlayedMs: Long, val selected: Boolean)

data class Game(
    val id: String,
    val title: String,
    val publisher: String?,
    val genres: List<String>,
    val favorite: Boolean,
    /** Wide artwork for the background and hero. */
    val hero: String?,
    /** Portrait box art for tiles. */
    val boxArt: String?,
    /** Landscape art for wide tiles. */
    val banner: String?,
    /** Transparent title logo, if the store has one. */
    val logo: String?,
    val variants: List<Variant>,
) {
    val lastPlayedMs: Long get() = variants.maxOfOrNull { it.lastPlayedMs } ?: 0L
    val store: String? get() = (variants.firstOrNull { it.selected } ?: variants.firstOrNull())?.store?.let(::storeName)

    companion object {
        fun parse(j: JSONObject): Game? {
            val id = j.optString("id").ifEmpty { return null }
            val title = j.optString("title").ifEmpty { return null }
            val images = j.optJSONObject("images") ?: JSONObject()
            val variants = ArrayList<Variant>()
            val vs = j.optJSONArray("variants") ?: JSONArray()
            for (i in 0 until vs.length()) {
                val v = vs.getJSONObject(i)
                val lib = v.optJSONObject("gfn")?.optJSONObject("library")
                variants += Variant(
                    id = v.optString("id"),
                    store = v.optString("appStore"),
                    lastPlayedMs = lib?.optString("lastPlayedDate")?.let(::parseTime) ?: 0L,
                    selected = lib?.optBoolean("selected") == true,
                )
            }
            val genres = j.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
            return Game(
                id = id,
                title = title,
                publisher = j.optString("publisherName").ifEmpty { j.optString("developerName").ifEmpty { null } },
                genres = genres.map(::prettyGenre),
                favorite = j.optJSONObject("library")?.optBoolean("favorited") == true,
                hero = image(images, 3840, "MARQUEE_HERO_IMAGE", "HERO_IMAGE", "KEY_ART", "FEATURE_IMAGE", "SCREENSHOTS"),
                boxArt = image(images, 720, "GAME_BOX_ART", "KEY_IMAGE"),
                banner = image(images, 1280, "TV_BANNER", "KEY_ART", "HERO_IMAGE", "FEATURE_IMAGE"),
                logo = image(images, 1200, "GAME_LOGO", format = "png"),
                variants = variants,
            )
        }

        /** NVIDIA's image CDN resizes on request; ask for exactly what a 4K screen needs. */
        private fun image(images: JSONObject, width: Int, vararg keys: String, format: String = "jpg"): String? {
            for (k in keys) {
                val v = images.opt(k)
                val url = when (v) {
                    is String -> v
                    is JSONArray -> if (v.length() > 0) v.optString(0) else null
                    else -> null
                }?.trim()
                if (!url.isNullOrEmpty() && url != "null") {
                    return if (url.contains("img.nvidiagrid.net")) "$url;f=$format;w=$width" else url
                }
            }
            return null
        }

        private fun parseTime(s: String): Long = runCatching { Instant.parse(s).toEpochMilli() }.getOrDefault(0L)

        private fun prettyGenre(s: String) = s.lowercase().split('_', ' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

        fun storeName(code: String) = when (code.uppercase()) {
            "STEAM" -> "Steam"
            "EPIC" -> "Epic Games Store"
            "EPIC_GAMES_STORE" -> "Epic Games Store"
            "UPLAY", "UBISOFT", "UBISOFT_CONNECT" -> "Ubisoft Connect"
            "ORIGIN", "EA_APP" -> "EA app"
            "BATTLENET", "BATTLE_NET" -> "Battle.net"
            "XBOX", "MICROSOFT" -> "Xbox"
            "GOG" -> "GOG"
            "NONE", "" -> "GeForce NOW"
            else -> code.lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}

/** A session running in the cloud for this account, possibly started on another device. */
data class ActiveSession(
    val sessionId: String,
    val appId: String,
    val status: Int,
    val queuePosition: Int?,
    val resolution: String?,
    val fps: Int?,
    val streamingBase: String,
    /** The rig or regional host that owns the session; claims go there. */
    val serverHost: String?,
    val gpu: String?,
) {
    val phase: String
        get() = when (status) {
            1 -> if ((queuePosition ?: 0) > 0) "In queue, position $queuePosition" else "Starting"
            2, 3 -> "Ready to resume"
            4, 5, 6 -> "Paused, ready to resume"
            else -> "Starting"
        }

    companion object {
        fun parse(j: JSONObject, base: String): ActiveSession? {
            val id = j.optString("sessionId").ifBlank { return null }
            val status = j.optInt("status", -1)
            if (status !in 1..6) return null
            val req = j.optJSONObject("sessionRequestData") ?: JSONObject()
            val monitor = j.optJSONArray("monitorSettings")?.optJSONObject(0)
                ?: req.optJSONArray("clientRequestMonitorSettings")?.optJSONObject(0)
            val w = monitor?.optInt("widthInPixels") ?: 0
            val h = monitor?.optInt("heightInPixels") ?: 0
            val queue = j.optJSONObject("seatSetupInfo")?.optInt("queuePosition", 0)?.takeIf { it > 0 }
            fun first(v: Any?): String? = when (v) {
                is String -> v.takeIf { it.isNotBlank() }
                is JSONArray -> v.optString(0).takeIf { it.isNotBlank() }
                else -> null
            }
            val conns = j.optJSONArray("connectionInfo") ?: JSONArray()
            val signal = (0 until conns.length()).mapNotNull { conns.optJSONObject(it) }.firstOrNull { it.optInt("usage") == 14 }
            val host = first(signal?.opt("ip")) ?: first(j.optJSONObject("sessionControlInfo")?.opt("ip"))
            return ActiveSession(
                sessionId = id,
                appId = req.opt("appId")?.toString().orEmpty(),
                status = status,
                queuePosition = queue,
                resolution = if (w > 0 && h > 0) "${w}×$h" else null,
                fps = monitor?.optInt("framesPerSecond")?.takeIf { it > 0 },
                streamingBase = base,
                serverHost = host,
                gpu = j.optString("gpuType").takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }
}
