package app.cloudlight.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.cloudlight.tv.Settings
import app.cloudlight.tv.input.NavAction
import app.cloudlight.tv.input.PadHub
import app.cloudlight.tv.ui.components.Pressable
import app.cloudlight.tv.ui.motion.Motion
import app.cloudlight.tv.ui.theme.LocalType
import app.cloudlight.tv.ui.theme.Palette
import kotlinx.coroutines.delay

enum class Tab(val label: String) { HOME("Home"), LIBRARY("Library"), SETTINGS("Settings") }

@Composable
fun AppRoot(hub: PadHub, settings: Settings, onExit: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var previous by remember { mutableIntStateOf(0) }
    val tabFocus = remember { Tab.entries.associateWith { FocusRequester() } }

    fun select(t: Tab) {
        if (t == tab) return
        previous = tab.ordinal
        tab = t
    }

    // Shoulder buttons flip between tabs from anywhere in the menus.
    LaunchedEffect(hub) {
        hub.actions.collect { a ->
            when (a) {
                NavAction.TAB_PREV, NavAction.TAB_NEXT -> {
                    val step = if (a == NavAction.TAB_NEXT) 1 else -1
                    val t = Tab.entries[(tab.ordinal + step).coerceIn(0, Tab.entries.lastIndex)]
                    select(t)
                    // Focus follows, so it never stays behind on a screen that is leaving.
                    runCatching { tabFocus.getValue(t).requestFocus() }
                }
                else -> Unit
            }
        }
    }

    // Back walks home first, then leaves the app.
    BackHandler(enabled = tab != Tab.HOME) {
        select(Tab.HOME)
        runCatching { tabFocus.getValue(Tab.HOME).requestFocus() }
    }
    BackHandler(enabled = tab == Tab.HOME, onBack = onExit)

    // The whole app rises in once on launch rather than popping onto the screen.
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, Motion.page()) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.ink)
            .background(Palette.backdrop),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = intro.value
                    translationY = (1f - intro.value) * 40.dp.toPx()
                    val s = 0.97f + 0.03f * intro.value
                    scaleX = s
                    scaleY = s
                },
        ) {
            TopBar(tab, tabFocus, ::select)
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val forward = targetState.ordinal > previous
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(Motion.pageOffset) { it / 8 * dir } + fadeIn(Motion.page()) + scaleIn(Motion.page(), initialScale = 0.98f))
                        .togetherWith(slideOutHorizontally(Motion.pageOffset) { -it / 10 * dir } + fadeOut(Motion.fade()) + scaleOut(Motion.fade(), targetScale = 0.98f))
                },
                label = "tab",
            ) { t ->
                when (t) {
                    Tab.HOME -> HomeScreen(settings, onOpenSettings = { select(Tab.SETTINGS) })
                    Tab.LIBRARY -> LibraryScreen()
                    Tab.SETTINGS -> SettingsScreen(hub, settings)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { tabFocus.getValue(Tab.HOME).requestFocus() }
    }
}

@Composable
private fun TopBar(tab: Tab, focus: Map<Tab, FocusRequester>, onSelect: (Tab) -> Unit) {
    val type = LocalType.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 64.dp, end = 64.dp, top = 36.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Wordmark()
        Spacer(Modifier.width(48.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in Tab.entries) {
                val selected = t == tab
                val dot by animateFloatAsState(if (selected) 1f else 0f, Motion.focus(), label = "dot")
                Pressable(
                    onClick = { onSelect(t) },
                    shape = RoundedCornerShape(50),
                    focusedScale = 1.05f,
                    color = Palette.ink.copy(alpha = 0f),
                    focusedColor = Palette.surfaceHigh,
                    focusRequester = focus.getValue(t),
                ) { focused ->
                    // Moving focus onto a tab selects it, like a TV home screen.
                    LaunchedEffect(focused) { if (focused) onSelect(t) }
                    val fg by animateColorAsState(
                        if (selected || focused) Palette.text else Palette.textDim, Motion.fade(), label = "tabfg",
                    )
                    Column(
                        Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BasicText(t.label, style = type.label.copy(color = fg))
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier
                                .size(width = 18.dp, height = 3.dp)
                                .graphicsLayer {
                                    scaleX = dot
                                    alpha = dot
                                }
                                .background(Palette.glow, RoundedCornerShape(50)),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        BasicText("LB  ·  RB to switch", style = type.caption)
    }
}

@Composable
fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(14.dp)
                .background(Palette.glow, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        BasicText("cloudlight", style = LocalType.current.heading.copy(color = Palette.text))
    }
}
