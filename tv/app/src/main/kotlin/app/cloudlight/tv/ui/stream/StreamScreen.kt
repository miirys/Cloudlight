package app.cloudlight.tv.ui.stream

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.cloudlight.tv.Settings
import app.cloudlight.tv.StatsCorner
import app.cloudlight.tv.StatsMode
import app.cloudlight.tv.input.PadHub
import app.cloudlight.tv.stream.StreamStats
import app.cloudlight.tv.stream.Streamer
import app.cloudlight.tv.stream.VideoOutput
import app.cloudlight.tv.stream.Rtc
import app.cloudlight.tv.ui.components.Backdrop
import app.cloudlight.tv.ui.components.GameLogo
import app.cloudlight.tv.ui.components.GlassButton
import app.cloudlight.tv.ui.components.glass
import app.cloudlight.tv.ui.motion.Motion
import app.cloudlight.tv.ui.motion.RevealPage
import app.cloudlight.tv.ui.motion.reveal
import app.cloudlight.tv.ui.theme.LocalType
import app.cloudlight.tv.ui.theme.Palette
import kotlinx.coroutines.delay

/** The surface the decoder draws into. It never takes focus, so menus keep working on top. */
private class VideoSurface(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    init {
        isFocusable = false
        isFocusableInTouchMode = false
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        VideoOutput.surface = holder.surface
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        VideoOutput.surface = null
    }
}

@Composable
fun StreamScreen(streamer: Streamer, settings: Settings, hub: PadHub) {
    val phase = streamer.phase ?: return
    val live = phase == Streamer.Phase.Live

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // 16:9 picture, letterboxed on other screen shapes.
        val pictureModifier = Modifier.align(Alignment.Center).fillMaxHeight().aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
        if (streamer.compatibleVideo) {
            AndroidView(
                factory = { ctx ->
                    org.webrtc.SurfaceViewRenderer(ctx).apply {
                        isFocusable = false
                        isFocusableInTouchMode = false
                        init(Rtc.egl.eglBaseContext, null)
                        setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                        setEnableHardwareScaler(false)
                        VideoOutput.renderer = this
                    }
                },
                modifier = pictureModifier,
                onRelease = { r ->
                    if (VideoOutput.renderer === r) VideoOutput.renderer = null
                    runCatching { r.release() }
                },
            )
        } else {
            AndroidView(
                factory = { VideoSurface(it) },
                modifier = pictureModifier,
                onRelease = { VideoOutput.surface = null },
            )
        }

        PointerCapture(enabled = live && !streamer.menuOpen, streamer = streamer)

        AnimatedVisibility(
            visible = !live,
            enter = fadeIn(Motion.page()),
            exit = fadeOut(Motion.springOf(0.7f, 1f)) + scaleOut(Motion.springOf(0.7f, 1f), targetScale = 1.04f),
        ) {
            PreparingScreen(streamer)
        }

        if (live && settings.stats != StatsMode.OFF && !streamer.menuOpen) {
            StatsOverlay(streamer.stats, settings.stats, settings.statsCorner, streamer)
        }

        Notice(streamer.notice, onDone = streamer::clearNotice, modifier = Modifier.align(Alignment.TopCenter))

        if (live) HoldHint(streamer, settings)

        AnimatedVisibility(
            visible = streamer.menuOpen,
            enter = fadeIn(Motion.fade()),
            exit = fadeOut(Motion.fade()),
        ) {
            StreamMenu(streamer, settings, hub)
        }
    }

    // While the menu is closed the remote's Back key opens it; the game never needs Back.
    BackHandler(enabled = !streamer.menuOpen && phase !is Streamer.Phase.Ended) {
        if (live) streamer.openMenu() else streamer.quit()
    }
}

/**
 * Locks a connected mouse to the stream while it plays: Android then reports raw movement,
 * which goes to the game as relative motion, exactly like a desktop game expects.
 */
@Composable
private fun PointerCapture(enabled: Boolean, streamer: Streamer) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        var buttons = 0
        if (enabled && hasMouse()) {
            view.setOnCapturedPointerListener { _, e ->
                buttons = onCaptured(e, streamer, buttons)
                true
            }
            view.requestPointerCapture()
        }
        onDispose {
            view.setOnCapturedPointerListener(null)
            if (view.hasPointerCapture()) view.releasePointerCapture()
        }
    }
}

private fun hasMouse(): Boolean = InputDevice.getDeviceIds().any { id ->
    val d = InputDevice.getDevice(id)
    d != null && !d.isVirtual && d.supportsSource(InputDevice.SOURCE_MOUSE)
}

/** Sends one captured mouse event to the game; returns the new button state. */
private fun onCaptured(e: MotionEvent, streamer: Streamer, old: Int): Int {
    when (e.actionMasked) {
        MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
            var dx = e.x
            var dy = e.y
            for (h in 0 until e.historySize) {
                dx += e.getHistoricalX(h)
                dy += e.getHistoricalY(h)
            }
            streamer.mouseMove(dx.toInt(), dy.toInt())
        }
        MotionEvent.ACTION_SCROLL -> {
            val v = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (v != 0f) streamer.wheel((v * 120).toInt())
        }
    }
    val now = e.buttonState
    val changed = old xor now
    for ((bit, id) in MOUSE_BUTTONS) {
        if (changed and bit != 0) streamer.mouseButton(now and bit != 0, id)
    }
    return now
}

private val MOUSE_BUTTONS = listOf(
    MotionEvent.BUTTON_PRIMARY to 1,
    MotionEvent.BUTTON_TERTIARY to 2,
    MotionEvent.BUTTON_SECONDARY to 3,
    MotionEvent.BUTTON_BACK to 4,
    MotionEvent.BUTTON_FORWARD to 5,
)

// ---------- Before the picture ----------

@Composable
private fun PreparingScreen(streamer: Streamer) {
    val type = LocalType.current
    val g = streamer.game
    val phase = streamer.phase
    val primary = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
        Backdrop(g?.hero, sharpArt = true)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))))
        RevealPage(key = g?.id) {
            Column(Modifier.align(Alignment.CenterStart).padding(start = 72.dp).widthIn(max = 440.dp)) {
                Box(Modifier.reveal(0).heightIn(max = 92.dp).widthIn(max = 340.dp)) {
                    GameLogo(g?.logo, g?.title ?: "Your game", Modifier.heightIn(max = 92.dp).widthIn(max = 340.dp))
                }
                Spacer(Modifier.height(26.dp))
                AnimatedContent(
                    targetState = phase,
                    contentKey = { it?.let { p -> p::class } },
                    transitionSpec = {
                        (fadeIn(Motion.page()) + slideInVertically(Motion.pageOffset) { it / 3 })
                            .togetherWith(fadeOut(Motion.fade()) + slideOutVertically(Motion.pageOffset) { -it / 4 })
                    },
                    modifier = Modifier.reveal(1),
                    label = "phase",
                ) { p ->
                    when (p) {
                        is Streamer.Phase.Queued -> Column {
                            BasicText("PLACE IN LINE", style = type.overline)
                            Spacer(Modifier.height(6.dp))
                            AnimatedContent(
                                targetState = p.position,
                                transitionSpec = {
                                    (slideInVertically(Motion.pageOffset) { it } + fadeIn(Motion.fade()))
                                        .togetherWith(slideOutVertically(Motion.pageOffset) { -it } + fadeOut(Motion.fade()))
                                },
                                label = "queue",
                            ) { n -> BasicText("$n", style = type.display.copy(fontSize = 64.sp, lineHeight = 66.sp, fontFeatureSettings = "tnum")) }
                            Spacer(Modifier.height(8.dp))
                            BasicText("The game starts by itself when it's your turn.", style = type.body)
                        }
                        is Streamer.Phase.Ended -> Column {
                            BasicText(if (p.error) "Something went wrong" else "Session ended", style = type.title)
                            Spacer(Modifier.height(8.dp))
                            BasicText(p.message, style = type.body)
                        }
                        is Streamer.Phase.Starting -> Status(p.step)
                        is Streamer.Phase.Connecting -> Status(p.step)
                        else -> Status("Starting")
                    }
                }
                Spacer(Modifier.height(26.dp))
                Row(Modifier.reveal(2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (phase is Streamer.Phase.Ended) {
                        if (phase.canRetry) GlassButton("Try again", streamer::retry, icon = Icons.Rounded.Refresh, primary = true, focusRequester = primary)
                        GlassButton("Back", streamer::dismiss, icon = Icons.Rounded.ArrowBack, focusRequester = if (phase.canRetry) null else primary)
                    } else {
                        GlassButton("Cancel", streamer::quit, icon = Icons.Rounded.Close, focusRequester = primary)
                    }
                }
                val s = streamer.session
                val detail = listOfNotNull(
                    s?.gpu?.let { "Rig: $it" },
                    if (s?.width != null && s.height != null) "${s.width} × ${s.height}${s.fps?.let { " at $it fps" } ?: ""}" else null,
                ).joinToString("  ·  ")
                if (detail.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    BasicText(detail, Modifier.reveal(3), style = type.caption)
                }
            }
        }
    }
    LaunchedEffect(phase is Streamer.Phase.Ended) {
        delay(120)
        runCatching { primary.requestFocus() }
    }
    BackHandler(enabled = phase is Streamer.Phase.Ended, onBack = streamer::dismiss)
}

@Composable
private fun Status(text: String) {
    val type = LocalType.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spinner(Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        AnimatedContent(
            targetState = text,
            transitionSpec = { fadeIn(Motion.fade()) togetherWith fadeOut(Motion.fade()) },
            label = "status",
        ) { t -> BasicText(t, style = type.headline) }
    }
}

/** A calm activity indicator: an arc that breathes while it turns. */
@Composable
fun Spinner(modifier: Modifier = Modifier, color: Color = Palette.white) {
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "angle")
    val sweep by t.animateFloat(40f, 250f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "sweep")
    Canvas(modifier) {
        val w = size.minDimension * 0.14f
        drawArc(color.copy(alpha = 0.18f), 0f, 360f, false, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w))
        drawArc(color, angle, sweep, false, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
    }
}

// ---------- While playing ----------

/** Shown once per stream: how to reach the menu. */
@Composable
private fun HoldHint(streamer: Streamer, settings: Settings) {
    var show by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(6000)
        show = false
    }
    val type = LocalType.current
    Box(Modifier.fillMaxSize().padding(bottom = 34.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = show && !streamer.menuOpen,
            enter = fadeIn(Motion.page()) + slideInVertically(Motion.pageOffset) { it },
            exit = fadeOut(Motion.fade()) + slideOutVertically(Motion.pageOffset) { it },
        ) {
            val secs = settings.holdToOpenMs / 1000f
            Box(Modifier.glass(RoundedCornerShape(50), strong = true).background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 9.dp)) {
                BasicText("Hold Start for ${if (secs < 1f) "a moment" else "%.0f s".format(secs)} or press Back to open the menu", style = type.label)
            }
        }
    }
}

@Composable
private fun Notice(text: String?, onDone: () -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(text) {
        if (text != null) {
            shown = text
            delay(6000)
            onDone()
        }
    }
    AnimatedVisibility(
        visible = text != null,
        modifier = modifier.padding(top = 28.dp),
        enter = fadeIn(Motion.page()) + slideInVertically(Motion.pageOffset) { -it },
        exit = fadeOut(Motion.fade()) + slideOutVertically(Motion.pageOffset) { -it },
    ) {
        Box(Modifier.glass(RoundedCornerShape(50), strong = true).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50)).padding(horizontal = 20.dp, vertical = 10.dp)) {
            BasicText(shown.orEmpty(), style = LocalType.current.bodyStrong)
        }
    }
}

private val Tabular = "tnum"

private fun fmt(v: Double?, digits: Int = 0, unit: String = ""): String =
    if (v == null || v.isNaN()) "–" else "%.${digits}f".format(v) + unit

fun qualityColor(q: StreamStats.Quality): Color = when (q) {
    StreamStats.Quality.GREAT -> Color(0xFF7CF29A)
    StreamStats.Quality.GOOD -> Color(0xFFC6F27C)
    StreamStats.Quality.FAIR -> Color(0xFFFFD27A)
    StreamStats.Quality.POOR -> Color(0xFFFF8A80)
    StreamStats.Quality.UNKNOWN -> Palette.textTertiary
}

@Composable
private fun StatsOverlay(s: StreamStats, mode: StatsMode, corner: StatsCorner, streamer: Streamer) {
    val align = when (corner) {
        StatsCorner.TOP_LEFT -> Alignment.TopStart
        StatsCorner.TOP_RIGHT -> Alignment.TopEnd
        StatsCorner.BOTTOM_LEFT -> Alignment.BottomStart
        StatsCorner.BOTTOM_RIGHT -> Alignment.BottomEnd
    }
    Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = align) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = { (fadeIn(Motion.page()) + scaleIn(Motion.page(), 0.96f)) togetherWith fadeOut(Motion.fade()) },
            label = "stats",
        ) { m ->
            if (m == StatsMode.DETAILED) DetailedStats(s, streamer) else CompactStats(s)
        }
    }
}

@Composable
private fun CompactStats(s: StreamStats) {
    val type = LocalType.current
    val num = type.label.copy(fontFeatureSettings = Tabular, fontSize = 10.sp)
    Row(
        Modifier.background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(6.dp).background(qualityColor(s.quality), CircleShape))
        BasicText("${fmt(s.fps)} fps", style = num)
        BasicText("${fmt(s.rttMs)} ms", style = num)
        BasicText(fmt(s.bitrateMbps, 1, " Mbps"), style = num)
        if ((s.packetLossPct ?: 0.0) >= 0.05) BasicText(fmt(s.packetLossPct, 1, "% loss"), style = num.copy(color = Color(0xFFFFD27A)))
    }
}

@Composable
private fun DetailedStats(s: StreamStats, streamer: Streamer) {
    val type = LocalType.current
    val session = streamer.session
    Column(
        Modifier.width(250.dp).background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(qualityColor(s.quality), CircleShape))
            Spacer(Modifier.width(8.dp))
            BasicText("Connection: ${s.quality.label.lowercase()}", style = type.label)
        }
        Spacer(Modifier.height(6.dp))
        StatGroup("Video")
        Stat("Resolution", if (s.width > 0) "${s.width} × ${s.height}" else "–")
        Stat("Frame rate", "${fmt(s.fps)} fps")
        Stat("Codec", s.codec ?: "–")
        Stat("Decoder", s.decoder?.substringAfterLast('.')?.take(26) ?: "–")
        Stat("Decode time", fmt(s.decodeMs, 1, " ms"))
        Stat("Frames dropped", "${s.framesDropped}")
        Stat("Freezes", "${s.freezes}")
        Spacer(Modifier.height(4.dp))
        StatGroup("Network")
        Stat("Round trip", fmt(s.rttMs, 0, " ms"))
        Stat("Jitter", fmt(s.jitterMs, 1, " ms"))
        Stat("Buffer delay", fmt(s.jitterBufferMs, 0, " ms"))
        Stat("Bitrate", fmt(s.bitrateMbps, 1, " Mbps"))
        Stat("Bandwidth estimate", fmt(s.availableMbps, 0, " Mbps"))
        Stat("Packet loss", fmt(s.packetLossPct, 2, " %"))
        Stat("Packets lost", "${s.packetsLost}")
        Stat("Retransmit requests", "${s.nacks}")
        Stat("Key frame requests", "${s.keyframeRequests}")
        Stat("Path", s.transport ?: "–")
        Spacer(Modifier.height(4.dp))
        StatGroup("Session")
        Stat("Rig", session?.gpu ?: "–")
        Stat("Server", s.serverAddress ?: session?.serverIp ?: "–")
        Stat("Playing for", elapsed(streamer.liveSince))
    }
}

@Composable
private fun StatGroup(name: String) {
    BasicText(name.uppercase(), Modifier.padding(top = 2.dp, bottom = 1.dp), style = LocalType.current.overline)
}

@Composable
private fun Stat(label: String, value: String) {
    val type = LocalType.current
    Row(Modifier.fillMaxWidth()) {
        BasicText(label, Modifier.weight(1f), style = type.caption)
        BasicText(value, style = type.caption.copy(color = Palette.text, fontFeatureSettings = Tabular), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "1 h 04 min" style elapsed time, ticking once a second. */
@Composable
fun elapsed(since: Long): String {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(since) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    if (since == 0L) return "–"
    val secs = ((now - since) / 1000).coerceAtLeast(0)
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val s = secs % 60
    return if (h > 0) "%d h %02d min".format(h, m) else "%d:%02d".format(m, s)
}
