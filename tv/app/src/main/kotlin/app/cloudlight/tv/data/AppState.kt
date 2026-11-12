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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** The membership's tier and the stream modes it includes, once known. */
    var membership by mutableStateOf<Nvidia.Membership?>(null)
        private set
    /** Server locations, nearest first. */
    var regions by mutableStateOf<List<Nvidia.Region>>(emptyList())
        private set
    /** Why the app last closed unexpectedly, shown once at startup. */
    var problem by mutableStateOf<app.cloudlight.tv.CrashLog.Problem?>(null)

    val signedIn: Boolean get() = profile != null

    private var tokens: Nvidia.Tokens? = null
    /** The provider's session service, from sign-in. */
    var streamingBase: String = prefs.getString("base", null) ?: "https://prod.cloudmatchbeta.nvidiagrid.net/"
        private set
    private var loginJob: Job? = null

    init {
        val access = prefs.getString("access", null)
        if (access != null) {
            tokens = Nvidia.Tokens(
                access,
                prefs.getString("refresh", null),
                prefs.getString("id", null),
                prefs.getLong("expires", 0),
                prefs.getString("client", null),
                prefs.getLong("clientExpires", 0),
            )
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
                            // The long-lived token that keeps this sign-in alive across restarts.
                            // Without it the sign-in ends when the first tokens run out.
                            runCatching { store(Nvidia.withClientToken(r.tokens)) }
                                .onFailure { android.util.Log.w("AppState", "no client token at sign-in", it) }
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
        prefs.edit().remove("access").remove("refresh").remove("id").remove("expires")
            .remove("client").remove("clientExpires").apply()
        tokens = null
        profile = null
        library = emptyList()
        featured = emptyList()
        shelves = emptyList()
        sessions = emptyList()
        membership = null
    }

    /** Reloads the library and the running sessions. */
    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                try {
                    load(serviceToken())
                } catch (e: ApiException) {
                    if (e.code != 401 && e.code != 403) throw e
                    // A service turned the token down before its time. Renew it and try once
                    // more; only a refused renewal means the sign-in is really over.
                    load(serviceToken(force = true))
                }
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

    private suspend fun load(token: String) {
        val vpc = Nvidia.vpcId(streamingBase, token).also { this@AppState.vpc = it }
        val sessionsJob = scope.launch {
            sessions = runCatching { Nvidia.activeSessions(streamingBase, token, deviceId) }.getOrDefault(emptyList())
        }
        val storesJob = scope.launch { runCatching { Stores.update(Nvidia.storeLabels(token)) } }
        scope.launch { loadMembership(token, vpc) }
        scope.launch {
            runCatching { Nvidia.regions(streamingBase, token) }
                .onSuccess { regions = it }
                .onFailure { android.util.Log.w("AppState", "region list failed", it) }
        }
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
    }

    private suspend fun loadMembership(token: String, vpc: String?) {
        val user = profile?.userId ?: return
        val t = tokens
        membership = runCatching { Nvidia.membership(token, user, vpc) }.recoverCatching {
            // The subscription service sometimes wants the access token rather than the ID token.
            if (t != null && t.access != token) Nvidia.membership(t.access, user, vpc) else throw it
        }.onFailure { android.util.Log.w("AppState", "membership lookup failed", it) }.getOrNull() ?: membership
    }

    /** Membership modes, best first, or null when unknown. */
    val allowedModes: List<Nvidia.Mode>? get() = membership?.modes?.takeIf { it.isNotEmpty() }

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

    private val renewing = Mutex()
    private var clientCheckedAtMs = 0L

    /**
     * A valid token for GeForce NOW's services. The saved tokens last about a day; when they
     * are close to running out (or [force] says a service refused them) they are renewed with
     * the long-lived client token, so the sign-in survives restarts and long breaks.
     *
     * Throws a 401 only when the sign-in is really over: NVIDIA refused the renewal, or there
     * is nothing to renew with. A network failure never signs anyone out.
     */
    suspend fun serviceToken(force: Boolean = false): String = renewing.withLock {
        var t = tokens ?: throw ApiException("Not signed in.", 401)
        val now = System.currentTimeMillis()
        // Keep the client token fresh while the access token still works: once at every start
        // and a few times a day after that. Sign-ins saved by earlier versions get their first
        // one here. The fresher it is, the longer the app can sit unused and stay signed in.
        val recheck = if (t.client.isNullOrBlank()) 5 * 60_000L else 6 * 3600_000L
        if (now < t.expiresAtMs - 60_000L && now - clientCheckedAtMs > recheck) {
            clientCheckedAtMs = now
            runCatching { Nvidia.withClientToken(t) }
                .onSuccess { t = it; store(it) }
                .onFailure { android.util.Log.w("AppState", "client token unavailable", it) }
        }
        val expired = now >= t.staleAtMs
        if (force || now > t.staleAtMs - 10 * 60_000L) {
            if (!t.canRenew) {
                if (expired || force) throw ApiException("Your sign-in has expired.", 401)
                return@withLock t.service(now)
            }
            try {
                t = Nvidia.renew(t, profile?.userId)
                store(t)
                // Start the client token's clock again from this renewal.
                clientCheckedAtMs = now
                runCatching { Nvidia.withClientToken(t) }.onSuccess { t = it; store(it) }
            } catch (e: ApiException) {
                android.util.Log.w("AppState", "sign-in renewal refused (${e.code}): ${e.message}")
                val refused = e.code in 400..403
                when {
                    // NVIDIA said no: the sign-in is over.
                    refused && (expired || force) -> throw ApiException("Your sign-in has expired.", 401)
                    // Not expired yet: keep using what we have and try again next time.
                    !expired && !force -> Unit
                    else -> throw e
                }
            } catch (e: java.io.IOException) {
                // Offline or NVIDIA unreachable: the saved tokens may still work.
                if (expired || force) throw e
            }
        }
        t.service()
    }

    private fun store(t: Nvidia.Tokens) {
        tokens = t
        profile = Nvidia.profileFromJwt(t.id ?: t.access) ?: profile
        prefs.edit()
            .putString("access", t.access)
            .putString("refresh", t.refresh)
            .putString("id", t.id)
            .putLong("expires", t.expiresAtMs)
            .putString("client", t.client)
            .putLong("clientExpires", t.clientExpiresAtMs)
            .putString("base", streamingBase)
            .apply()
    }
}
