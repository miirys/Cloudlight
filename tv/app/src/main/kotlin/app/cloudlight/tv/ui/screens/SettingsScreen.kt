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
import androidx.compose.ui.focus.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import app.cloudlight.tv.BuildConfig
import app.cloudlight.tv.Settings
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
    STREAM("Stream"), CONTROLLER("Controllers"), DISPLAY("Display"), ACCOUNT("Account"), ABOUT("About")
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(hub: PadHub, app: AppState, settings: Settings) {
    val type = LocalType.current
    var page by remember { mutableStateOf(Page.STREAM) }
    val pageFocus = remember { Page.entries.associateWith { FocusRequester() } }
    Row(Modifier.fillMaxSize().padding(start = 40.dp, end = 48.dp, top = 34.dp, bottom = 34.dp)) {
        Column(
            Modifier
                .width(200.dp)
                // Coming back from a panel always lands on the open page, so it never
                // switches pages by accident.
                .focusProperties { enter = { pageFocus.getValue(page) } }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("Settings", Modifier.padding(start = 12.dp, bottom = 16.dp), style = type.title)
            for (p in Page.entries) {
                val selected = p == page
                Pressable(
                    onClick = { page = p },
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    shape = RoundedCornerShape(10.dp),
                    focusedScale = 1.03f,
                    color = if (selected) Palette.glassStrong else Color.Transparent,
                    border = false,
                    focusRequester = pageFocus.getValue(p),
                ) { focused ->
                    LaunchedEffect(focused) { if (focused) page = p }
                    val fg = onFocusColor(focused, if (selected) Palette.text else Palette.textSecondary)
                    BasicText(p.label, Modifier.fillMaxWidth().padding(horizontal = 12.dp), style = type.label.copy(color = fg))
                }
            }
        }
        Spacer(Modifier.width(28.dp))
        AnimatedContent(
            targetState = page,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val down = targetState.ordinal > initialState.ordinal
                (slideInVertically(Motion.pageOffset) { (if (down) it else -it) / 16 } + fadeIn(Motion.page()))
                    .togetherWith(slideOutVertically(Motion.pageOffset) { (if (down) -it else it) / 20 } + fadeOut(Motion.fade()))
            },
            label = "page",
        ) { p ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 46.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (p) {
                    Page.STREAM -> StreamPage(settings)
                    Page.CONTROLLER -> ControllerPage(hub)
                    Page.DISPLAY -> DisplayPage()
                    Page.ACCOUNT -> AccountPage(app)
                    Page.ABOUT -> AboutPage()
                }
            }
        }
    }
}

@Composable
private fun Intro(text: String) {
    BasicText(text, Modifier.padding(start = 2.dp, bottom = 8.dp), style = LocalType.current.body)
}

@Composable
private fun ValueRow(label: String, value: String, hint: String, onClick: () -> Unit) {
    val type = LocalType.current
    Pressable(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), focusedScale = 1.02f) { focused ->
        val fg = onFocusColor(focused)
        val sub = onFocusColor(focused, Palette.textSecondary)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(label, style = type.label.copy(color = fg))
                Spacer(Modifier.height(2.dp))
                BasicText(hint, style = type.caption.copy(color = sub))
            }
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (slideInVertically(Motion.pageOffset) { it / 2 } + fadeIn(Motion.fade()))
                        .togetherWith(slideOutVertically(Motion.pageOffset) { -it / 2 } + fadeOut(Motion.fade()))
                },
                label = "value",
            ) { v -> BasicText(v, style = type.label.copy(color = sub)) }
        }
    }
}

@Composable
private fun StreamPage(settings: Settings) {
    Intro("Both modes use HEVC, the codec this box decodes fastest. Press A to change a value.")
    ValueRow("Resolution and frame rate", settings.mode.label, "1080p at 120 fps needs the Zidoo set to 1080p 120 Hz.") { settings.cycleMode(1) }
    ValueRow("Bitrate", "${settings.bitrateMbps} Mbps", "Your Ethernet link reports 100 Mbps, so stay at or below that.") { settings.stepBitrate(1) }
    ValueRow("Performance overlay", if (settings.showStats) "On" else "Off", "Shows latency, frame rate and bitrate while streaming.") { settings.toggleStats() }
}

@Composable
private fun ControllerPage(hub: PadHub) {
    val pads by hub.pads.collectAsState()
    val type = LocalType.current
    Intro("Every controller Cloudlight can hear, live. Press buttons to see them light up.")
    if (pads.isEmpty()) {
        Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(12.dp)).padding(16.dp)) {
            BasicText(
                "No controller yet. Plug in the 8BitDo dongle, switch the controller on in 2.4G mode and press a button. " +
                    "If Android asks whether Cloudlight may use the device, tick Always and choose OK.",
                style = type.body,
            )
        }
    }
    for (p in pads) PadCard(p)
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
    val is120 = current != null && current.physicalHeight == 1080 && current.refreshRate > 119f
    Intro("Android only lets apps use the output mode the Zidoo is set to, so 120 fps has to be chosen in the Zidoo's own display settings.")
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(
            "Output: " + (current?.let { "${it.physicalWidth} × ${it.physicalHeight} at ${"%.0f".format(it.refreshRate)} Hz" } ?: "unknown"),
            style = type.headline,
        )
        BasicText(
            if (is120) "1080p at 120 Hz is active, so 1080p 120 fps streams play at their full rate."
            else "1080p at 120 Hz is not active. Choose it in the Zidoo's display settings before streaming at 120 fps.",
            style = type.body,
        )
        BasicText("The interface is drawn at the full output resolution, so 4K output means a 4K interface.", style = type.caption)
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
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(12.dp)).padding(16.dp)) {
        BasicText(p.name, style = type.headline)
        if (p.email != null) BasicText(p.email, style = type.body)
        Spacer(Modifier.height(4.dp))
        BasicText("${app.library.size} games in your library", style = type.caption)
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassButton("Refresh library", app::refresh, icon = Icons.Rounded.Refresh)
        GlassButton("Sign out", app::signOut, icon = Icons.Rounded.ExitToApp)
    }
}

@Composable
private fun AboutPage() {
    val type = LocalType.current
    Intro("Cloudlight ${BuildConfig.VERSION_NAME}, made for the Zidoo Z30 Pro.")
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText("Android ${Build.VERSION.RELEASE} on ${Build.MANUFACTURER} ${Build.MODEL}", style = type.bodyStrong)
        BasicText("Cloudlight is not affiliated with NVIDIA. GeForce NOW is a trademark of NVIDIA Corporation.", style = type.caption)
        BasicText("Inter typeface by Rasmus Andersson, SIL Open Font License 1.1.", style = type.caption)
    }
}
