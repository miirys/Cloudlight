package app.cloudlight.tv.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import app.cloudlight.tv.BuildConfig
import app.cloudlight.tv.GameLanguage
import app.cloudlight.tv.Settings
import app.cloudlight.tv.StatsCorner
import app.cloudlight.tv.StatsMode
import app.cloudlight.tv.StreamMode
import app.cloudlight.tv.VideoCodec
import app.cloudlight.tv.stream.TvDecoderFactory
import app.cloudlight.tv.ui.components.ChoiceRow
import app.cloudlight.tv.ui.components.PivotScroll
import app.cloudlight.tv.ui.components.ToggleRow
import app.cloudlight.tv.ui.motion.RevealPage
import app.cloudlight.tv.ui.motion.reveal
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleOut
import app.cloudlight.tv.data.AppState
import app.cloudlight.tv.input.Btn
import app.cloudlight.tv.input.PadHub
import app.cloudlight.tv.input.PadInfo
import app.cloudlight.tv.ui.components.GlassButton
import app.cloudlight.tv.ui.components.Pressable
import app.cloudlight.tv.ui.components.glass
import app.cloudlight.tv.ui.components.onFocusColor
import app.cloudlight.tv.ui.motion.Motion
import app.cloudlight.tv.ui.theme.LocalType
import app.cloudlight.tv.ui.theme.Palette

private enum class Page(val label: String) {
    STREAM("Stream quality"), PLAYING("While playing"), CONTROLLER("Controllers"), DISPLAY("Display"), ACCOUNT("Account"), ABOUT("About")
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(hub: PadHub, app: AppState, settings: Settings) {
    val type = LocalType.current
    var page by remember { mutableStateOf(Page.STREAM) }
    val pageFocus = remember { Page.entries.associateWith { FocusRequester() } }
    val pillY by animateDpAsState(38.dp * page.ordinal, Motion.focus(), label = "pill")
    Row(Modifier.fillMaxSize().padding(start = 40.dp, end = 48.dp, top = 34.dp, bottom = 34.dp)) {
        Column(Modifier.width(200.dp)) {
            BasicText("Settings", Modifier.reveal(0).padding(start = 12.dp, bottom = 16.dp), style = type.title)
            Box(
                Modifier
                    .reveal(1)
                    // Coming back from a panel always lands on the open page, so it never
                    // switches pages by accident.
                    .focusProperties { enter = { pageFocus.getValue(page) } }
                    .focusGroup(),
            ) {
                Box(Modifier.offset(y = pillY).fillMaxWidth().height(34.dp).background(Palette.glassStrong, RoundedCornerShape(10.dp)))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (p in Page.entries) {
                        val selected = p == page
                        Pressable(
                            onClick = { page = p },
                            modifier = Modifier.fillMaxWidth().height(34.dp),
                            shape = RoundedCornerShape(10.dp),
                            focusedScale = 1.03f,
                            color = Color.Transparent,
                            border = false,
                            focusRequester = pageFocus.getValue(p),
                        ) { focused ->
                            LaunchedEffect(focused) { if (focused) page = p }
                            val fg = onFocusColor(focused, if (selected) Palette.text else Palette.textSecondary)
                            BasicText(p.label, Modifier.fillMaxWidth().padding(horizontal = 12.dp), style = type.label.copy(color = fg))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(28.dp))
        AnimatedContent(
            targetState = page,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                // Rows of the new page cascade in (reveal); the old page just steps aside.
                fadeIn(tween(160, delayMillis = 60)).togetherWith(fadeOut(tween(120)) + scaleOut(tween(200), targetScale = 0.98f))
            },
            label = "page",
        ) { p ->
            RevealPage(key = p) {
                PivotScroll(leadingFraction = 0.32f) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 46.dp, bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (p) {
                            Page.STREAM -> StreamPage(settings)
                            Page.PLAYING -> PlayingPage(settings)
                            Page.CONTROLLER -> ControllerPage(hub)
                            Page.DISPLAY -> DisplayPage()
                            Page.ACCOUNT -> AccountPage(app)
                            Page.ABOUT -> AboutPage()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Intro(text: String, index: Int = 0) {
    BasicText(text, Modifier.reveal(index).padding(start = 2.dp, bottom = 8.dp), style = LocalType.current.body)
}

@Composable
private fun StreamPage(settings: Settings) {
    Intro("These apply to the next game you start. Press A on a setting to see its choices.")
    ChoiceRow(
        "Resolution and frame rate",
        StreamMode.entries.map { it.label },
        settings.mode.ordinal,
        { settings.cycleMode(it - settings.mode.ordinal) },
        Modifier.reveal(1),
        hint = "For 120 fps, set your TV and streaming device to a 120 Hz output first. 4K at 120 fps needs an Ultimate membership.",
    )
    val bi = Settings.BITRATES.indexOf(settings.bitrateMbps).coerceAtLeast(0)
    ChoiceRow(
        "Maximum bitrate",
        Settings.BITRATES.map { "$it Mbps" },
        bi,
        { settings.stepBitrate(it - bi) },
        Modifier.reveal(2),
        hint = "Higher is sharper but needs a faster connection. About 70 % of your measured speed works well.",
    )
    ChoiceRow(
        "Video codec",
        VideoCodec.entries.map { it.label },
        settings.codec.ordinal,
        { settings.cycleCodec(it - settings.codec.ordinal) },
        Modifier.reveal(3),
        hint = "Automatic picks the most efficient codec this device decodes in hardware.",
    )
    ToggleRow(
        "10-bit colour",
        settings.tenBit,
        settings::toggleTenBit,
        Modifier.reveal(4),
        hint = "Smoother gradients and less banding. Uses HEVC or AV1.",
    )
    ToggleRow(
        "Low-latency mode",
        settings.lowLatency,
        settings::toggleLowLatency,
        Modifier.reveal(5),
        hint = "Turns on NVIDIA Reflex in supported games when streaming at 120 fps.",
    )
    ChoiceRow(
        "Game language",
        GameLanguage.entries.map { it.label },
        settings.language.ordinal,
        { settings.cycleLanguage(it - settings.language.ordinal) },
        Modifier.reveal(6),
        hint = "The language games launch in, where they offer it.",
    )
}

@Composable
private fun PlayingPage(settings: Settings) {
    Intro("Everything here can also be changed during a game from the stream menu.")
    ChoiceRow(
        "Performance overlay",
        StatsMode.entries.map { it.label },
        settings.stats.ordinal,
        { settings.setStatsMode(StatsMode.entries[it]) },
        Modifier.reveal(1),
        hint = "Compact shows frame rate, latency and bitrate. Detailed adds decoding, network and session details.",
    )
    ChoiceRow(
        "Overlay position",
        StatsCorner.entries.map { it.label },
        settings.statsCorner.ordinal,
        { settings.cycleCorner(it - settings.statsCorner.ordinal) },
        Modifier.reveal(2),
    )
    val hi = Settings.HOLD_TIMES.indexOf(settings.holdToOpenMs).coerceAtLeast(0)
    ChoiceRow(
        "Hold Start to open the menu",
        Settings.HOLD_TIMES.map { if (it < 1000) "%.2f s".format(it / 1000f).replace(".00", "") else "%.1f s".format(it / 1000f) },
        hi,
        { settings.stepHold(it - hi) },
        Modifier.reveal(3),
        hint = "A quick press of Start still goes to the game. The Guide button and the remote's Back key open the menu too.",
    )
    ChoiceRow(
        "Game volume",
        (0..10).map { "${it * 10} %" },
        settings.volume / 10,
        { settings.stepVolume(it - settings.volume / 10) },
        Modifier.reveal(4),
    )
    val mi = Settings.MOUSE_SPEEDS.indexOfFirst { it >= settings.mouseSpeed - 0.01f }.coerceAtLeast(0)
    ChoiceRow(
        "Mouse speed",
        Settings.MOUSE_SPEEDS.map { "${(it * 100).toInt()} %" },
        mi,
        { settings.stepMouseSpeed(it - mi) },
        Modifier.reveal(5),
        hint = "A USB or Bluetooth mouse is locked to the game while it plays.",
    )
}

@Composable
private fun ControllerPage(hub: PadHub) {
    val pads by hub.pads.collectAsState()
    val type = LocalType.current
    Intro("Every connected controller, live. Press buttons to see them light up. Up to four play at once.")
    if (pads.isEmpty()) {
        Box(Modifier.reveal(1).fillMaxWidth().glass(RoundedCornerShape(12.dp)).padding(16.dp)) {
            BasicText(
                "No controller yet. Pair one over Bluetooth in Android's settings, or plug in a USB controller or its wireless " +
                    "receiver and press a button. If Android asks whether the app may use the device, tick Always and choose OK.",
                style = type.body,
            )
        }
    }
    pads.forEachIndexed { i, p -> Box(Modifier.reveal(1 + i)) { PadCard(p) } }
}

@Composable
private fun PadCard(p: PadInfo) {
    val type = LocalType.current
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(p.name, Modifier.weight(1f), style = type.headline)
            BasicText("${p.route}  ·  ${p.reportsPerSecond} reports/s", style = type.caption)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stick("L", p.state.lx, p.state.ly, p.state.has(Btn.LS))
            Stick("R", p.state.rx, p.state.ry, p.state.has(Btn.RS))
            Trigger("LT", p.state.lt)
            Trigger("RT", p.state.rt)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (b in listOf(Btn.A, Btn.B, Btn.X, Btn.Y, Btn.LB, Btn.RB)) Chip(b, p.state.buttons)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (b in listOf(Btn.UP, Btn.DOWN, Btn.LEFT, Btn.RIGHT, Btn.VIEW, Btn.MENU, Btn.GUIDE)) Chip(b, p.state.buttons)
                }
            }
        }
    }
}

@Composable
private fun Chip(bit: Int, buttons: Int) {
    val on = buttons and bit != 0
    val lit by animateFloatAsState(if (on) 1f else 0f, Motion.press(), label = "chip")
    val name = Btn.names.first { it.first == bit }.second
    Box(
        Modifier
            .graphicsLayer {
                val s = 1f + 0.08f * lit
                scaleX = s
                scaleY = s
            }
            .background(if (on) Palette.white else Palette.glassStrong, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        BasicText(name, style = LocalType.current.caption.copy(color = if (on) Palette.black else Palette.textSecondary))
    }
}

@Composable
private fun Stick(label: String, x: Float, y: Float, pressed: Boolean) {
    val size = 52.dp
    val dot = 11.dp
    Box(Modifier.size(size).border(1.dp, if (pressed) Palette.white else Palette.hairlineStrong, CircleShape)) {
        Box(
            Modifier
                .align(Alignment.Center)
                .offset(x = (size - dot) / 2 * x, y = (size - dot) / 2 * y)
                .size(dot)
                .background(Palette.white, CircleShape),
        )
        BasicText(label, Modifier.align(Alignment.BottomEnd), style = LocalType.current.caption)
    }
}

@Composable
private fun Trigger(label: String, v: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 9.dp, height = 44.dp).background(Palette.glassStrong, RoundedCornerShape(5.dp))) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(v.coerceIn(0f, 1f))
                    .background(Palette.white, RoundedCornerShape(5.dp)),
            )
        }
        Spacer(Modifier.height(4.dp))
        BasicText(label, style = LocalType.current.caption)
    }
}

@Composable
private fun DisplayPage() {
    val display = LocalView.current.display
    val type = LocalType.current
    val current = display?.mode
    val best = display?.supportedModes?.maxWithOrNull(compareBy({ it.refreshRate }, { it.physicalHeight }))
    Intro("Android apps play at the output mode your device is set to, so pick the resolution and refresh rate in its display settings.")
    Column(Modifier.reveal(1).fillMaxWidth().glass(RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(
            "Output now: " + (current?.let { "${it.physicalWidth} × ${it.physicalHeight} at ${"%.0f".format(it.refreshRate)} Hz" } ?: "unknown"),
            style = type.headline,
        )
        if (best != null && current != null && best.refreshRate > current.refreshRate + 1) {
            BasicText(
                "Your display also offers ${best.physicalWidth} × ${best.physicalHeight} at ${"%.0f".format(best.refreshRate)} Hz. Switch to it before streaming at that frame rate.",
                style = type.body,
            )
        }
        BasicText("The interface is drawn at the full output resolution, so a 4K output gets a 4K interface.", style = type.caption)
    }
    val decoders = listOf("HEVC" to TvDecoderFactory.MIME_HEVC, "AV1" to TvDecoderFactory.MIME_AV1, "H.264" to TvDecoderFactory.MIME_AVC)
        .filter { TvDecoderFactory.find(it.second) != null }
        .map { it.first }
    Column(Modifier.reveal(2).fillMaxWidth().glass(RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText("Hardware video decoding", style = type.headline)
        BasicText(
            if (decoders.isEmpty()) "No hardware video decoder was found, so streaming may not work on this device."
            else "This device decodes ${decoders.joinToString(", ")} in hardware. Video goes straight from the decoder to the screen in low-latency mode.",
            style = type.body,
        )
    }
}

@Composable
private fun AccountPage(app: AppState) {
    val type = LocalType.current
    val p = app.profile
    if (p == null) {
        Intro("You are not signed in. Go to Home to sign in with your phone.")
        return
    }
    Intro("Signed in to GeForce NOW.")
    Column(Modifier.reveal(1).fillMaxWidth().glass(RoundedCornerShape(12.dp)).padding(16.dp)) {
        BasicText(p.name, style = type.headline)
        if (p.email != null) BasicText(p.email, style = type.body)
        Spacer(Modifier.height(4.dp))
        BasicText("${app.library.size} games in your library", style = type.caption)
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.reveal(2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassButton("Refresh library", app::refresh, icon = Icons.Rounded.Refresh)
        GlassButton("Sign out", app::signOut, icon = Icons.Rounded.ExitToApp)
    }
}

@Composable
private fun AboutPage() {
    val type = LocalType.current
    Intro("Cloudlight ${BuildConfig.VERSION_NAME}, a GeForce NOW client for Android TV.")
    Column(Modifier.reveal(1).fillMaxWidth().glass(RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText("Android ${Build.VERSION.RELEASE} on ${Build.MANUFACTURER} ${Build.MODEL}", style = type.bodyStrong)
        BasicText("Streaming uses WebRTC, the same technology as GeForce NOW in a browser.", style = type.caption)
        BasicText("Cloudlight is not affiliated with NVIDIA. GeForce NOW is a trademark of NVIDIA Corporation.", style = type.caption)
        BasicText("Inter typeface by Rasmus Andersson, SIL Open Font License 1.1.", style = type.caption)
    }
}
