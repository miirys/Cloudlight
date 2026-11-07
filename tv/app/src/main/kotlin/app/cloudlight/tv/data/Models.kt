package app.cloudlight.tv.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class Variant(
    val id: String,
    /** The store's code, e.g. STEAM or HOYOPLAY. */
    val store: String,
    val lastPlayedMs: Long,
    val selected: Boolean,
    /** In the user's library on that store (bought, synced or added by hand). */
    val owned: Boolean = false,
    val storeUrl: String? = null,
)

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
    /** A sentence or two about the game, when the catalog has one. */
    val description: String? = null,
) {
    val lastPlayedMs: Long get() = variants.maxOfOrNull { it.lastPlayedMs } ?: 0L
    val store: String? get() = (variants.firstOrNull { it.selected } ?: variants.firstOrNull())?.let { Stores.label(it, publisher) }

    /** Every store this game can be played from, by name. */
    val stores: List<String> get() = variants.map { Stores.label(it, publisher) }.distinct()

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
                    owned = lib?.optString("status") in setOf("MANUAL", "PLATFORM_SYNC", "IN_LIBRARY"),
                    storeUrl = v.optString("storeUrl").takeIf { it.startsWith("http") },
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
                description = listOf("shortDescription", "description", "longDescription")
                    .firstNotNullOfOrNull { k -> j.optString(k).trim().takeIf { it.isNotEmpty() && it != "null" } }
                    ?.let(::plainText),
            )
        }

        /** Catalog text sometimes carries HTML; keep the words. */
        private fun plainText(s: String): String = s
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

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

        fun storeName(code: String) = Stores.label(code)
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

/**
 * Store names as GeForce NOW labels them. The service's own list is loaded at sign-in; the
 * built-in names cover the common stores until then, and launchers the service calls NONE or
 * UNKNOWN are named from their store page or publisher.
 */
object Stores {
    @Volatile private var labels: Map<String, String> = emptyMap()

    fun update(map: Map<String, String>) {
        if (map.isNotEmpty()) labels = map.mapKeys { it.key.uppercase() }
    }

    private val builtIn = mapOf(
        "STEAM" to "Steam",
        "EPIC" to "Epic Games Store",
        "EPIC_GAMES_STORE" to "Epic Games Store",
        "EGS" to "Epic Games Store",
        "UPLAY" to "Ubisoft Connect",
        "UBISOFT" to "Ubisoft Connect",
        "UBISOFT_CONNECT" to "Ubisoft Connect",
        "ORIGIN" to "EA app",
        "EA_APP" to "EA app",
        "EA" to "EA app",
        "BATTLENET" to "Battle.net",
        "BATTLE_NET" to "Battle.net",
        "XBOX" to "Xbox",
        "MICROSOFT" to "Xbox",
        "MICROSOFT_STORE" to "Microsoft Store",
        "GOG" to "GOG",
        "HOYOPLAY" to "HoYoPlay",
        "HOYOVERSE" to "HoYoPlay",
        "MIHOYO" to "HoYoPlay",
        "KURO" to "Kuro Games",
        "KUROGAMES" to "Kuro Games",
        "WUTHERING_WAVES" to "Kuro Games",
        "ROCKSTAR" to "Rockstar Games Launcher",
        "ROCKSTAR_GAMES_LAUNCHER" to "Rockstar Games Launcher",
        "WARGAMING" to "Wargaming Game Center",
        "WGC" to "Wargaming Game Center",
        "GAIJIN" to "Gaijin",
        "BETHESDA" to "Bethesda.net",
        "ARC" to "Arc",
        "ITCH" to "itch.io",
        "NEXON" to "Nexon",
        "NCSOFT" to "NC Launcher",
        "PLAYSTATION" to "PlayStation PC",
        "GAMEPASS" to "PC Game Pass",
        "PC_GAME_PASS" to "PC Game Pass",
        "GRYPHLINK" to "Gryphline",
        "GRYPHLINE" to "Gryphline",
        "INFOLD" to "Infold Games",
        "LEVEL_INFINITE" to "Level Infinite",
        "GARENA" to "Garena",
        "KAKAO" to "Kakao Games",
        "STOVE" to "STOVE",
        "SMILEGATE" to "STOVE",
        "PEARL_ABYSS" to "Pearl Abyss",
        "DIGITAL_EXTREMES" to "Digital Extremes",
        "RIOT" to "Riot Client",
        "RIOT_GAMES" to "Riot Client",
        "MYTONA" to "Mytona",
        "TENCENT" to "Tencent",
        "NETEASE" to "NetEase",
        "GFN" to "GeForce NOW",
        "FREE" to "Free to play",
    )

    private val hosts = listOf(
        "steampowered.com" to "Steam",
        "epicgames.com" to "Epic Games Store",
        "ubisoft.com" to "Ubisoft Connect",
        "ea.com" to "EA app",
        "battle.net" to "Battle.net",
        "blizzard.com" to "Battle.net",
        "xbox.com" to "Xbox",
        "microsoft.com" to "Microsoft Store",
        "gog.com" to "GOG",
        "hoyoverse.com" to "HoYoPlay",
        "hoyoplay" to "HoYoPlay",
        "mihoyo.com" to "HoYoPlay",
        "kurogames" to "Kuro Games",
        "wutheringwaves" to "Kuro Games",
        "rockstargames.com" to "Rockstar Games Launcher",
        "wargaming.net" to "Wargaming Game Center",
        "gaijin.net" to "Gaijin",
        "gryphline.com" to "Gryphline",
        "infoldgames.com" to "Infold Games",
        "levelinfinite.com" to "Level Infinite",
        "nexon.com" to "Nexon",
        "riotgames.com" to "Riot Client",
        "playstation.com" to "PlayStation PC",
    )

    fun label(code: String): String {
        val c = code.trim().uppercase()
        labels[c]?.let { return it }
        builtIn[c]?.let { return it }
        if (c.isEmpty() || c == "NONE" || c == "UNKNOWN" || c == "OTHER") return "Publisher launcher"
        return c.lowercase().split('_', ' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }

    fun label(v: Variant, publisher: String? = null): String {
        val c = v.store.trim().uppercase()
        val unnamed = c.isEmpty() || c == "NONE" || c == "UNKNOWN" || c == "OTHER"
        if (!unnamed) return label(c)
        val url = v.storeUrl?.lowercase()
        if (url != null) hosts.firstOrNull { url.contains(it.first) }?.let { return it.second }
        // The game ships with its own launcher; the publisher is the most useful name for it.
        return publisher?.takeIf { it.isNotBlank() }?.let { "$it launcher" } ?: "Own launcher"
    }
}
