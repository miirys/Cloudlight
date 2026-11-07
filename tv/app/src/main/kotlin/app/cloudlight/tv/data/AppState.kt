package app.cloudlight.tv.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/** Everything the screens show, as Compose state, plus the actions that change it. */
class AppState(context: Context, private val scope: CoroutineScope) {

    sealed interface SignIn {
        data object Idle : SignIn
        data object Preparing : SignIn
        data class Waiting(val challenge: Nvidia.DeviceChallenge) : SignIn
        data class Failed(val message: String) : SignIn
    }

    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val deviceId: String = prefs.getString("device", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("device", it).apply()
    }

    var profile by mutableStateOf<Nvidia.Profile?>(null)
        private set
    var signIn by mutableStateOf<SignIn>(SignIn.Idle)
        private set
    var library by mutableStateOf<List<Game>>(emptyList())
        private set
    var sessions by mutableStateOf<List<ActiveSession>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    /** GeForce NOW's featured games, for the rotating banner on Home. */
    var featured by mutableStateOf<List<Game>>(emptyList())
        private set
    /** Storefront rails such as new releases and popular games. */
    var shelves by mutableStateOf<List<Nvidia.Shelf>>(emptyList())
        private set
    /** Games seen in search results, so sessions and links can be matched to them later. */
    private val seen = LinkedHashMap<String, Game>()
    var error by mutableStateOf<String?>(null)
        private set

    val signedIn: Boolean get() = profile != null

    private var tokens: Nvidia.Tokens? = null
    /** The provider's session service, from sign-in. */
    var streamingBase: String = prefs.getString("base", null) ?: "https://prod.cloudmatchbeta.nvidiagrid.net/"
        private set
    private var loginJob: Job? = null

    init {
        val access = prefs.getString("access", null)
        if (access != null) {
            tokens = Nvidia.Tokens(access, prefs.getString("refresh", null), prefs.getString("id", null), prefs.getLong("expires", 0))
            profile = Nvidia.profileFromJwt(prefs.getString("id", null) ?: access)
            refresh()
        }
    }

    fun beginSignIn() {
        loginJob?.cancel()
        loginJob = scope.launch {
            signIn = SignIn.Preparing
            try {
                val provider = Nvidia.defaultProvider()
                streamingBase = provider.streamingUrl
                var challenge = Nvidia.startDeviceLogin(provider, deviceId)
                signIn = SignIn.Waiting(challenge)
                var interval = challenge.intervalSeconds
                while (isActive) {
                    delay(interval * 1000L)
                    if (System.currentTimeMillis() > challenge.expiresAtMs) {
                        // Codes last about ten minutes; quietly fetch a fresh one.
                        challenge = Nvidia.startDeviceLogin(provider, deviceId)
                        signIn = SignIn.Waiting(challenge)
                        continue
                    }
                    when (val r = Nvidia.pollDeviceLogin(challenge)) {
                        Nvidia.Poll.Pending -> Unit
                        Nvidia.Poll.SlowDown -> interval += 5
                        Nvidia.Poll.Expired -> {
                            challenge = Nvidia.startDeviceLogin(provider, deviceId)
                            signIn = SignIn.Waiting(challenge)
                        }
                        Nvidia.Poll.Denied -> {
                            signIn = SignIn.Failed("Sign-in was declined on the other device.")
                            return@launch
                        }
                        is Nvidia.Poll.Done -> {
                            store(r.tokens)
                            signIn = SignIn.Idle
                            refresh()
                            return@launch
                        }
                    }
                }
            } catch (t: Throwable) {
                if (isActive) signIn = SignIn.Failed(t.message ?: "Sign-in failed. Check the network connection.")
            }
        }
    }

    fun cancelSignIn() {
        loginJob?.cancel()
        signIn = SignIn.Idle
    }

    fun signOut() {
        loginJob?.cancel()
        prefs.edit().remove("access").remove("refresh").remove("id").remove("expires").apply()
        tokens = null
        profile = null
        library = emptyList()
        featured = emptyList()
        shelves = emptyList()
        sessions = emptyList()
    }

    /** Reloads the library and the running sessions. */
    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                val token = serviceToken()
                val vpc = Nvidia.vpcId(streamingBase, token).also { this@AppState.vpc = it }
                val sessionsJob = scope.launch {
                    sessions = runCatching { Nvidia.activeSessions(streamingBase, token, deviceId) }.getOrDefault(emptyList())
                }
                val storesJob = scope.launch { runCatching { Stores.update(Nvidia.storeLabels(token)) } }
                val storeJob = scope.launch {
                    runCatching {
                        val p = Nvidia.panels(token, vpc, "MARQUEE", "MAIN")
                        featured = p["MARQUEE"].orEmpty().flatMap { it.games }.distinctBy { it.id }.take(8)
                        shelves = p["MAIN"].orEmpty().filter { it.title.isNotBlank() }.take(8)
                    }
                }
                storesJob.join()
                library = Nvidia.library(token, vpc)
                sessionsJob.join()
                storeJob.join()
            } catch (e: ApiException) {
                if (e.code == 401 || e.code == 403) {
                    signOut()
                    error = "Your sign-in has expired. Sign in again to continue."
                } else {
                    error = e.message
                }
            } catch (t: Throwable) {
                error = "GeForce NOW can't be reached. Check the network connection."
            } finally {
                loading = false
            }
        }
    }

    /** Checks for sessions started elsewhere, without reloading the library. */
    fun refreshSessions() {
        scope.launch {
            runCatching { sessions = Nvidia.activeSessions(streamingBase, serviceToken(), deviceId) }
        }
    }

    private var vpc: String? = null

    /** Searches everything GeForce NOW streams in this region. Owned copies win over catalog ones. */
    suspend fun search(query: String): List<Game> {
        val token = serviceToken()
        val v = vpc ?: Nvidia.vpcId(streamingBase, token).also { vpc = it }
        val found = Nvidia.search(token, v, query)
        val owned = library.associateBy { it.id }
        synchronized(seen) { for (g in found) seen[g.id] = g }
        return found.map { owned[it.id] ?: it }
    }

    fun gameFor(session: ActiveSession): Game? {
        fun match(g: Game) = g.variants.any { it.id == session.appId } || g.id == session.appId
        return library.firstOrNull(::match)
            ?: featured.firstOrNull(::match)
            ?: shelves.firstNotNullOfOrNull { s -> s.games.firstOrNull(::match) }
            ?: synchronized(seen) { seen.values.firstOrNull(::match) }
    }

    /** Region the account streams from, for the About page. */
    val region: String? get() = vpc

    /** A valid token for GeForce NOW's services, refreshed when it is close to expiring. */
    suspend fun serviceToken(): String {
        var t = tokens ?: throw ApiException("Not signed in.", 401)
        if (System.currentTimeMillis() > t.expiresAtMs - 10 * 60_000L) {
            val r = t.refresh ?: throw ApiException("Your sign-in has expired.", 401)
            t = try {
                Nvidia.refresh(r)
            } catch (e: ApiException) {
                // A refused refresh token means the sign-in is over, not a network blip.
                throw if (e.code in 400..403) ApiException("Your sign-in has expired.", 401) else e
            }
            store(t)
        }
        return t.id ?: t.access
    }

    private fun store(t: Nvidia.Tokens) {
        tokens = t
        profile = Nvidia.profileFromJwt(t.id ?: t.access)
        prefs.edit()
            .putString("access", t.access)
            .putString("refresh", t.refresh)
            .putString("id", t.id)
            .putLong("expires", t.expiresAtMs)
            .putString("base", streamingBase)
            .apply()
    }
}
