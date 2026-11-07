package app.cloudlight.tv.ui.stream

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.cloudlight.tv.Settings
import app.cloudlight.tv.StatsCorner
import app.cloudlight.tv.StatsMode
import app.cloudlight.tv.input.NavAction
import app.cloudlight.tv.input.PadHub
import app.cloudlight.tv.stream.Keymap
import app.cloudlight.tv.stream.Streamer
import app.cloudlight.tv.ui.components.ActionRow
import app.cloudlight.tv.ui.components.ChoiceRow
import app.cloudlight.tv.ui.components.GameLogo
import app.cloudlight.tv.ui.components.GlassButton
import app.cloudlight.tv.ui.components.PivotScroll
import app.cloudlight.tv.ui.components.Pressable
import app.cloudlight.tv.ui.components.SectionLabel
import app.cloudlight.tv.ui.components.ToggleRow
import app.cloudlight.tv.ui.components.onFocusColor
import app.cloudlight.tv.ui.motion.Motion
import app.cloudlight.tv.ui.motion.RevealPage
import app.cloudlight.tv.ui.motion.reveal
import app.cloudlight.tv.ui.theme.LocalType
import app.cloudlight.tv.ui.theme.Palette
import kotlinx.coroutines.delay

/**
 * The in-stream menu, over the running game: stream and sound controls, mouse and keyboard
 * helpers, and the ways out. The game keeps running behind it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StreamMenu(streamer: Streamer, settings: Settings, hub: PadHub) {
    val type = LocalType.current
    val resume = remember { FocusRequester() }
    var confirmEnd by remember { mutableStateOf(false) }
    val slide = remember { Animatable(0f) }
    val px = with(LocalDensity.current) { 40.dp.toPx() }

    BackHandler(onBack = streamer::closeMenu)
    LaunchedEffect(Unit) {
        slide.animateTo(1f, Motion.springOf(0.5f, 0.86f))
    }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { resume.requestFocus() }
    }
    // Start closes the menu again.
    LaunchedEffect(hub) {
        hub.actions.collect { if (it == NavAction.MENU) streamer.closeMenu() }
    }
    LaunchedEffect(confirmEnd) {
        if (confirmEnd) {
            delay(4000)
            confirmEnd = false
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.82f), Color.Black.copy(alpha = 0.35f))))) {
        RevealPage(key = "menu") {
            Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 30.dp)) {
                Column(
                    Modifier
                        .width(380.dp)
                        .fillMaxHeight()
                        .graphicsLayer {
                            alpha = slide.value.coerceIn(0f, 1f)
                            translationX = (1f - slide.value) * -px
                        },
                ) {
                    val g = streamer.game
                    Row(Modifier.reveal(0), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.heightIn(max = 40.dp).widthIn(max = 220.dp)) {
                            GameLogo(g?.logo, g?.title ?: "Game", Modifier.heightIn(max = 40.dp).widthIn(max = 220.dp), style = type.title)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.reveal(1), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(qualityColor(streamer.stats.quality), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        val s = streamer.stats
                        BasicText(
                            listOfNotNull(
                                "Playing for ${elapsed(streamer.liveSince)}",
                                s.rttMs?.let { "%.0f ms".format(it) },
                                s.fps?.let { "%.0f fps".format(it) },
                            ).joinToString("  ·  "),
                            style = type.caption,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    PivotScroll(leadingFraction = 0.35f) {
                        Column(
                            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).focusGroup().padding(bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(Modifier.reveal(2).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GlassButton("Resume", streamer::closeMenu, icon = Icons.Rounded.PlayArrow, primary = true, focusRequester = resume)
                                GlassButton("Leave running", streamer::leave, icon = Icons.Rounded.ExitToApp)
                            }

                            SectionLabel("Stream", Modifier.reveal(3))
                            ChoiceRow(
                                "Performance overlay",
                                StatsMode.entries.map { it.label },
                                settings.stats.ordinal,
                                { settings.setStatsMode(StatsMode.entries[it]) },
                                Modifier.reveal(4),
                                hint = "Frame rate, latency and bitrate, or the full breakdown.",
                            )
                            ChoiceRow(
                                "Overlay position",
                                StatsCorner.entries.map { it.label },
                                settings.statsCorner.ordinal,
                                { settings.cycleCorner(it - settings.statsCorner.ordinal) },
                                Modifier.reveal(5),
                            )
                            ActionRow(
                                "Refresh the picture",
                                streamer::refreshPicture,
                                Modifier.reveal(6),
                                hint = "Asks the rig for a clean frame if the image looks broken.",
                                icon = Icons.Rounded.Refresh,
                            )

                            SectionLabel("Sound", Modifier.reveal(7))
                            ChoiceRow(
                                "Game volume",
                                (0..10).map { "${it * 10} %" },
                                settings.volume / 10,
                                { streamer.stepVolume(it - settings.volume / 10) },
                                Modifier.reveal(8),
                            )
                            ToggleRow("Mute", streamer.muted, streamer::toggleMute, Modifier.reveal(9))

                            SectionLabel("Controls", Modifier.reveal(10))
                            ToggleRow(
                                "Use the controller as a mouse",
                                streamer.controllerMouse,
                                streamer::toggleControllerMouse,
                                Modifier.reveal(11),
                                hint = "Left stick moves the pointer, A clicks, B right-clicks, right stick scrolls.",
                            )
                            ChoiceRow(
                                "Mouse speed",
                                Settings.MOUSE_SPEEDS.map { "${(it * 100).toInt()} %" },
                                Settings.MOUSE_SPEEDS.indexOfFirst { it >= settings.mouseSpeed - 0.01f }.coerceAtLeast(0),
                                { i ->
                                    val cur = Settings.MOUSE_SPEEDS.indexOfFirst { it >= settings.mouseSpeed - 0.01f }.coerceAtLeast(0)
                                    streamer.stepMouseSpeed(i - cur)
                                },
                                Modifier.reveal(12),
                                hint = "For a real mouse and for the controller mouse.",
                            )

                            SectionLabel("Keyboard", Modifier.reveal(13))
                            FlowRow(
                                Modifier.reveal(14).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                KeyChip("Esc") { streamer.sendCombo(Keymap.ESCAPE) }
                                KeyChip("Enter") { streamer.sendCombo(Keymap.ENTER) }
                                KeyChip("Tab") { streamer.sendCombo(Keymap.TAB) }
                                KeyChip("Alt + Tab") { streamer.sendCombo(Keymap.ALT, Keymap.TAB) }
                                KeyChip("Alt + Enter") { streamer.sendCombo(Keymap.ALT, Keymap.ENTER) }
                                KeyChip("Windows") { streamer.sendCombo(Keymap.WIN) }
                                KeyChip("F11") { streamer.sendCombo(Keymap.F11) }
                            }

                            SectionLabel("Session", Modifier.reveal(15))
                            ActionRow(
                                "Leave the game running",
                                streamer::leave,
                                Modifier.reveal(16),
                                hint = "Back to Home. The game stays on the rig for a few minutes, ready to resume.",
                                icon = Icons.Rounded.ExitToApp,
                            )
                            ActionRow(
                                if (confirmEnd) "Press again to end the session" else "End session",
                                { if (confirmEnd) streamer.quit() else confirmEnd = true },
                                Modifier.reveal(17),
                                hint = "Closes the game on the rig. Progress the game hasn't saved is lost.",
                                icon = Icons.Rounded.Close,
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                SessionCard(streamer, Modifier.reveal(3).graphicsLayer { alpha = slide.value.coerceIn(0f, 1f) })
            }
        }
    }
}

@Composable
private fun KeyChip(label: String, onClick: () -> Unit) {
    Pressable(onClick, shape = RoundedCornerShape(10.dp), focusedScale = 1.08f) { focused ->
        BasicText(label, Modifier.padding(horizontal = 13.dp, vertical = 8.dp), style = LocalType.current.label.copy(color = onFocusColor(focused)))
    }
}

@Composable
private fun SessionCard(streamer: Streamer, modifier: Modifier) {
    val type = LocalType.current
    val s = streamer.stats
    val session = streamer.session
    Column(
        modifier.width(240.dp).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText("THIS SESSION", style = type.overline)
        Spacer(Modifier.height(4.dp))
        Line("Picture", if (s.width > 0) "${s.width} × ${s.height}" else session?.let { "${it.width ?: "–"} × ${it.height ?: "–"}" } ?: "–")
        Line("Frame rate", s.fps?.let { "%.0f fps".format(it) } ?: "–")
        Line("Codec", s.codec ?: "–")
        Line("Bitrate", s.bitrateMbps?.let { "%.1f Mbps".format(it) } ?: "–")
        Line("Latency", s.rttMs?.let { "%.0f ms".format(it) } ?: "–")
        Line("Packet loss", s.packetLossPct?.let { "%.2f %%".format(it) } ?: "–")
        Line("Connection", s.quality.label)
        Line("Rig", session?.gpu ?: "–")
    }
}

@Composable
private fun Line(label: String, value: String) {
    val type = LocalType.current
    Row(Modifier.fillMaxWidth()) {
        BasicText(label, Modifier.weight(1f), style = type.caption)
        BasicText(value, style = type.caption.copy(color = Palette.text, fontFeatureSettings = "tnum"))
    }
}
