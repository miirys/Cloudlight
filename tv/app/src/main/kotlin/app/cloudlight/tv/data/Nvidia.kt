package app.cloudlight.tv.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String, val code: Int = 0) : IOException(message)

/** Talks to NVIDIA's sign-in, catalog and session services. Every call runs off the main thread. */
object Nvidia {
    private const val SERVICE_URLS = "https://pcs.geforcenow.com/v1/serviceUrls"
    private const val DEVICE_AUTHORIZE = "https://login.nvidia.com/device/authorize"
    private const val TOKEN = "https://login.nvidia.com/token"
    private const val GRAPHQL = "https://games.geforce.com/graphql"
    private const val DEFAULT_STREAMING = "https://prod.cloudmatchbeta.nvidiagrid.net/"

    private const val AUTH_CLIENT_ID = "q61ddeJrVt7O90Nl-P-N7I36yctih4Ml6FyXLrb6j-U"
    private const val SCOPES = "openid consent email tk_client age"
    private const val LCARS_CLIENT_ID = "ec7e38d4-03af-4b58-b131-cfb0495903ab"
    private const val CLIENT_VERSION = "2.0.87.131"
    private const val BROWSER_UA = "Mozilla/5.0 (X11; Linux x86_64; Steam Deck) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    private const val GFN_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 NVIDIACEFClient/HEAD/7b92719716 GFN-PC/2.0.87.131"
    private const val BIFROST_UA = "GFN-PC/30.0 (Linux) BifrostClientSDK/4.9 (38495286)"

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json".toMediaType()

    // ---------- Sign-in ----------

    data class Provider(val idpId: String, val name: String, val streamingUrl: String)

    data class DeviceChallenge(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val verificationUriComplete: String,
        val intervalSeconds: Int,
        val expiresAtMs: Long,
    )

    sealed interface Poll {
        data object Pending : Poll
        data object SlowDown : Poll
        data object Expired : Poll
        data object Denied : Poll
        data class Done(val tokens: Tokens) : Poll
    }

    data class Tokens(val access: String, val refresh: String?, val id: String?, val expiresAtMs: Long)

    suspend fun defaultProvider(): Provider = io {
        val body = call(Request.Builder().url(SERVICE_URLS).header("Accept", "application/json").header("User-Agent", GFN_UA).build())
        val info = body.getJSONObject("gfnServiceInfo")
        val endpoints = info.optJSONArray("gfnServiceEndpoints") ?: JSONArray()
        val wanted = info.optString("defaultProvider")
        var fallback: Provider? = null
        for (i in 0 until endpoints.length()) {
            val e = endpoints.getJSONObject(i)
            val p = Provider(
                e.optString("idpId"),
                e.optString("loginProviderDisplayName", "NVIDIA"),
                e.optString("streamingServiceUrl", DEFAULT_STREAMING).let { if (it.endsWith("/")) it else "$it/" },
            )
            if (p.idpId.isEmpty()) continue
            if (fallback == null) fallback = p
            if (e.optString("loginProvider") == wanted || e.optString("loginProviderCode") == wanted) return@io p
        }
        fallback ?: Provider("PDiAhv2kJTFeQ7WOPqiQ2tRZ7lGhR2X11dXvM4TZSxg", "NVIDIA", DEFAULT_STREAMING)
    }

    suspend fun startDeviceLogin(provider: Provider, deviceId: String): DeviceChallenge = io {
        val form = FormBody.Builder()
            .add("client_id", AUTH_CLIENT_ID)
            .add("scope", SCOPES)
            .add("device_id", deviceId)
            .add("display_name", "Android TV")
            .add("idp_id", provider.idpId)
            .build()
        val req = Request.Builder().url(DEVICE_AUTHORIZE).post(form)
            .header("Accept", "application/json, text/plain, */*")
            .header("Origin", "https://play.geforcenow.com")
            .header("Referer", "https://play.geforcenow.com/")
            .header("User-Agent", BROWSER_UA)
            .header("x-device-id", deviceId)
            .header("nv-client-id", AUTH_CLIENT_ID)
            .header("nv-client-streamer", "WEBRTC")
            .header("nv-client-type", "BROWSER")
            .header("nv-client-platform-name", "browser")
            .header("nv-browser-type", "CHROME")
            .header("nv-device-os", "STEAMOS")
            .header("nv-device-type", "CONSOLE")
            .header("nv-device-model", "STEAMDECK")
            .header("nv-device-make", "VALVE")
            .build()
        val j = call(req)
        val life = j.optLong("expires_in", 600).coerceIn(60, 3600)
        DeviceChallenge(
            deviceCode = j.getString("device_code"),
            userCode = j.getString("user_code"),
            verificationUri = j.getString("verification_uri"),
            verificationUriComplete = j.optString("verification_uri_complete", j.getString("verification_uri")),
            intervalSeconds = j.optInt("interval", 5).coerceIn(1, 60),
            expiresAtMs = System.currentTimeMillis() + life * 1000,
        )
    }

    suspend fun pollDeviceLogin(challenge: DeviceChallenge): Poll = io {
        val form = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .add("device_code", challenge.deviceCode)
            .add("client_id", AUTH_CLIENT_ID)
            .build()
        val resp = http.newCall(tokenRequest(form)).execute()
        resp.use {
            val text = it.body?.string().orEmpty()
            val j = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (!it.isSuccessful) {
                return@io when (j.optString("error")) {
                    "authorization_pending" -> Poll.Pending
                    "slow_down" -> Poll.SlowDown
                    "expired_token" -> Poll.Expired
                    "access_denied" -> Poll.Denied
                    else -> throw ApiException("Sign-in failed (${it.code}).", it.code)
                }
            }
            Poll.Done(tokens(j, null))
        }
    }

    suspend fun refresh(refreshToken: String): Tokens = io {
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", AUTH_CLIENT_ID)
            .build()
        tokens(call(tokenRequest(form)), refreshToken)
    }

    private fun tokenRequest(form: FormBody) = Request.Builder().url(TOKEN).post(form)
        .header("Accept", "application/json, text/plain, */*")
        .header("Origin", "https://play.geforcenow.com")
        .header("Referer", "https://play.geforcenow.com/")
        .header("User-Agent", BROWSER_UA)
        .build()

    private fun tokens(j: JSONObject, previousRefresh: String?) = Tokens(
        access = j.getString("access_token"),
        refresh = j.optString("refresh_token").ifEmpty { previousRefresh },
        id = j.optString("id_token").ifEmpty { null },
        expiresAtMs = System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000,
    )

    data class Profile(val userId: String, val name: String, val email: String?, val avatar: String?)

    fun profileFromJwt(token: String): Profile? = runCatching {
        val part = token.split('.')[1]
        val j = JSONObject(String(Base64.decode(part, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)))
        val email = j.optString("email").ifEmpty { null }
        Profile(
            userId = j.getString("sub"),
            name = j.optString("preferred_username").ifEmpty { email?.substringBefore('@') ?: "Player" },
            email = email,
            avatar = j.optString("picture").ifEmpty { null },
        )
    }.getOrNull()

    // ---------- Catalog ----------

    private fun lcars(b: Request.Builder, token: String) = b
        .header("Accept", "application/json, text/plain, */*")
        .header("Authorization", "GFNJWT $token")
        .header("nv-client-id", LCARS_CLIENT_ID)
        .header("nv-client-type", "NATIVE")
        .header("nv-client-version", CLIENT_VERSION)
        .header("nv-client-streamer", "NVIDIA-CLASSIC")
        .header("nv-device-os", "LINUX")
        .header("nv-device-type", "DESKTOP")
        .header("nv-device-make", "GENERIC")
        .header("nv-device-model", "PC")
        .header("x-nv-client-identity", "GFN-PC")
        .header("User-Agent", GFN_UA)

    /** The streaming region ("VPC") the account is served from; the catalog is per region. */
    suspend fun vpcId(streamingBase: String, token: String): String = io {
        val j = call(lcars(Request.Builder().url(streamingBase + "v2/serverInfo"), token).build())
        j.optJSONObject("requestStatus")?.optString("serverId")?.takeIf { it.isNotBlank() }
            ?: throw ApiException("The server did not say which region you are in.")
    }

    suspend fun library(token: String, vpcId: String): List<Game> = apps(
        token, vpcId,
        filters = JSONObject().put("variants", JSONObject().put("gfn", JSONObject().put("library", JSONObject().put("status", JSONObject().put("notEquals", "NOT_OWNED"))))),
        sort = "variants.gfn.library.lastPlayedDate:DESC,computedValues.libraryAddedDate:DESC,sortName:ASC",
        pages = 12,
    )

    /** Every game GeForce NOW streams in this region, A to Z. */
    suspend fun catalog(token: String, vpcId: String): List<Game> = apps(token, vpcId, JSONObject(), "sortName:ASC", pages = 40)

    private suspend fun apps(token: String, vpcId: String, filters: JSONObject, sort: String, pages: Int): List<Game> = io {
        val games = ArrayList<Game>()
        var cursor = ""
        repeat(pages) {
            val vars = JSONObject()
                .put("vpcId", vpcId)
                .put("locale", "en_US")
                .put("sortString", sort)
                .put("fetchCount", 100)
                .put("cursor", cursor)
                .put("filters", filters)
            val body = JSONObject().put("query", LIBRARY_QUERY).put("variables", vars).toString()
            val req = lcars(Request.Builder().url(GRAPHQL).post(body.toRequestBody(JSON)), token)
                .header("Content-Type", "application/json")
                .header("Origin", "https://play.geforcenow.com")
                .header("Referer", "https://play.geforcenow.com/")
                .header("nv-browser-type", "CHROME")
                .build()
            val j = call(req)
            j.optJSONArray("errors")?.let { e ->
                if (e.length() > 0) throw ApiException(e.getJSONObject(0).optString("message", "The library could not be loaded."))
            }
            val apps = j.getJSONObject("data").getJSONObject("apps")
            val items = apps.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) Game.parse(items.getJSONObject(i))?.let(games::add)
            val page = apps.optJSONObject("pageInfo")
            if (page?.optBoolean("hasNextPage") != true) return@io games
            cursor = page.optString("endCursor")
            if (cursor.isEmpty()) return@io games
        }
        games
    }

    // ---------- Sessions ----------

    private fun cloudmatch(b: Request.Builder, token: String, deviceId: String) = b
        .header("User-Agent", BIFROST_UA)
        .header("Authorization", "GFNJWT $token")
        .header("Content-Type", "text/plain")
        .header("nv-client-id", LCARS_CLIENT_ID)
        .header("nv-client-streamer", "NVIDIA-CLASSIC")
        .header("nv-client-type", "NATIVE")
        .header("nv-client-version", CLIENT_VERSION)
        .header("nv-device-os", "LINUX")
        .header("nv-device-type", "DESKTOP")
        .header("nv-device-make", "UNKNOWN")
        .header("nv-device-model", "UNKNOWN")
        .header("x-device-id", deviceId)
        .header("x-nv-client-identity", BIFROST_UA)

    /** Sessions running for this account on any device, so they can be picked up here. */
    suspend fun activeSessions(streamingBase: String, token: String, deviceId: String): List<ActiveSession> = io {
        val j = call(cloudmatch(Request.Builder().url(streamingBase + "v2/session"), token, deviceId).build())
        val arr = j.optJSONArray("sessions") ?: JSONArray()
        (0 until arr.length()).mapNotNull { ActiveSession.parse(arr.getJSONObject(it), streamingBase) }
    }

    // ---------- Plumbing ----------

    private fun call(req: Request): JSONObject {
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 401 || r.code == 403) throw ApiException("Your sign-in has expired.", r.code)
            if (!r.isSuccessful) throw ApiException("The server answered ${r.code}.", r.code)
            return runCatching { JSONObject(text) }.getOrElse { throw ApiException("The server sent an unreadable answer.") }
        }
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private val LIBRARY_QUERY = """query GetLibraryApps(
  ${'$'}vpcId: String!, ${'$'}locale: String!, ${'$'}sortString: String!,
  ${'$'}fetchCount: Int!, ${'$'}cursor: String!, ${'$'}filters: AppFilterFields!
) {
  apps(vpcId: ${'$'}vpcId, language: ${'$'}locale, orderBy: ${'$'}sortString, first: ${'$'}fetchCount, after: ${'$'}cursor, filters: ${'$'}filters) {
    numberReturned pageInfo { hasNextPage endCursor totalCount }
    items {
      id title developerName publisherName genres
      library { favorited }
      images { KEY_ART KEY_IMAGE GAME_BOX_ART TV_BANNER HERO_IMAGE MARQUEE_HERO_IMAGE FEATURE_IMAGE GAME_LOGO SCREENSHOTS }
      variants {
        id appStore
        gfn { status library { status selected lastPlayedDate } }
      }
    }
  }
}"""
}
