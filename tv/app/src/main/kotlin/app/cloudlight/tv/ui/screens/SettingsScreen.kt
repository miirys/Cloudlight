package app.cloudlight.tv.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.IntOffset
import app.cloudlight.tv.ui.components.Avatar
import app.cloudlight.tv.ui.components.InfoLine
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.cloudlight.tv.FramePacing
import app.cloudlight.tv.GameLanguage
import app.cloudlight.tv.Settings
import app.cloudlight.tv.StatsCorner
import app.cloudlight.tv.StatsMode
import app.cloudlight.tv.StreamMode
import app.cloudlight.tv.VideoCodec
import app.cloudlight.tv.VideoPath
import app.cloudlight.tv.data.AppState
import app.cloudlight.tv.input.Btn
import app.cloudlight.tv.input.PadHub
import app.cloudlight.tv.input.PadInfo
import app.cloudlight.tv.ui.components.ActionSetting
import app.cloudlight.tv.ui.components.GameArt
import app.cloudlight.tv.ui.components.PivotScroll
import app.cloudlight.tv.ui.components.Pressable
import app.cloudlight.tv.ui.components.SettingsFocus
import app.cloudlight.tv.ui.components.SettingsGroup
import app.cloudlight.tv.ui.components.SettingsList
import app.cloudlight.tv.ui.components.SwitchSetting
import app.cloudlight.tv.ui.components.ValueSetting
import app.cloudlight.tv.ui.components.onFocusColor
import app.cloudlight.tv.ui.motion.Motion
import app.cloudlight.tv.ui.motion.RevealPage
import app.cloudlight.tv.ui.motion.reveal
import app.cloudlight.tv.ui.theme.LocalType
import app.cloudlight.tv.ui.theme.Palette

private enum class Page(val label: String, val icon: ImageVector) {
    STREAM("Stream", Icons.Rounded.PlayArrow),
    GAME("In game", Icons.Rounded.Settings),
    CONTROLLERS("Controllers", Icons.Rounded.Star),
    ACCOUNT("Account", Icons.Rounded.AccountCircle),
    ABOUT("About", Icons.Rounded.Info),
}

private val PageItemHeight = 34.dp
private val PageItemGap = 4.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(hub: PadHub, app: AppState, settings: Settings) {
    var page by remember { mutableStateOf(Page.STREAM) }
    var pageDir by remember { mutableIntStateOf(1) }
    fun open(p: Page) {
        if (p == page) return
        pageDir = if (p.ordinal > page.ordinal) 1 else -1
        page = p
    }
    val entry = LocalSectionEntry.current
    val pageFocus = remember { Page.entries.associateWith { FocusRequester() } }
    val pillY by animateDpAsState((PageItemHeight + PageItemGap) * page.ordinal, Motion.focus(), label = "pill")
    Row(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = TopBarHeight + 18.dp)) {
        Box(
            Modifier
                .width(176.dp)
                .reveal(0)
                // Coming back from the list always lands on the open page.
                .focusProperties { enter = { pageFocus.getValue(page) } }
                .focusGroup(),
        ) {
            Box(Modifier.offset(y = pillY).fillMaxWidth().height(PageItemHeight).background(Palette.glassStrong, RoundedCornerShape(50)))
            Column(verticalArrangement = Arrangement.spacedBy(PageItemGap)) {
                for (p in Page.entries) {
                    Pressable(
                        onClick = { open(p) },
                        shape = RoundedCornerShape(50),
                        focusedScale = 1.03f,
                        color = Color.Transparent,
                        border = false,
                        focusRequester = pageFocus.getValue(p),
                        modifier = Modifier.fillMaxWidth().height(PageItemHeight).then(
                            if (p == page && entry != null) Modifier.focusRequester(entry) else Modifier,
                        ),
                    ) { focused ->
                        LaunchedEffect(focused) { if (focused) open(p) }
                        val fg = onFocusColor(focused, if (p == page) Palette.text else Palette.textSecondary)
                        Row(Modifier.fillMaxSize().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(p.icon, null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(fg))
                            Spacer(Modifier.width(10.dp))
                            BasicText(p.label, style = LocalType.current.label.copy(color = fg))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(36.dp))
        AnimatedContent(
            targetState = page,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                // Pages stack like the list beside them: moving down the list brings the next
                // page up from below, and the old one lifts away.
                val d = pageDir
                (fadeIn(tween(220, delayMillis = 50)) + slideInVertically(Motion.springOf(0.55f, 0.84f, IntOffset(1, 1))) { it / 9 * d })
                    .togetherWith(fadeOut(tween(140)) + slideOutVertically(Motion.springOf(0.45f, 1f, IntOffset(1, 1))) { -it / 14 * d } + scaleOut(tween(220), targetScale = 0.97f))
            },
            label = "page",
        ) { p ->
            RevealPage(key = p, dx = 1) {
                when (p) {
                    Page.STREAM -> ListPage { StreamPage(settings, app) }
                    Page.GAME -> ListPage { GamePage(settings) }
                    Page.CONTROLLERS -> ControllerPage(hub)
                    Page.ACCOUNT -> ListPage { AccountPage(app) }
                    Page.ABOUT -> AboutPage(app, settings)
                }
            }
        }
    }
}

/** A scrolling settings list with the focused row's hint underneath. */
@Composable
private fun ListPage(content: @Composable () -> Unit) {
    val focus = remember { SettingsFocus() }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            PivotScroll(leadingFraction = 0.3f) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    SettingsList(focus) { content() }
                }
            }
        }
        // One line of help for the focused row, in the same place every time.
        AnimatedContent(
            targetState = if (focus.active) focus.hint else null,
            transitionSpec = { fadeIn(Motion.fade()) togetherWith fadeOut(Motion.fade()) },
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 16.dp, top = 6.dp),
            label = "hint",
        ) { h ->
            if (h != null) BasicText(h, style = LocalType.current.body)
        }
    }
}

private fun modeLabel(height: Int, fps: Int) = "${if (height >= 2160) "4K" else "${height}p"} at $fps fps"

private fun gbPerHour(mbps: Int) = mbps * 3600 / 8 / 1000.0

@Composable
private fun StreamPage(settings: Settings, app: AppState) {
    SettingsGroup("Picture")
    val m = settings.mode
    val allowed = app.allowedModes
    val tier = app.membership?.tier?.let { "Your $it membership" } ?: "Your membership"
    // Only modes this app can stream count; a 4K entitlement still streams at 1440p here.
    val best = allowed?.filter { it.height <= 1440 }?.maxWithOrNull(compareBy({ it.height }, { it.fps }))
    val fits = allowed == null || allowed.any { it.height >= m.height && it.fps >= m.fps }
    val modeHint = when {
        !fits && best != null -> "$tier goes up to ${modeLabel(best.height, best.fps)}, so games start there instead."
        best != null && (best.height < 1440 || best.fps < 120) -> "$tier goes up to ${modeLabel(best.height, best.fps)}."
        else -> "120 fps needs a TV that can run at 120 Hz."
    }
    ValueSetting(
        "Resolution",
        StreamMode.entries.map { mode ->
            val ok = allowed == null || allowed.any { it.height >= mode.height && it.fps >= mode.fps }
            if (ok) mode.label else "${mode.label}, not included"
        },
        settings.mode.ordinal,
        { settings.cycleMode(it - settings.mode.ordinal) },
        hint = modeHint,
    )
    SwitchSetting(
        "4K at 60 fps (experimental)",
        settings.native4k,
        settings::toggleNative4k,
        hint = if (settings.native4k) {
            settings.nativeLog.lines().lastOrNull { it.isNotBlank() }?.let { "Last time: $it" }
                ?: "Streams over NVIDIA's native protocol in place of the resolution above. If it can't start, the normal stream is used."
        } else {
            "GeForce NOW only gives 4K to NVIDIA's native protocol. This streams with it. Needs Ultimate."
        },
    )
    val regions = app.regions
    if (regions.isNotEmpty()) {
        val ri = regions.indexOfFirst { it.name == settings.region } + 1
        ValueSetting(
            "Server location",
            listOf("Automatic") + regions.map { r -> r.pingMs?.let { "${r.name}, $it ms" } ?: r.name },
            ri,
            { settings.chooseRegion(if (it == 0) "" else regions[it - 1].name) },
            hint = if (ri == 0) "Uses the nearest server, and tries the next closest ones if it can't start your resolution." else "Always streams from this location.",
        )
    }
    val bi = Settings.BITRATES.indexOf(settings.bitrateMbps).coerceAtLeast(0)
    ValueSetting(
        "Maximum bitrate",
        Settings.BITRATES.map { "$it Mbps" },
        bi,
        { settings.stepBitrate(it - bi) },
        hint = "Up to %.0f GB an hour. Lower it if the picture stutters.".format(gbPerHour(settings.bitrateMbps)),
    )
    ValueSetting(
        "Video codec",
        VideoCodec.entries.map { it.label },
        settings.codec.ordinal,
        { settings.cycleCodec(it - settings.codec.ordinal) },
        hint = "Automatic picks the best one your TV can decode.",
    )
    ValueSetting(
        "Video output",
        VideoPath.entries.map { it.label },
        settings.videoPath.ordinal,
        { settings.cycleVideoPath(it - settings.videoPath.ordinal) },
        hint = when (settings.videoPath) {
            VideoPath.AUTO -> "Picks the fastest output this box handles, and falls back if it fails."
            VideoPath.DIRECT -> "Lowest delay. Switch to Compatible if streams close the app."
            VideoPath.COMPATIBLE -> "Works on more TVs, with a little more delay."
        },
    )
    ValueSetting(
        "Frame pacing",
        FramePacing.entries.map { it.label },
        settings.framePacing.ordinal,
        { settings.cycleFramePacing(it - settings.framePacing.ordinal) },
        hint = when (settings.framePacing) {
            FramePacing.SMOOTH -> "Waits for the screen on every frame. Can cap 120 fps streams near 90."
            FramePacing.FASTEST -> "Never waits for the screen, so every frame is decoded. Best for 120 fps."
        },
    )
    settings.fixes?.let { f ->
        ActionSetting(
            "Undo automatic fixes",
            settings::clearFixes,
            value = f,
            hint = "Turned on after streams closed the app. Undo them to try the defaults again.",
        )
    }
    SwitchSetting("10-bit colour", settings.tenBit, settings::toggleTenBit, hint = "Smoother skies and shadows. Needs HEVC or AV1.")
    SwitchSetting(
        "Highest refresh rate",
        settings.maxRefresh,
        settings::toggleMaxRefresh,
        hint = "Runs the TV at its fastest mode, for smoother menus and 120 fps streams.",
    )
    SettingsGroup("Game")
    SwitchSetting("NVIDIA Reflex", settings.lowLatency, settings::toggleLowLatency, hint = "Lower input lag in supported games at 120 fps.")
    ValueSetting(
        "Game language",
        GameLanguage.entries.map { it.label },
        settings.language.ordinal,
        { settings.cycleLanguage(it - settings.language.ordinal) },
        hint = "Used by games that offer more than one language.",
    )
}

@Composable
private fun GamePage(settings: Settings) {
    SettingsGroup("Overlay")
    ValueSetting(
        "Performance stats",
        StatsMode.entries.map { it.label },
        settings.stats.ordinal,
        { settings.setStatsMode(StatsMode.entries[it]) },
        hint = "Also in the stream menu while you play.",
    )
    ValueSetting(
        "Stats position",
        StatsCorner.entries.map { it.label },
        settings.statsCorner.ordinal,
        { settings.cycleCorner(it - settings.statsCorner.ordinal) },
    )
    SwitchSetting("Session timer", settings.sessionTimer, settings::toggleTimer, hint = "Shows how long you've played in the stream menu.")
    SettingsGroup("Controls")
    val hi = Settings.HOLD_TIMES.indexOf(settings.holdToOpenMs).coerceAtLeast(0)
    ValueSetting(
        "Hold Start for menu",
        Settings.HOLD_TIMES.map { ms -> (ms / 1000.0).let { v -> if (v == Math.floor(v)) "${v.toInt()} s" else "$v s" } },
        hi,
        { settings.stepHold(it - hi) },
        hint = "A quick press still goes to the game. Guide opens the menu too.",
    )
    val mi = Settings.MOUSE_SPEEDS.indexOfFirst { it >= settings.mouseSpeed - 0.01f }.coerceAtLeast(0)
    ValueSetting(
        "Mouse speed",
        Settings.MOUSE_SPEEDS.map { "${(it * 100).toInt()} %" },
        mi,
        { settings.stepMouseSpeed(it - mi) },
    )
    SettingsGroup("Sound")
    ValueSetting(
        "Game volume",
        (0..10).map { "${it * 10} %" },
        settings.volume / 10,
        { settings.stepVolume(it - settings.volume / 10) },
    )
}

@Composable
private fun AccountPage(app: AppState) {
    val p = app.profile
    val type = LocalType.current
    if (p == null) {
        SettingsGroup("Not signed in")
        BasicText("Sign in from Home.", Modifier.padding(start = 16.dp), style = type.body)
        return
    }
    Row(Modifier.padding(start = 16.dp, top = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(p.avatar, Modifier.size(56.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            BasicText(p.name, style = type.title)
            BasicText(listOfNotNull(p.email, app.membership?.tier ?: p.tier).joinToString("  ·  "), style = type.body)
        }
    }
    app.membership?.let { mem ->
        SettingsGroup("Membership")
        mem.modes.maxWithOrNull(compareBy({ it.height }, { it.fps }))?.let { InfoLine("Best stream", modeLabel(it.height, it.fps)) }
        InfoLine("Play time", if (mem.unlimited) "Unlimited" else mem.hoursLeft?.let { "%.1f hours left".format(it) } ?: "Unknown")
    }
    ActionSetting("Refresh library", app::refresh, value = "${app.library.size} games", hint = "Picks up games you added in another app.")
    ActionSetting("Sign out", app::signOut, hint = "You can sign back in with your phone.")
}
