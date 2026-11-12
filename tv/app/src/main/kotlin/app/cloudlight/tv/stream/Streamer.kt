package app.cloudlight.tv.stream

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cloudlight.tv.CrashLog
import app.cloudlight.tv.Diagnostics
import app.cloudlight.tv.Settings
import app.cloudlight.tv.VideoPath
import app.cloudlight.tv.VideoCodec
import app.cloudlight.tv.data.ActiveSession
import app.cloudlight.tv.data.AppState
import app.cloudlight.tv.data.CloudSession
import app.cloudlight.tv.data.Cloudmatch
import app.cloudlight.tv.data.Game
import app.cloudlight.tv.data.LaunchRequest
import app.cloudlight.tv.data.SessionException
import app.cloudlight.tv.input.PadHub
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Runs a game from the moment Play is pressed until the session ends: asks for a rig, waits
 * in the queue, connects, and keeps the screens informed. Compose reads its state directly.
 */
class Streamer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val app: AppState,
    private val settings: Settings,
    private val hub: PadHub,
) {
    sealed interface Phase {
        /** Talking to the session service; [step] says what we are doing. */
        data class Starting(val step: String) : Phase
        data class Queued(val position: Int) : Phase
        data class Connecting(val step: String) : Phase
        data object Live : Phase
        data class Ended(val message: String, val error: Boolean, val canRetry: Boolean) : Phase
    }

    var phase by mutableStateOf<Phase?>(null)
        private set
    var game by mutableStateOf<Game?>(null)
        private set
    var stats by mutableStateOf(StreamStats.Empty)
        private set
    var menuOpen by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)

    /** The running stream's frame rate, 0 when nothing is streaming. Picks the TV's mode. */
    var streamFps by mutableIntStateOf(0)
        private set
        private set
    var liveSince by mutableLongStateOf(0L)
        private set
    var muted by mutableStateOf(false)
        private set
    var controllerMouse by mutableStateOf(false)
        private set
    var session by mutableStateOf<CloudSession?>(null)
        private set
    var inputReady by mutableStateOf(false)
        private set
    /** Whether this stream uses the compatible video path (WebRTC's renderer). */
    var compatibleVideo by mutableStateOf(false)
        private set

    val active: Boolean get() = phase != null
    val live: Boolean get() = phase == Phase.Live

    /** True while the stream plays in a small picture-in-picture window. */
    var inPip by mutableStateOf(false)

    private val main = Handler(Looper.getMainLooper())
    private var job: Job? = null
    @Volatile private var engine: StreamEngine? = null
    private var cloud: Cloudmatch? = null
    private var lastResume: ActiveSession? = null
    private var lastGame: Game? = null

    val input = GameInput(onMenu = { openMenu() })

    init {
        hub.raw = { id, state -> if (engine != null) input.report(id, state) }
    }

    private fun request(): LaunchRequest {
        val m = settings.mode
        return LaunchRequest(
            appId = "",
            width = m.width,
            height = m.height,
            fps = m.fps,
            bitDepth = if (settings.tenBit) 10 else 8,
            lowLatencyMode = settings.lowLatency,
            language = settings.language.code,
        )
    }

    /** Starts [g], or picks up its session if one is already running for the account. */
    fun play(g: Game) {
        val running = app.sessions.firstOrNull { app.gameFor(it) == g }
        if (running != null) {
            resume(running, g)
            return
        }
        startFresh(g)
    }

    /** Whether the running session was asked for with the TV identity (see [LaunchRequest.asBrowser]). */
    private var tvIdentityInUse = false

    /** 4K is asked for with the TV identity first, unless it has already failed here. */
    // Asking as the Android TV client still got 1440p rigs: NVIDIA keeps 4K for its native
    // streaming protocol, not WebRTC. Kept switched off until that protocol is supported.
    @Suppress("UNUSED_PARAMETER")
    private fun useTvIdentity(height: Int) = false

    private fun startFresh(g: Game) {
        lastResume = null
        val v = g.variants.firstOrNull { it.selected } ?: g.variants.firstOrNull()
        val appId = v?.id ?: g.id
        begin(g) { cm ->
            phase = Phase.Starting("Requesting a rig")
            val (w, h, fps) = target()
            val ask = request().copy(appId = appId, title = g.title, accountLinked = v?.owned ?: true, width = w, height = h, fps = fps)
            var s = start(cm, ask)
            session = s
            s = waitForSeat(cm, s)
            s
        }
    }

    /**
     * The mode to ask for: the one chosen in Settings, or the closest one the membership
     * includes when the choice is beyond it (asking for more only gets the request refused).
     */
    private fun target(): Triple<Int, Int, Int> {
        val m = settings.mode
        // Ultimate includes every mode; never second-guess it from the entitlement list.
        if (app.membership?.tier.equals("Ultimate", ignoreCase = true)) return Triple(m.width, m.height, m.fps)
        val allowed = app.allowedModes ?: return Triple(m.width, m.height, m.fps)
        if (allowed.any { it.height >= m.height && it.fps >= m.fps }) return Triple(m.width, m.height, m.fps)
        val best = allowed.filter { it.height <= m.height && it.fps <= m.fps }.maxWithOrNull(compareBy({ it.height }, { it.fps }))
            ?: allowed.minWithOrNull(compareBy({ it.height }, { it.fps }))
            ?: return Triple(m.width, m.height, m.fps)
        val tier = app.membership?.tier
        notice = "Playing at ${modeName(best.height, best.fps)}, the most ${if (tier != null) "your $tier membership" else "your membership"} includes."
        return Triple(best.width, best.height, best.fps)
    }

    /**
     * Asks for a rig. When the nearest location turns the stream shape down (it happens when
     * its high-end rigs are busy or missing, whatever the membership), the next-closest
     * locations are asked for the same quality first. Only if none of them can is the quality
     * stepped down, one level at a time.
     */
    private suspend fun start(cm: Cloudmatch, ask: LaunchRequest): CloudSession {
        val chosen = settings.region.takeIf { it.isNotEmpty() }?.let { n -> app.regions.firstOrNull { it.name == n } }
        val nearest = app.regions.firstOrNull { it.pingMs != null }?.pingMs
        val bases: List<Pair<String?, String>> = if (chosen != null) {
            listOf(chosen.name to chosen.url)
        } else {
            val near = app.regions
                .filter { r -> r.pingMs != null && nearest != null && r.pingMs <= nearest * 2 + 25 }
                .take(3)
                .map { it.name to it.url }
            (listOf<Pair<String?, String>>(null to app.streamingBase) + near).distinctBy { it.second.trimEnd('/') }
        }
        val ladder = buildList {
            add(ask)
            if (ask.bitDepth == 10) add(ask.copy(bitDepth = 8))
            for ((w, h, f) in listOf(Triple(2560, 1440, 120), Triple(2560, 1440, 60), Triple(1920, 1080, 120), Triple(1920, 1080, 60))) {
                if (h <= ask.height && f <= ask.fps && !(h == ask.height && f == ask.fps)) add(ask.copy(width = w, height = h, fps = f, bitDepth = 8))
            }
        }
        var last: SessionException? = null
        for ((i, attempt) in ladder.withIndex()) {
            // The full quality is worth trying everywhere nearby; lower steps stay on the first.
            // Always as the desktop web client, so the rig streams over WebRTC.
            val places = if (i == 0) bases else bases.take(1)
            val where = if (useTvIdentity(attempt.height)) places.flatMap { listOf(it to false, it to true) } else places.map { it to true }
            for ((j, entry) in where.withIndex()) {
                val (place, asBrowser) = entry
                val (name, base) = place
                if (j > 0) phase = Phase.Starting("Trying ${name ?: "another server"}")
                try {
                    val s = cm.create(base, attempt.copy(asBrowser = asBrowser))
                    tvIdentityInUse = !asBrowser
                    notice = when {
                        i == 0 && name != null && chosen == null ->
                            "Playing from $name, because the nearest server couldn't start ${modeName(ask.height, ask.fps)}."
                        i == 0 -> notice
                        attempt.height == ask.height && attempt.fps == ask.fps -> "Playing in 8-bit colour; this game wouldn't start in 10-bit."
                        else -> "Playing at ${modeName(attempt.height, attempt.fps)}; no server nearby could start ${modeName(ask.height, ask.fps)} for this game."
                    }
                    return s
                } catch (e: SessionException) {
                    android.util.Log.w("Streamer", "create refused at ${attempt.width}x${attempt.height}@${attempt.fps} via ${name ?: "default"}${if (asBrowser) " as browser" else ""}: ${e.message}")
                    Diagnostics.lastRefusal =
                        "${modeName(attempt.height, attempt.fps)}${if (asBrowser) " as web client" else ""}: ${e.description ?: e.message}"
                    if (!e.retryable) throw e
                    last = e
                }
            }
            ladder.getOrNull(i + 1)?.let { phase = Phase.Starting("Trying ${modeName(it.height, it.fps)}") }
        }
        throw last ?: SessionException("GeForce NOW couldn't start the game.")
    }

    private fun modeName(height: Int, fps: Int) = "${if (height >= 2160) "4K" else "${height}p"} at $fps fps"

    /** Takes over a session running elsewhere (or left running here). */
    fun resume(a: ActiveSession, g: Game?) {
        lastResume = a
        begin(g ?: app.gameFor(a)) { cm ->
            phase = Phase.Starting(if (a.status == 1) "Following your session" else "Taking over your session")
            val (w, h, fps) = target()
            tvIdentityInUse = false
            val s = cm.claim(a, request().copy(appId = a.appId, width = w, height = h, fps = fps, asBrowser = !useTvIdentity(h)))
            session = s
            s
        }
    }

    private suspend fun waitForSeat(cm: Cloudmatch, first: CloudSession): CloudSession {
        var s = first
        var tries = 0
        while (!s.ready) {
            if (s.status !in 1..3) throw SessionException("The session ended before it was ready.")
            if (s.adsRequired) {
                throw SessionException("Your membership shows ads in the queue, which this app can't play. A paid membership skips them.")
            }
            val q = s.queuePosition
            phase = if (q != null && q > 0) Phase.Queued(q) else Phase.Starting(setupText(s.setupStep, tries))
            delay(if (q != null && q > 10) 4000 else 1500)
            s = runCatching { cm.poll(s) }.getOrElse { e ->
                if (e is SessionException && e.statusCode != 0) throw e
                if (++tries > 20) throw e
                s
            }
            session = s
        }
        return s
    }

    private fun setupText(step: Int?, tries: Int) = when {
        step == null && tries == 0 -> "Preparing your rig"
        step == null -> "Preparing your rig"
        step <= 1 -> "Finding a rig"
        step <= 3 -> "Preparing your rig"
        else -> "Launching the game"
    }

    private fun begin(g: Game?, obtain: suspend (Cloudmatch) -> CloudSession) {
        stopEngine()
        job?.cancel()
        game = g
        lastGame = g
        stats = StreamStats.Empty
        menuOpen = false
        notice = null
        inputReady = false
        liveSince = 0
        // The controller keeps driving our screens until the picture is up.
        hub.captured = false
        // Chosen before the stream screen appears, since it decides what that screen draws into.
        compatibleVideo = when (settings.videoPath) {
            VideoPath.COMPATIBLE -> true
            VideoPath.DIRECT -> false
            // WebRTC's own decoder and renderer: the path OpenNOW streams with on these boxes.
            // The direct path stays available as an explicit choice.
            VideoPath.AUTO -> true
        }
        val cm = Cloudmatch({ app.serviceToken() }).also { cloud = it }
        job = scope.launch {
            try {
                val s = obtain(cm)
                session = s
                connect(s)
            } catch (c: CancellationException) {
                throw c
            } catch (e: SessionException) {
                end(e.message ?: "The session couldn't start.", error = true)
            } catch (t: Throwable) {
                end("GeForce NOW can't be reached. Check the network connection.", error = true)
            }
        }
    }

    private fun connect(s: CloudSession) {
        phase = Phase.Connecting("Connecting to the rig")
        val m = settings.mode
        val e = StreamEngine(
            context,
            s,
            StreamEngine.Config(
                width = s.width ?: m.width,
                height = s.height ?: m.height,
                fps = s.fps ?: m.fps,
                maxKbps = settings.bitrateMbps * 1000,
                codec = if (settings.safeCodec && settings.codec == VideoCodec.AUTO) "H264" else settings.codec.sdpName,
                bitDepth = if (settings.tenBit) 10 else 8,
                audio = !settings.noAudio,
            ),
            listener,
        )
        engine = e
        VideoOutput.compatible = compatibleVideo
        CrashLog.markStreaming(context, if (compatibleVideo) "compatible" else "direct")
        input.engine = e
        // The game only hears about a controller when it reports, and a controller at rest
        // reports nothing, so tell it now about every one that is already connected.
        for ((id, state) in hub.snapshot()) input.report(id, state)
        VideoHooks.swapInterval = settings.framePacing.swapInterval
        streamFps = s.fps ?: m.fps
        input.holdMs = settings.holdToOpenMs.toLong()
        input.mouseSpeed = settings.mouseSpeed
        e.volume = settings.volume / 100.0
        e.muted = muted
        e.start()
        // If no picture arrives, say so instead of spinning forever.
        scope.launch {
            delay(30_000)
            if (engine === e && phase !is Phase.Live && phase !is Phase.Ended) {
                end(
                    if (e.offered) "The rig didn't send any video. Try again, or pick a different video codec in Settings."
                    else "The rig didn't start the stream. Try again in a moment.",
                    error = true,
                )
            }
        }
    }

    private val listener = object : StreamEngine.Listener {
        override fun onProgress(step: String) = ui { if (phase !is Phase.Live && phase !is Phase.Ended) phase = Phase.Connecting(step) }
        override fun onConnected() = ui { if (phase !is Phase.Live) phase = Phase.Connecting("Starting the video") }
        override fun onFirstFrame() = ui {
            if (phase !is Phase.Ended) {
                CrashLog.step("live")
                phase = Phase.Live
                if (settings.noAudio && notice == null) notice = NO_SOUND
                if (notice == null) notice = slowScreenNotice()
                if (liveSince == 0L) liveSince = SystemClock.elapsedRealtime()
                hub.captured = !menuOpen
                input.enabled = !menuOpen
            }
        }
        override fun onInputReady() = ui {
            inputReady = true
            input.enabled = !menuOpen && phase == Phase.Live
        }
        override fun onStats(stats: StreamStats) = ui { this@Streamer.stats = stats }
        override fun onTimeWarning(code: Int, secondsLeft: Int?) = ui {
            val mins = secondsLeft?.let { (it + 59) / 60 }
            notice = when (code) {
                1, 2 -> if (mins != null) "Your session ends in $mins ${if (mins == 1) "minute" else "minutes"}." else "Your session time is almost up."
                4 -> "You've been idle for a while. Press something to keep playing."
                6 -> "Your session is ending."
                else -> null
            }
        }
        override fun onClosed(reason: String, error: Boolean) = ui {
            if (phase is Phase.Ended) return@ui
            end(reason, error, canRetry = true)
        }
    }

    private fun ui(block: () -> Unit) {
        main.post(block)
    }

    // ---------- Menu ----------

    fun openMenu() {
        if (phase == null || phase is Phase.Ended || menuOpen) return
        menuOpen = true
        input.enabled = false
        hub.captured = false
    }

    fun closeMenu() {
        if (!menuOpen) return
        menuOpen = false
        if (phase == Phase.Live) {
            hub.captured = true
            input.enabled = true
        }
    }

    /** True while the on-screen keyboard types into the game. */
    var keyboardOpen by mutableStateOf(false)
        private set

    fun openKeyboard() {
        if (phase != Phase.Live) return
        closeMenu()
        keyboardOpen = true
        input.enabled = false
        hub.captured = false
    }

    fun closeKeyboard() {
        if (!keyboardOpen) return
        keyboardOpen = false
        if (phase == Phase.Live && !menuOpen) {
            hub.captured = true
            input.enabled = true
        }
    }

    fun type(text: String) {
        if (text.isNotEmpty()) engine?.typeText(text)
    }

    fun clearNotice() {
        notice = null
    }

    fun toggleMute() {
        muted = !muted
        engine?.muted = muted
    }

    fun stepVolume(step: Int) {
        settings.stepVolume(step)
        engine?.volume = settings.volume / 100.0
    }

    fun stepMouseSpeed(step: Int) {
        settings.stepMouseSpeed(step)
        input.mouseSpeed = settings.mouseSpeed
    }

    fun toggleControllerMouse() {
        controllerMouse = !controllerMouse
        input.controllerMouse = controllerMouse
    }

    fun sendCombo(vararg keys: Keymap.Key) {
        engine?.sendCombo(*keys)
    }

    fun refreshPicture() {
        engine?.requestKeyframe("user")
    }

    // ---------- Keyboard and mouse from the activity ----------

    fun key(down: Boolean, key: Keymap.Key, modifiers: Int) {
        if (!menuOpen) engine?.sendKey(down, key, modifiers)
    }

    // Fractions of a count left over from earlier moves, so slow, careful aiming isn't lost
    // to rounding.
    private var restX = 0f
    private var restY = 0f

    fun mouseMove(dx: Float, dy: Float) {
        if (menuOpen) return
        val s = settings.mouseSpeed
        restX += dx * s
        restY += dy * s
        val x = restX.toInt()
        val y = restY.toInt()
        restX -= x
        restY -= y
        engine?.sendMouseMove(x, y)
    }

    fun mouseButton(down: Boolean, button: Int) {
        if (!menuOpen) engine?.sendMouseButton(down, button)
    }

    fun wheel(delta: Int) {
        if (!menuOpen) engine?.sendWheel(delta)
    }

    // ---------- Ending ----------

    /** Leaves the stream but keeps the game running in the cloud, to resume later. */
    fun leave() {
        stopEngine()
        job?.cancel()
        finish()
        app.refreshSessions()
    }

    /** Ends the session on the server. */
    fun quit() {
        val s = session
        val cm = cloud
        stopEngine()
        job?.cancel()
        finish()
        scope.launch {
            if (s != null && cm != null) cm.stop(s)
            delay(800)
            app.refreshSessions()
        }
    }

    /** Ends a session running elsewhere without opening it. */
    fun quit(a: ActiveSession) {
        scope.launch {
            Cloudmatch({ app.serviceToken() }).stop(a)
            delay(800)
            app.refreshSessions()
        }
    }

    fun retry() {
        val g = lastGame
        val r = lastResume
        val s = session
        when {
            s != null && s.ready -> {
                // The rig is still ours: reconnect to it rather than queueing again.
                val a = ActiveSession(s.id, "", 2, null, null, null, s.controlBase, s.serverIp, s.gpu)
                begin(g) { cm -> cm.claim(a.copy(appId = r?.appId ?: appIdOf(g)), request().copy(appId = r?.appId ?: appIdOf(g))) }
            }
            r != null -> resume(r, g)
            g != null -> play(g)
            else -> finish()
        }
    }

    private fun appIdOf(g: Game?) = g?.let { (it.variants.firstOrNull { v -> v.selected } ?: it.variants.firstOrNull())?.id ?: it.id }.orEmpty()

    fun dismiss() = finish()

    /** A background thread failed; end the stream cleanly rather than leaving it frozen. */
    fun internalFailure(t: Throwable) {
        if (phase == null || phase is Phase.Ended) return
        end("The stream stopped because of an internal error (${t.javaClass.simpleName}). Your game is still running; try again to reconnect.", error = true)
    }

    private fun end(message: String, error: Boolean, canRetry: Boolean = true) {
        if (error && fallBackFromTvIdentity()) return
        stopEngine()
        menuOpen = false
        hub.captured = false
        phase = Phase.Ended(message, error, canRetry)
        app.refreshSessions()
    }

    /**
     * A new 4K session asked for with the TV identity can land on a rig that only streams
     * NVIDIA's native way and never starts WebRTC. Then end it and ask again as the web client,
     * and remember not to try that here again.
     */
    private fun fallBackFromTvIdentity(): Boolean {
        val e = engine ?: return false
        val g = lastGame ?: return false
        if (!tvIdentityInUse || e.offered || lastResume != null) return false
        tvIdentityInUse = false
        settings.markTvIdentityFailed()
        val s = session
        val cm = cloud
        stopEngine()
        phase = Phase.Starting("Trying another kind of rig")
        scope.launch {
            if (s != null && cm != null) runCatching { cm.stop(s) }
            delay(1500)
            startFresh(g)
        }
        return true
    }

    /** Says so when the TV is refreshing slower than the stream, which caps what is shown. */
    private fun slowScreenNotice(): String? = runCatching {
        val hz = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate ?: return null
        if (streamFps <= 0 || hz >= streamFps - 1f) return null
        "The TV is running at ${hz.roundToInt()} Hz, so this $streamFps fps stream shows at ${hz.roundToInt()}. " +
            "Set the TV's output to $streamFps Hz to see every frame."
    }.getOrNull()

    private fun finish() {
        streamFps = 0
        menuOpen = false
        keyboardOpen = false
        hub.captured = false
        phase = null
        session = null
        game = null
        lastResume = null
        stats = StreamStats.Empty
        controllerMouse = false
        input.controllerMouse = false
    }

    private companion object {
        const val NO_SOUND = "Sound is off after an earlier crash. Settings, Stream can turn it back on."
    }

    private fun stopEngine() {
        CrashLog.markStreaming(context, null)
        input.enabled = false
        input.engine = null
        input.reset()
        engine?.stop()
        engine = null
        inputReady = false
    }
}
