package app.cloudlight.tv.stream

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cloudlight.tv.Settings
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

    val active: Boolean get() = phase != null
    val live: Boolean get() = phase == Phase.Live

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
        val appId = (g.variants.firstOrNull { it.selected } ?: g.variants.firstOrNull())?.id ?: g.id
        begin(g) { cm ->
            phase = Phase.Starting("Requesting a rig")
            var s = cm.create(app.streamingBase, request().copy(appId = appId))
            session = s
            s = waitForSeat(cm, s)
            s
        }
    }

    /** Takes over a session running elsewhere (or left running here). */
    fun resume(a: ActiveSession, g: Game?) {
        lastResume = a
        begin(g ?: app.gameFor(a)) { cm ->
            phase = Phase.Starting(if (a.status == 1) "Following your session" else "Taking over your session")
            val s = cm.claim(a, request().copy(appId = a.appId))
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
        val cm = Cloudmatch(app::serviceToken).also { cloud = it }
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
                codec = settings.codec.sdpName,
                bitDepth = if (settings.tenBit) 10 else 8,
            ),
            listener,
        )
        engine = e
        input.engine = e
        input.holdMs = settings.holdToOpenMs.toLong()
        input.mouseSpeed = settings.mouseSpeed
        e.volume = settings.volume / 100.0
        e.muted = muted
        e.start()
        // If no picture arrives, say so instead of spinning forever.
        scope.launch {
            delay(30_000)
            if (engine === e && phase !is Phase.Live && phase !is Phase.Ended) {
                end("The rig didn't send any video. Try again, or pick a different video codec in Settings.", error = true)
            }
        }
    }

    private val listener = object : StreamEngine.Listener {
        override fun onProgress(step: String) = ui { if (phase !is Phase.Live && phase !is Phase.Ended) phase = Phase.Connecting(step) }
        override fun onConnected() = ui { if (phase !is Phase.Live) phase = Phase.Connecting("Starting the video") }
        override fun onFirstFrame() = ui {
            if (phase !is Phase.Ended) {
                phase = Phase.Live
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

    fun mouseMove(dx: Int, dy: Int) {
        if (menuOpen) return
        val s = settings.mouseSpeed
        engine?.sendMouseMove((dx * s).toInt(), (dy * s).toInt())
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
            Cloudmatch(app::serviceToken).stop(a)
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

    private fun end(message: String, error: Boolean, canRetry: Boolean = true) {
        stopEngine()
        menuOpen = false
        hub.captured = false
        phase = Phase.Ended(message, error, canRetry)
        app.refreshSessions()
    }

    private fun finish() {
        menuOpen = false
        hub.captured = false
        phase = null
        session = null
        game = null
        lastResume = null
        stats = StreamStats.Empty
        controllerMouse = false
        input.controllerMouse = false
    }

    private fun stopEngine() {
        input.enabled = false
        input.engine = null
        input.reset()
        engine?.stop()
        engine = null
        inputReady = false
    }
}
