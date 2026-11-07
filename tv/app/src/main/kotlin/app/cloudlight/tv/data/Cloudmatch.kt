package app.cloudlight.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.TimeZone
import java.util.UUID

/** Where to reach the streaming server once a seat is ready. */
data class Endpoint(val ip: String, val port: Int)

data class IceSpec(val urls: List<String>, val username: String?, val credential: String?)

/** One session as the session service describes it. */
data class CloudSession(
    val id: String,
    /** 1 setting up or queued, 2 ready, 3 streaming. */
    val status: Int,
    val queuePosition: Int?,
    val setupStep: Int?,
    /** Base URL for polling and ending this session. */
    val controlBase: String,
    val serverIp: String?,
    val signalingUrl: String?,
    val media: Endpoint?,
    val ice: List<IceSpec>,
    val gpu: String?,
    val width: Int?,
    val height: Int?,
    val fps: Int?,
    val adsRequired: Boolean,
) {
    val ready: Boolean get() = status == 2 || status == 3
}

class SessionException(message: String, val statusCode: Int = 0, val http: Int = 0) : Exception(message)

/** Asks for the stream the user picked; [kbps] caps the video bitrate. */
data class LaunchRequest(
    val appId: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitDepth: Int,
    val lowLatencyMode: Boolean,
    val language: String = "en_US",
    val keyboard: String = "en-US",
)

/**
 * The GeForce NOW session service: start a game on a cloud rig, follow it through the queue,
 * pick up one started elsewhere, and end it.
 */
class Cloudmatch(private val token: suspend () -> String) {

    // One identity per session, as the official clients do.
    private val clientId = UUID.randomUUID().toString()
    private val deviceId = UUID.randomUUID().toString()

    private fun headers(b: Request.Builder, t: String, origin: Boolean): Request.Builder {
        b.header("User-Agent", UA)
            .header("Authorization", "GFNJWT $t")
            .header("Content-Type", "application/json")
            .header("nv-browser-type", "CHROME")
            .header("nv-client-id", clientId)
            .header("nv-client-streamer", "NVIDIA-CLASSIC")
            .header("nv-client-type", "NATIVE")
            .header("nv-client-version", VERSION)
            .header("nv-device-make", "UNKNOWN")
            .header("nv-device-model", "UNKNOWN")
            .header("nv-device-os", "WINDOWS")
            .header("nv-device-type", "DESKTOP")
            .header("x-device-id", deviceId)
        if (origin) {
            b.header("Origin", "https://play.geforcenow.com").header("Referer", "https://play.geforcenow.com/")
        }
        return b
    }

    suspend fun create(streamingBase: String, r: LaunchRequest): CloudSession = io {
        if (r.appId.toLongOrNull() == null) throw SessionException("This game can't be launched from here yet.")
        val base = streamingBase.trimEnd('/')
        val url = "$base/v2/session?keyboardLayout=${r.keyboard}&languageCode=${r.language}"
        val req = headers(Request.Builder().url(url), token(), origin = true)
            .post(createBody(r).toString().toRequestBody(JSON))
            .build()
        parse(send(req), base)
    }

    /** Follows a session. Once a seat is ready we talk to that rig directly, not the load balancer. */
    suspend fun poll(s: CloudSession): CloudSession = io {
        val t = token()
        val next = parse(send(headers(Request.Builder().url("${s.controlBase}/v2/session/${s.id}"), t, false).build()), s.controlBase)
        val direct = next.serverIp
        if (next.ready && isZoneHost(hostOf(s.controlBase)) && direct != null && !isZoneHost(direct)) {
            val base = "https://$direct"
            runCatching {
                parse(send(headers(Request.Builder().url("$base/v2/session/${s.id}"), t, false).build()), base)
            }.getOrDefault(next.copy(controlBase = base))
        } else {
            next
        }
    }

    /** Takes over a session running on another device (or left running here) and waits for its seat. */
    suspend fun claim(active: ActiveSession, r: LaunchRequest): CloudSession = io {
        val t = token()
        var host = active.serverHost ?: hostOf(active.streamingBase)
        // The listing names the region's load balancer; ask it for the rig itself first.
        if (isZoneHost(host)) {
            runCatching {
                val pre = parse(send(headers(Request.Builder().url("https://$host/v2/session/${active.sessionId}"), t, false).build()), "https://$host")
                pre.serverIp?.let { host = it }
            }
        }
        val base = "https://$host"
        val current = runCatching {
            parse(send(headers(Request.Builder().url("$base/v2/session/${active.sessionId}"), t, false).build()), base)
        }.getOrNull()
        // A session still being set up can't be resumed, only followed.
        if (current?.status != 1) {
            val url = "$base/v2/session/${active.sessionId}?keyboardLayout=${r.keyboard}&languageCode=${r.language}"
            val req = headers(Request.Builder().url(url), t, origin = true)
                .put(claimBody(active.appId).toString().toRequestBody(JSON))
                .build()
            parse(send(req), base)
        }
        repeat(90) { attempt ->
            if (attempt > 0) delay(1000)
            val s = runCatching {
                parse(send(headers(Request.Builder().url("$base/v2/session/${active.sessionId}"), token(), false).build()), base)
            }.getOrNull()
            if (s != null && s.ready) return@io s
        }
        throw SessionException("The session didn't come back. It may have ended on the other device.")
    }

    suspend fun stop(s: CloudSession) = io {
        runCatching { send(headers(Request.Builder().url("${s.controlBase}/v2/session/${s.id}"), token(), false).delete().build()) }
        Unit
    }

    suspend fun stop(active: ActiveSession) = io {
        val host = active.serverHost ?: hostOf(active.streamingBase)
        runCatching { send(headers(Request.Builder().url("https://$host/v2/session/${active.sessionId}"), token(), false).delete().build()) }
        Unit
    }

    // ---------- Bodies ----------

    private fun meta(vararg kv: Pair<String, String>) = JSONArray().apply {
        for ((k, v) in kv) put(JSONObject().put("key", k).put("value", v))
    }

    private fun timezoneMs() = TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()

    private fun createBody(r: LaunchRequest): JSONObject {
        val monitor = JSONObject()
            .put("widthInPixels", r.width)
            .put("heightInPixels", r.height)
            .put("framesPerSecond", r.fps)
            .put("sdrHdrMode", 0)
            .put(
                "displayData",
                JSONObject().put("desiredContentMaxLuminance", 0).put("desiredContentMinLuminance", 0).put("desiredContentMaxFrameAverageLuminance", 0),
            )
            .put("dpi", 100)
        val features = JSONObject()
            .put("reflex", r.lowLatencyMode && r.fps >= 120)
            .put("bitDepth", if (r.bitDepth >= 10) 10 else 0)
            .put("cloudGsync", false)
            .put("enabledL4S", false)
            .put("mouseMovementFlags", 0)
            .put("trueHdr", false)
            .put("supportedHidDevices", 0)
            .put("profile", 0)
            .put("fallbackToLogicalResolution", false)
            .put("hidDevices", JSONObject.NULL)
            .put("chromaFormat", 0)
            .put("prefilterMode", 0)
            .put("prefilterSharpness", 0)
            .put("prefilterNoiseReduction", 0)
            .put("hudStreamingMode", 0)
            .put("sdrColorSpace", 2)
            .put("hdrColorSpace", 0)
        val data = JSONObject()
            .put("appId", r.appId.toLong())
            .put("internalTitle", JSONObject.NULL)
            .put("availableSupportedControllers", JSONArray())
            .put("networkTestSessionId", JSONObject.NULL)
            .put("parentSessionId", JSONObject.NULL)
            .put("clientIdentification", "GFN-PC")
            .put("deviceHashId", UUID.randomUUID().toString())
            .put("clientVersion", "30.0")
            .put("sdkVersion", "1.0")
            .put("streamerVersion", 1)
            .put("clientPlatformName", "windows")
            .put("clientRequestMonitorSettings", JSONArray().put(monitor))
            .put("useOps", true)
            .put("audioMode", 2)
            .put(
                "metaData",
                meta(
                    "SubSessionId" to UUID.randomUUID().toString(),
                    "wssignaling" to "1",
                    "GSStreamerType" to "WebRTC",
                    "networkType" to "Unknown",
                    "ClientImeSupport" to "0",
                    "clientPhysicalResolution" to JSONObject().put("horizontalPixels", r.width).put("verticalPixels", r.height).toString(),
                    "surroundAudioInfo" to "2",
                ),
            )
            .put("sdrHdrMode", 0)
            .put("clientDisplayHdrCapabilities", JSONObject.NULL)
            .put("surroundAudioInfo", 0)
            .put("remoteControllersBitmap", 0)
            .put("clientTimezoneOffset", timezoneMs())
            .put("enhancedStreamMode", 1)
            .put("appLaunchMode", 1)
            .put("secureRTSPSupported", false)
            .put("partnerCustomData", "")
            .put("accountLinked", true)
            .put("enablePersistingInGameSettings", true)
            .put("userAge", 26)
            .put("requestedStreamingFeatures", features)
        return JSONObject().put("sessionRequestData", data)
    }

    /** A resume must not renegotiate the stream; the server keeps what the session started with. */
    private fun claimBody(appId: String): JSONObject {
        val features = JSONObject()
            .put("reflex", false).put("bitDepth", 0).put("cloudGsync", false).put("enabledL4S", false)
            .put("profile", 0).put("fallbackToLogicalResolution", false).put("chromaFormat", 0)
            .put("prefilterMode", 0).put("hudStreamingMode", 0)
        val data = JSONObject()
            .put("audioMode", 2)
            .put("remoteControllersBitmap", 0)
            .put("sdrHdrMode", 0)
            .put("networkTestSessionId", JSONObject.NULL)
            .put("availableSupportedControllers", JSONArray())
            .put("clientVersion", "30.0")
            .put("deviceHashId", UUID.randomUUID().toString())
            .put("internalTitle", JSONObject.NULL)
            .put("clientPlatformName", "windows")
            .put(
                "metaData",
                meta(
                    "SubSessionId" to UUID.randomUUID().toString(),
                    "wssignaling" to "1",
                    "GSStreamerType" to "WebRTC",
                    "networkType" to "Unknown",
                    "ClientImeSupport" to "0",
                ),
            )
            .put("surroundAudioInfo", 0)
            .put("clientTimezoneOffset", timezoneMs())
            .put("clientIdentification", "GFN-PC")
            .put("parentSessionId", JSONObject.NULL)
            .put("appId", appId.toLongOrNull() ?: 0L)
            .put("streamerVersion", 1)
            .put("appLaunchMode", 1)
            .put("sdkVersion", "1.0")
            .put("enhancedStreamMode", 1)
            .put("useOps", true)
            .put("clientDisplayHdrCapabilities", JSONObject.NULL)
            .put("accountLinked", true)
            .put("partnerCustomData", "")
            .put("enablePersistingInGameSettings", true)
            .put("secureRTSPSupported", false)
            .put("userAge", 26)
            .put("requestedStreamingFeatures", features)
        return JSONObject().put("action", 2).put("data", "RESUME").put("sessionRequestData", data).put("metaData", JSONArray())
    }

    // ---------- Parsing ----------

    private fun send(req: Request): JSONObject {
        Nvidia.http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            val j = runCatching { JSONObject(text) }.getOrNull()
            val status = j?.optJSONObject("requestStatus")
            val code = status?.optInt("statusCode", 0) ?: 0
            if (!r.isSuccessful || (status != null && code != 1)) {
                throw SessionException(message(code, status?.optString("statusDescription"), r.code), code, r.code)
            }
            return j ?: throw SessionException("The session service sent an unreadable answer.", 0, r.code)
        }
    }

    private fun parse(j: JSONObject, base: String): CloudSession {
        val s = j.optJSONObject("session") ?: throw SessionException("The session service didn't describe the session.")
        val conns = s.optJSONArray("connectionInfo") ?: JSONArray()
        val list = (0 until conns.length()).mapNotNull { conns.optJSONObject(it) }
        val signal = list.firstOrNull { it.optInt("usage") == 14 }
        val serverIp = signal?.let { ipOf(it) ?: it.optString("resourcePath").takeIf(String::isNotBlank)?.let(::hostOfUrl) }
            ?: firstString(s.optJSONObject("sessionControlInfo")?.opt("ip"))
        val monitor = s.optJSONArray("monitorSettings")?.optJSONObject(0)
            ?: s.optJSONObject("sessionRequestData")?.optJSONArray("clientRequestMonitorSettings")?.optJSONObject(0)
        return CloudSession(
            id = s.optString("sessionId"),
            status = s.optInt("status", 0),
            queuePosition = queue(s),
            setupStep = s.optJSONObject("seatSetupInfo")?.optInt("seatSetupStep", -1)?.takeIf { it >= 0 },
            controlBase = base,
            serverIp = serverIp,
            signalingUrl = serverIp?.let { signalingUrl(signal?.optString("resourcePath").orEmpty(), it) },
            media = media(list, serverIp),
            ice = ice(s),
            gpu = s.optString("gpuType").takeIf { it.isNotBlank() && it != "null" },
            width = monitor?.optInt("widthInPixels")?.takeIf { it > 0 },
            height = monitor?.optInt("heightInPixels")?.takeIf { it > 0 },
            fps = monitor?.optInt("framesPerSecond")?.takeIf { it > 0 },
            adsRequired = s.optBoolean("sessionAdsRequired", false) || s.optBoolean("isAdsRequired", false),
        )
    }

    private fun queue(s: JSONObject): Int? {
        fun pos(o: JSONObject?) = o?.optInt("queuePosition", 0)?.takeIf { it > 0 }
        return pos(s) ?: pos(s.optJSONObject("seatSetupInfo")) ?: pos(s.optJSONObject("sessionProgress")) ?: pos(s.optJSONObject("progressInfo"))
    }

    private fun firstString(v: Any?): String? = when (v) {
        is String -> v.takeIf { it.isNotBlank() }
        is JSONArray -> v.optString(0).takeIf { it.isNotBlank() }
        else -> null
    }

    private fun ipOf(c: JSONObject) = firstString(c.opt("ip"))

    private fun portOf(c: JSONObject): Int {
        val p = c.optInt("port", 0)
        if (p > 0) return p
        val path = c.optString("resourcePath")
        return runCatching { URI(path.replace("rtsps://", "https://").replace("rtsp://", "http://")).port }.getOrDefault(-1).coerceAtLeast(0)
    }

    private fun media(list: List<JSONObject>, serverIp: String?): Endpoint? {
        fun from(c: JSONObject, fallback: String?): Endpoint? {
            val ip = ipOf(c) ?: c.optString("resourcePath").takeIf(String::isNotBlank)?.let(::hostOfUrl) ?: fallback ?: return null
            val port = portOf(c)
            return if (port > 0) Endpoint(ip, port) else null
        }
        list.firstOrNull { it.optInt("usage") == 2 }?.let { c -> from(c, null)?.let { return it } }
        list.firstOrNull { it.optInt("usage") == 17 }?.let { c -> from(c, null)?.let { return it } }
        for (c in list.filter { it.optInt("usage") == 14 }.sortedByDescending { it.optInt("port") }) {
            from(c, serverIp)?.let { return it }
        }
        return null
    }

    private fun ice(s: JSONObject): List<IceSpec> {
        val arr = s.optJSONObject("iceServerConfiguration")?.optJSONArray("iceServers") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val urls = when (val u = o.opt("urls")) {
                is String -> listOf(u)
                is JSONArray -> (0 until u.length()).map { u.optString(it) }
                else -> emptyList()
            }.filter { it.isNotBlank() }
            if (urls.isEmpty()) null else IceSpec(urls, o.optString("username").ifBlank { null }, o.optString("credential").ifBlank { null })
        }
    }

    private fun signalingUrl(resourcePath: String, ip: String): String = when {
        resourcePath.startsWith("rtsps://") || resourcePath.startsWith("rtsp://") ->
            hostOfUrl(resourcePath)?.let { "wss://$it/nvst/" } ?: "wss://$ip:443/nvst/"
        resourcePath.startsWith("wss://") -> resourcePath
        resourcePath.startsWith("/") -> "wss://$ip:443$resourcePath"
        else -> "wss://$ip:443/nvst/"
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        private const val VERSION = "2.0.80.173"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 NVIDIACEFClient/HEAD/debb5919f6 GFN-PC/2.0.80.173"
        private val JSON = "application/json".toMediaType()

        fun isZoneHost(host: String) = host.contains("cloudmatchbeta.nvidiagrid.net") || host.contains("cloudmatch.nvidiagrid.net")

        fun hostOf(url: String): String = url.removePrefix("https://").removePrefix("http://").substringBefore('/')

        /** Host of rtsps://, rtsp://, wss:// or https:// URLs, without the port. */
        fun hostOfUrl(url: String): String? {
            val rest = listOf("rtsps://", "rtsp://", "wss://", "https://").firstOrNull { url.startsWith(it) }?.let { url.removePrefix(it) } ?: return null
            val host = rest.substringBefore('/').substringBefore(':')
            return host.takeIf { it.isNotBlank() }
        }

        /** Plain-language reasons for the session service's status codes. */
        fun message(code: Int, description: String?, http: Int): String = when (code) {
            2 -> "GeForce NOW refused the request."
            7, 11, 50 -> "Another game is already running on your account. Resume it or end it first."
            10 -> "Too many requests in a row. Wait a moment and try again."
            13 -> "You've used all the playtime your membership includes for now."
            14, 15, 16, 17, 37, 38 -> "Your sign-in has expired. Sign in again to continue."
            18, 68 -> "Your membership doesn't include this game or setting."
            19, 20, 42 -> "This game isn't available to stream right now."
            23 -> "Accept GeForce NOW's terms on another device first, then try again."
            24, 56 -> "GeForce NOW is under maintenance. Try again a little later."
            25, 48 -> "GeForce NOW isn't available right now. Try again a little later."
            26, 27, 28 -> "Steam needs you to sign in again. Launch the game once on another device to fix it."
            29 -> "Your Steam profile is private, so your games can't be checked."
            30, 31, 32, 33, 45, 52, 53, 63 -> "GeForce NOW isn't available in your region."
            34 -> "This session can't be resumed right now."
            35 -> "Verify your email address with NVIDIA first."
            41 -> "This game is being updated. Try again in a few minutes."
            46 -> "The session was taken over by another device."
            49 -> "The session expired."
            51, 58, 59, 60 -> "All rigs nearby are busy. Try again in a moment."
            54, 55 -> "Your region is temporarily paused for new sessions."
            62 -> "The queue is full right now. Try again a little later."
            69 -> "You left the queue."
            else -> when {
                http == 401 || http == 403 -> "Your sign-in has expired. Sign in again to continue."
                http >= 500 -> "GeForce NOW had a problem starting the session. Try again."
                !description.isNullOrBlank() && description != "null" ->
                    "GeForce NOW couldn't start the session (${description.lowercase().replace('_', ' ')})."
                else -> "GeForce NOW couldn't start the session."
            }
        }
    }
}
