package app.cloudlight.tv.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import app.cloudlight.tv.BuildConfig
import app.cloudlight.tv.Settings
import app.cloudlight.tv.input.Btn
import app.cloudlight.tv.input.PadHub
import app.cloudlight.tv.input.PadInfo
import app.cloudlight.tv.ui.components.Pressable
import app.cloudlight.tv.ui.motion.Motion
import app.cloudlight.tv.ui.theme.LocalType
import app.cloudlight.tv.ui.theme.Palette

private enum class Section(val label: String) { STREAM("Stream"), CONTROLLER("Controller"), DISPLAY("Display"), ABOUT("About") }

@Composable
fun SettingsScreen(hub: PadHub, settings: Settings) {
    var section by remember { mutableStateOf(Section.STREAM) }
    Row(
        Modifier
            .fillMaxSize()
            .padding(start = 64.dp, end = 64.dp, top = 24.dp, bottom = 48.dp),
    ) {
        Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (s in Section.entries) {
                val selected = s == section
                Pressable(
                    onClick = { section = s },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    focusedScale = 1.03f,
                    color = if (selected) Palette.surface else Palette.ink.copy(alpha = 0f),
                ) { focused ->
                    // Focus alone switches the panel, so scrolling the list previews each one.
                    androidx.compose.runtime.LaunchedEffect(focused) { if (focused) section = s }
                    val fg by animateColorAsState(if (selected || focused) Palette.text else Palette.textDim, Motion.fade(), label = "fg")
                    BasicText(s.label, Modifier.padding(horizontal = 20.dp, vertical = 16.dp), style = LocalType.current.label.copy(color = fg))
                }
            }
        }
        Spacer(Modifier.width(40.dp))
        AnimatedContent(
            targetState = section,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val down = targetState.ordinal > initialState.ordinal
                (slideInVertically(Motion.pageOffset) { (if (down) it else -it) / 12 } + fadeIn(Motion.page()))
                    .togetherWith(slideOutVertically(Motion.pageOffset) { (if (down) -it else it) / 16 } + fadeOut(Motion.fade()))
            },
            label = "section",
        ) { s ->
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (s) {
                    Section.STREAM -> StreamPanel(settings)
                    Section.CONTROLLER -> ControllerPanel(hub)
                    Section.DISPLAY -> DisplayPanel()
                    Section.ABOUT -> AboutPanel()
                }
            }
        }
    }
}

@Composable
private fun PanelTitle(title: String, body: String) {
    val type = LocalType.current
    BasicText(title, style = type.title)
    BasicText(body, Modifier.padding(bottom = 10.dp), style = type.body)
}

@Composable
private fun ValueRow(label: String, value: String, hint: String, onClick: () -> Unit) {
    val type = LocalType.current
    Pressable(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), focusedScale = 1.02f) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(label, style = type.label)
                BasicText(hint, style = type.caption)
            }
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (slideInVertically(Motion.pageOffset) { it / 2 } + fadeIn(Motion.fade()))
                        .togetherWith(slideOutVertically(Motion.pageOffset) { -it / 2 } + fadeOut(Motion.fade()))
                },
                label = "value",
            ) { v -> BasicText(v, style = type.label.copy(color = Palette.lavender)) }
        }
    }
}

@Composable
private fun StreamPanel(settings: Settings) {
    PanelTitle("Stream", "Both modes use HEVC, the codec this box decodes fastest. Press A to change a value.")
    ValueRow("Resolution and frame rate", settings.mode.label, "1080p 120 needs the Zidoo set to 1080p 120 Hz", { settings.cycleMode(1) })
    ValueRow("Bitrate", "${settings.bitrateMbps} Mbps", "Your Ethernet link reports 100 Mbps; stay at or below it", { settings.stepBitrate(1) })
    ValueRow("Performance overlay", if (settings.showStats) "On" else "Off", "Shows latency, frame rate and bitrate while streaming", { settings.toggleStats() })
}

@Composable
private fun ControllerPanel(hub: PadHub) {
    val pads by hub.pads.collectAsState()
    val type = LocalType.current
    PanelTitle("Controller", "Every controller Cloudlight can hear, live. Press buttons to see them light up.")
    if (pads.isEmpty()) {
        Box(Modifier.fillMaxWidth().background(Palette.surface, RoundedCornerShape(16.dp)).padding(24.dp)) {
            BasicText(
                "No controller heard yet. Plug in the 8BitDo dongle, switch the controller on in 2.4G mode and press a button. " +
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
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.surface, RoundedCornerShape(18.dp))
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(p.name, Modifier.weight(1f), style = type.heading)
            BasicText("${p.route} · ${p.reportsPerSecond} reports/s", style = type.caption)
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Stick("L", p.state.lx, p.state.ly, p.state.has(Btn.LS))
            Stick("R", p.state.rx, p.state.ry, p.state.has(Btn.RS))
            Trigger("LT", p.state.lt)
            Trigger("RT", p.state.rt)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (b in listOf(Btn.A, Btn.B, Btn.X, Btn.Y, Btn.LB, Btn.RB)) Chip(b, p.state.buttons)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            .background(if (on) Palette.lavender else Palette.surfaceHigh, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        BasicText(name, style = LocalType.current.caption.copy(color = if (on) Palette.ink else Palette.textDim))
    }
}

@Composable
private fun Stick(label: String, x: Float, y: Float, pressed: Boolean) {
    val size = 84.dp
    val dot = 18.dp
    Box(
        Modifier
            .size(size)
            .border(2.dp, if (pressed) Palette.lavender else Palette.line, CircleShape),
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .offset(x = (size - dot) / 2 * x, y = (size - dot) / 2 * y)
                .size(dot)
                .background(Palette.glow, CircleShape),
        )
        BasicText(label, Modifier.align(Alignment.BottomEnd), style = LocalType.current.caption)
    }
}

@Composable
private fun Trigger(label: String, v: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = 16.dp, height = 72.dp)
                .background(Palette.surfaceHigh, RoundedCornerShape(8.dp)),
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(v.coerceIn(0f, 1f))
                    .background(Palette.glow, RoundedCornerShape(8.dp)),
            )
        }
        Spacer(Modifier.height(6.dp))
        BasicText(label, style = LocalType.current.caption)
    }
}

@Composable
private fun DisplayPanel() {
    val view = LocalView.current
    val display = view.display
    val type = LocalType.current
    val modes = display?.supportedModes?.toList().orEmpty()
    val current = display?.mode
    val has120 = modes.any { it.physicalWidth == 1920 && it.refreshRate > 119f }
    PanelTitle("Display", "Android only lets apps use the output mode the Zidoo is set to, so 120 fps needs it set in the Zidoo's own display settings.")
    Column(
        Modifier.fillMaxWidth().background(Palette.surface, RoundedCornerShape(16.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(
            "Now: " + (current?.let { "${it.physicalWidth}×${it.physicalHeight} at ${"%.0f".format(it.refreshRate)} Hz" } ?: "unknown"),
            style = type.label,
        )
        BasicText(
            if (has120) "1080p 120 Hz is active, so 1080p 120 streams play at full rate."
            else "1080p 120 Hz is not active. Set it in the Zidoo's display settings before streaming at 120 fps.",
            style = type.body.copy(color = if (has120) Palette.good else Palette.warn),
        )
    }
}

@Composable
private fun AboutPanel() {
    PanelTitle("About", "Cloudlight ${BuildConfig.VERSION_NAME} for Zidoo Z30 Pro. Android ${Build.VERSION.RELEASE}, ${Build.MODEL}.")
}
