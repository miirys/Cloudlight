package app.cloudlight.tv.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
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

    data class Profile(val userId: String, val name: String, val email: String?, val avatar: String?, val tier: String? = null)

    fun profileFromJwt(token: String): Profile? = runCatching {
        val part = token.split('.')[1]
        val j = JSONObject(String(Base64.decode(part, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)))
        val email = j.optString("email").ifEmpty { null }
        Profile(
            userId = j.getString("sub"),
            name = j.optString("preferred_username").ifEmpty { email?.substringBefore('@') ?: "Player" },
            email = email,
            avatar = j.optString("picture").ifEmpty { null },
            tier = j.optString("gfn_tier").ifEmpty { null }?.let(::tierName),
        )
    }.getOrNull()

    /** Membership names as NVIDIA markets them. */
    fun tierName(raw: String) = when (raw.uppercase()) {
        "FREE" -> "Free"
        "PRIORITY", "PERFORMANCE" -> "Performance"
        "ULTIMATE" -> "Ultimate"
        "FOUNDERS" -> "Founders"
        else -> raw.lowercase().replaceFirstChar { it.uppercase() }
    }

    // ---------- Membership ----------

    /** One resolution and frame rate the membership may stream. */
    data class Mode(val width: Int, val height: Int, val fps: Int)

    /** What the account's membership allows: its tier and the stream modes it includes. */
    data class Membership(val tier: String?, val modes: List<Mode>, val hoursLeft: Double?, val unlimited: Boolean)

    suspend fun membership(token: String, userId: String, vpcId: String?): Membership = io {
        val url = "https://mes.geforcenow.com/v4/subscriptions".toHttpUrl().newBuilder()
            .addQueryParameter("serviceName", "gfn_pc")
            .addQueryParameter("languageCode", "en_US")
            .addQueryParameter("vpcId", vpcId ?: "NP-AMS-08")
            .addQueryParameter("userId", userId)
            .build()
        val req = Request.Builder().url(url)
            .header("Authorization", "GFNJWT $token")
            .header("Accept", "application/json")
            .header("nv-client-id", LCARS_CLIENT_ID)
            .header("nv-client-type", "NATIVE")
            .header("nv-client-version", CLIENT_VERSION)
            .header("nv-client-streamer", "NVIDIA-CLASSIC")
            .header("nv-device-os", "WINDOWS")
            .header("nv-device-type", "DESKTOP")
            .build()
        val j = call(req)
        val all = ArrayList<Pair<Mode, Boolean>>()
        val list = j.optJSONObject("features")?.optJSONArray("resolutions")
        if (list != null) for (i in 0 until list.length()) {
            val r = list.optJSONObject(i) ?: continue
            val m = Mode(r.optInt("widthInPixels"), r.optInt("heightInPixels"), r.optInt("framesPerSecond"))
            if (m.width > 0 && m.height > 0 && m.fps > 0) all += m to r.optBoolean("isEntitled", true)
        }
        // Some accounts list every mode without flags; then all of them are allowed.
        val entitled = all.filter { it.second }.map { it.first }.ifEmpty { all.map { it.first } }
        val remaining = j.opt("remainingTimeInMinutes")?.toString()?.toDoubleOrNull()
        Membership(
            tier = j.optString("membershipTier").ifEmpty { null }?.let(::tierName),
            modes = entitled.distinct().sortedWith(compareByDescending<Mode> { it.height }.thenByDescending { it.fps }),
            hoursLeft = remaining?.div(60),
            unlimited = j.optString("subType") == "UNLIMITED",
        )
    }

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
    suspend fun catalog(token: String, vpcId: String): List<Game> = apps(token, vpcId, JSONObject(), "sortName:ASC", pages = 40, count = 50)

    private fun graphql(token: String, query: String, vars: JSONObject): JSONObject {
        val body = JSONObject().put("query", query).put("variables", vars).toString()
        val req = lcars(Request.Builder().url(GRAPHQL).post(body.toRequestBody(JSON)), token)
            .header("Content-Type", "application/json")
            .header("Origin", "https://play.geforcenow.com")
            .header("Referer", "https://play.geforcenow.com/")
            .header("nv-browser-type", "CHROME")
            .build()
        val j = call(req)
        j.optJSONArray("errors")?.let { e ->
            if (e.length() > 0 && j.optJSONObject("data") == null) {
                throw ApiException(e.getJSONObject(0).optString("message", "GeForce NOW sent an error."))
            }
        }
        return j.optJSONObject("data") ?: throw ApiException("GeForce NOW sent an empty answer.")
    }

    /**
     * Whether the catalog accepts the description field. It isn't part of every catalog
     * version, so the first query finds out and later ones only ask when it works.
     */
    @Volatile private var describe: Boolean? = null

    private fun appsQuery(token: String, template: String, vars: JSONObject): JSONObject {
        if (describe != false) {
            try {
                return graphql(token, template.replace("__EXTRA__", "shortDescription"), vars).also { describe = true }
            } catch (e: ApiException) {
                if (describe == true || (e.code != 0 && e.code != 400)) throw e
                android.util.Log.i("Nvidia", "catalog has no descriptions: ${e.message}")
                describe = false
            }
        }
        return graphql(token, template.replace("__EXTRA__", ""), vars)
    }

    /** Store codes to the names GeForce NOW shows for them. */
    suspend fun storeLabels(token: String): Map<String, String> = io {
        val arr = graphql(token, STORES_QUERY, JSONObject().put("locale", "en_US")).optJSONArray("appStoreDefinitions") ?: JSONArray()
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .mapNotNull { o -> o.optString("store").takeIf { it.isNotBlank() }?.let { it to o.optString("label") } }
            .filter { it.second.isNotBlank() && it.second != "null" }
            .toMap()
    }

    /** GeForce NOW's own search over everything it streams in this region. */
    suspend fun search(token: String, vpcId: String, query: String, count: Int = 60): List<Game> = io {
        val vars = JSONObject()
            .put("vpcId", vpcId)
            .put("locale", "en_US")
            .put("sortString", "itemMetadata.relevance:DESC,sortName:ASC")
            .put("fetchCount", count)
            .put("cursor", "")
            .put("searchString", query)
            .put("filters", JSONObject())
        val items = appsQuery(token, SEARCH_QUERY, vars).optJSONObject("apps")?.optJSONArray("items") ?: JSONArray()
        (0 until items.length()).mapNotNull { items.optJSONObject(it)?.let(Game::parse) }
    }

    /** A named rail of games from GeForce NOW's storefront. */
    data class Shelf(val id: String, val title: String, val games: List<Game>)

    /**
     * Storefront panels: MARQUEE is the rotating featured banner, MAIN holds the
     * recommendation rails (new, popular, picked for you and so on).
     */
    suspend fun panels(token: String, vpcId: String, vararg names: String): Map<String, List<Shelf>> = io {
        val vars = JSONObject().put("vpcId", vpcId).put("locale", "en_US").put("panelNames", JSONArray(names.toList()))
        val panels = appsQuery(token, PANELS_QUERY, vars).optJSONArray("panels") ?: JSONArray()
        val out = LinkedHashMap<String, List<Shelf>>()
        for (p in 0 until panels.length()) {
            val panel = panels.optJSONObject(p) ?: continue
            val sections = panel.optJSONArray("sections") ?: JSONArray()
            out[panel.optString("name")] = (0 until sections.length()).mapNotNull { si ->
                val sec = sections.optJSONObject(si) ?: return@mapNotNull null
                val items = sec.optJSONArray("items") ?: JSONArray()
                val games = (0 until items.length()).mapNotNull { items.optJSONObject(it)?.optJSONObject("app")?.let(Game::parse) }
                    .distinctBy { it.id }
                if (games.isEmpty()) null else Shelf(sec.optString("id"), sec.optString("title").takeIf { it != "null" }.orEmpty(), games)
            }
        }
        out
    }

    private suspend fun apps(token: String, vpcId: String, filters: JSONObject, sort: String, pages: Int, count: Int = 100): List<Game> = io {
        val games = ArrayList<Game>()
        var cursor = ""
        repeat(pages) {
            val vars = JSONObject()
                .put("vpcId", vpcId)
                .put("locale", "en_US")
                .put("sortString", sort)
                .put("fetchCount", count)
                .put("cursor", cursor)
                .put("filters", filters)
            val apps = appsQuery(token, LIBRARY_QUERY, vars).getJSONObject("apps")
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

    private const val APP_FIELDS = """
      id title developerName publisherName genres __EXTRA__
      library { favorited }
      images { KEY_ART KEY_IMAGE GAME_BOX_ART TV_BANNER HERO_IMAGE MARQUEE_HERO_IMAGE FEATURE_IMAGE GAME_LOGO SCREENSHOTS }
      variants {
        id appStore storeUrl
        gfn { status library { status selected lastPlayedDate } }
      }"""

    private val LIBRARY_QUERY = """query GetLibraryApps(
  ${'$'}vpcId: String!, ${'$'}locale: String!, ${'$'}sortString: String!,
  ${'$'}fetchCount: Int!, ${'$'}cursor: String!, ${'$'}filters: AppFilterFields!
) {
  apps(vpcId: ${'$'}vpcId, language: ${'$'}locale, orderBy: ${'$'}sortString, first: ${'$'}fetchCount, after: ${'$'}cursor, filters: ${'$'}filters) {
    numberReturned pageInfo { hasNextPage endCursor totalCount }
    items { $APP_FIELDS }
  }
}"""

    private val SEARCH_QUERY = """query SearchApps(
  ${'$'}vpcId: String!, ${'$'}locale: String!, ${'$'}sortString: String!,
  ${'$'}fetchCount: Int!, ${'$'}cursor: String!, ${'$'}searchString: String!, ${'$'}filters: AppFilterFields!
) {
  apps(vpcId: ${'$'}vpcId, language: ${'$'}locale, orderBy: ${'$'}sortString, first: ${'$'}fetchCount, after: ${'$'}cursor, searchQuery: ${'$'}searchString, filters: ${'$'}filters) {
    numberReturned pageInfo { hasNextPage endCursor totalCount }
    items { $APP_FIELDS }
  }
}"""

    private val PANELS_QUERY = """query GetPanels(${'$'}vpcId: String!, ${'$'}locale: String!, ${'$'}panelNames: [String]!) {
  panels(vpcId: ${'$'}vpcId, language: ${'$'}locale, names: ${'$'}panelNames) {
    name
    sections {
      id title
      items { __typename ... on GameItem { app { $APP_FIELDS } } }
    }
  }
}"""

    private val STORES_QUERY = """query GetStores(${'$'}locale: String!) {
  appStoreDefinitions(language: ${'$'}locale) { store label }
}"""
}
