package app.cloudlight.tv.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import app.cloudlight.tv.GameLanguage
import app.cloudlight.tv.Settings
import app.cloudlight.tv.StatsCorner
import app.cloudlight.tv.StatsMode
import app.cloudlight.tv.StreamMode
import app.cloudlight.tv.VideoCodec
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
                        onClick = { page = p },
                        modifier = Modifier.fillMaxWidth().height(PageItemHeight),
                        shape = RoundedCornerShape(50),
                        focusedScale = 1.03f,
                        color = Color.Transparent,
                        border = false,
                        focusRequester = pageFocus.getValue(p),
                    ) { focused ->
                        LaunchedEffect(focused) { if (focused) page = p }
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
                fadeIn(tween(160, delayMillis = 60)).togetherWith(fadeOut(tween(110)) + scaleOut(tween(180), targetScale = 0.985f))
            },
            label = "page",
        ) { p ->
            RevealPage(key = p) {
                when (p) {
                    Page.STREAM -> ListPage { StreamPage(settings) }
                    Page.GAME -> ListPage { GamePage(settings) }
                    Page.CONTROLLERS -> ControllerPage(hub)
                    Page.ACCOUNT -> ListPage { AccountPage(app) }
                    Page.ABOUT -> AboutPage(app)
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
                    SettingsList(focus, Modifier.reveal(1)) { content() }
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

private fun gbPerHour(mbps: Int) = mbps * 3600 / 8 / 1000.0

@Composable
private fun StreamPage(settings: Settings) {
    SettingsGroup("Picture")
    ValueSetting(
        "Resolution",
        StreamMode.entries.map { it.label },
        settings.mode.ordinal,
        { settings.cycleMode(it - settings.mode.ordinal) },
        hint = "Set your TV to 120 Hz to stream at 120 fps. 4K at 120 fps needs Ultimate.",
    )
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
    SwitchSetting("10-bit colour", settings.tenBit, settings::toggleTenBit, hint = "Smoother skies and shadows. Needs HEVC or AV1.")
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
private fun ControllerPage(hub: PadHub) {
    val pads by hub.pads.collectAsState()
    val type = LocalType.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (pads.isEmpty()) {
            Column(Modifier.reveal(1).padding(top = 18.dp, start = 4.dp)) {
                BasicText("No controllers connected", style = type.title)
                Spacer(Modifier.height(8.dp))
                BasicText("Pair one in your TV's Bluetooth settings, or plug in a USB controller or its receiver.", style = type.body)
            }
        } else {
            BasicText(
                if (pads.size == 1) "1 controller" else "${pads.size} controllers",
                Modifier.reveal(1).padding(start = 4.dp, top = 18.dp),
                style = type.caption.copy(color = Palette.textTertiary),
            )
        }
        pads.forEachIndexed { i, p -> Box(Modifier.reveal(2 + i)) { PadView(i + 1, p) } }
    }
}

/** A live picture of one controller: sticks, triggers and buttons light up as you press them. */
@Composable
private fun PadView(player: Int, p: PadInfo) {
    val type = LocalType.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).background(Palette.white, CircleShape), contentAlignment = Alignment.Center) {
                BasicText("$player", style = type.label.copy(color = Palette.black))
            }
            Spacer(Modifier.width(10.dp))
            BasicText(p.name, Modifier.weight(1f), style = type.headline, maxLines = 1)
            BasicText(p.route, style = type.caption)
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
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
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(0.75.dp).background(Palette.hairline))
    }
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
        Box(Modifier.size(48.dp).clip(CircleShape).background(Palette.glassStrong), contentAlignment = Alignment.Center) {
            if (p.avatar != null) GameArt(p.avatar, "", Modifier.fillMaxSize())
            else BasicText(p.name.take(1).uppercase(), style = type.title)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            BasicText(p.name, style = type.title)
            BasicText(listOfNotNull(p.email, p.tier).joinToString("  ·  "), style = type.body)
        }
    }
    ActionSetting("Refresh library", app::refresh, value = "${app.library.size} games", hint = "Picks up games you added in another app.")
    ActionSetting("Sign out", app::signOut, hint = "You can sign back in with your phone.")
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

