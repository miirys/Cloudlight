package app.cloudlight.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.cloudlight.tv.Settings
import app.cloudlight.tv.data.AppState
import app.cloudlight.tv.data.Game
import app.cloudlight.tv.input.NavAction
import app.cloudlight.tv.input.PadHub
import app.cloudlight.tv.stream.Streamer
import app.cloudlight.tv.ui.components.Backdrop
import app.cloudlight.tv.ui.components.GameArt
import app.cloudlight.tv.ui.components.Pressable
import app.cloudlight.tv.ui.components.onFocusColor
import app.cloudlight.tv.ui.motion.Motion
import app.cloudlight.tv.ui.motion.RevealPage
import app.cloudlight.tv.ui.stream.StreamScreen
import app.cloudlight.tv.ui.theme.LocalType
import app.cloudlight.tv.ui.theme.Palette
import kotlinx.coroutines.delay

enum class Section(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    SEARCH("Search", Icons.Rounded.Search),
    LIBRARY("Library", Icons.Rounded.List),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

/** Width of the collapsed side rail; content always starts after it. */
val RailWidth = 64.dp

private val RailItemHeight = 36.dp
private val RailItemGap = 6.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppRoot(hub: PadHub, app: AppState, settings: Settings, streamer: Streamer, onExit: () -> Unit) {
    var section by remember { mutableStateOf(Section.HOME) }
    var detail by remember { mutableStateOf<Game?>(null) }
    var backdrop by remember { mutableStateOf<String?>(null) }
    val railFocus = remember { Section.entries.associateWith { FocusRequester() } }
    val streaming = streamer.active

    fun go(s: Section) {
        if (s != section) section = s
    }

    fun play(g: Game) {
        detail = null
        streamer.play(g)
    }

    // LB and RB step through the sections from anywhere; focus moves with them so it never
    // stays behind on a screen that is leaving.
    LaunchedEffect(hub) {
        hub.actions.collect { a ->
            if (detail != null || streamer.active) return@collect
            val step = when (a) {
                NavAction.TAB_PREV -> -1
                NavAction.TAB_NEXT -> 1
                else -> return@collect
            }
            val s = Section.entries[(section.ordinal + step).coerceIn(0, Section.entries.lastIndex)]
            go(s)
            runCatching { railFocus.getValue(s).requestFocus() }
        }
    }

    BackHandler(enabled = !streaming && detail == null && section != Section.HOME) {
        go(Section.HOME)
        runCatching { railFocus.getValue(Section.HOME).requestFocus() }
    }
    BackHandler(enabled = !streaming && detail == null && section == Section.HOME, onBack = onExit)

    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, Motion.page()) }

    Box(Modifier.fillMaxSize().background(Palette.base)) {
        if (!streaming) {
            Backdrop(if (app.signedIn) backdrop else null)
            if (!app.signedIn) SignInBackground()

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = intro.value
                        val s = 0.98f + 0.02f * intro.value
                        scaleX = s
                        scaleY = s
                    },
            ) {
                // Content first, so the rail draws over it when it opens.
                Box(Modifier.fillMaxSize().padding(start = RailWidth)) {
                    AnimatedContent(
                        targetState = if (!app.signedIn && section != Section.SETTINGS) null else section,
                        transitionSpec = {
                            // The old page steps back and fades; the new one builds itself up
                            // part by part (see reveal), so only a short cross-fade is needed here.
                            (fadeIn(tween(220, delayMillis = 70)))
                                .togetherWith(fadeOut(tween(150)) + scaleOut(tween(220), targetScale = 0.975f))
                        },
                        label = "section",
                    ) { s ->
                        RevealPage(key = s) {
                            when (s) {
                                null -> SignInScreen(app)
                                Section.HOME -> HomeScreen(
                                    app,
                                    onFocusGame = { backdrop = it?.hero },
                                    onOpen = { detail = it },
                                    onPlay = ::play,
                                    onResume = { streamer.resume(it, app.gameFor(it)) },
                                )
                                Section.SEARCH -> SearchScreen(app, onFocusGame = { backdrop = it?.hero }, onOpen = { detail = it })
                                Section.LIBRARY -> LibraryScreen(app, onFocusGame = { backdrop = it?.hero }, onOpen = { detail = it })
                                Section.SETTINGS -> SettingsScreen(hub, app, settings)
                            }
                        }
                    }
                }
                Rail(section, railFocus, app, onSelect = ::go)
            }

            AnimatedVisibility(
                visible = detail != null,
                enter = fadeIn(Motion.page()) + scaleIn(Motion.page(), initialScale = 1.03f),
                exit = fadeOut(Motion.fade()) + scaleOut(Motion.fade(), targetScale = 1.02f),
            ) {
                var shown by remember { mutableStateOf(detail) }
                LaunchedEffect(detail) { if (detail != null) shown = detail }
                shown?.let { g ->
                    RevealPage(key = g.id) {
                        GameDetailScreen(app, settings, g, onClose = { detail = null }, onPlay = ::play, onQuitSession = { streamer.quit(it) })
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = streaming,
            enter = fadeIn(Motion.page()),
            exit = fadeOut(Motion.page()),
        ) {
            StreamScreen(streamer, settings, hub)
        }
    }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { railFocus.getValue(Section.HOME).requestFocus() }
    }
    // Coming back from a stream: put focus somewhere sensible again.
    LaunchedEffect(streaming) {
        if (!streaming) {
            delay(250)
            runCatching { railFocus.getValue(section).requestFocus() }
        }
    }
    // A brand-new library: show the most recent game until something is focused.
    LaunchedEffect(app.library) {
        if (backdrop == null) backdrop = app.library.firstOrNull()?.hero
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Rail(section: Section, focus: Map<Section, FocusRequester>, app: AppState, onSelect: (Section) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (open) 196.dp else RailWidth, Motion.page(), label = "rail")
    val labels by animateFloatAsState(if (open) 1f else 0f, Motion.fade(), label = "labels")
    // The selection pill glides between items instead of jumping.
    val pillY by animateDpAsState((RailItemHeight + RailItemGap) * section.ordinal, Motion.focus(), label = "pill")
    val type = LocalType.current
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.55f + 0.3f * labels),
                    1f to Color.Black.copy(alpha = 0.0f + 0.55f * labels),
                ),
            )
            // Entering the rail always lands on the current section, never a random item.
            .focusProperties { enter = { focus.getValue(section) } }
            .onFocusChanged { open = it.hasFocus }
            .focusGroup()
            .padding(vertical = 26.dp, horizontal = 12.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Box {
            Box(
                Modifier
                    .offset(y = pillY)
                    .fillMaxWidth()
                    .height(RailItemHeight)
                    .background(Palette.glassStrong, RoundedCornerShape(50)),
            )
            Column {
                for (s in Section.entries) {
                    RailItem(s.label, s.icon, selected = s == section, labels = labels, focusRequester = focus.getValue(s), onClick = { onSelect(s) })
                    Spacer(Modifier.height(RailItemGap))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        val p = app.profile
        Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(Palette.glassStrong),
                contentAlignment = Alignment.Center,
            ) {
                if (p?.avatar != null) {
                    GameArt(p.avatar, "", Modifier.fillMaxSize())
                } else {
                    BasicText((p?.name ?: "?").take(1).uppercase(), style = type.label)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.graphicsLayer { alpha = labels }) {
                BasicText(p?.name ?: "Not signed in", style = type.label, softWrap = false)
                BasicText(if (p != null) "GeForce NOW" else "Sign in from Home", style = type.caption, softWrap = false)
            }
        }
    }
}

@Composable
private fun RailItem(label: String, icon: ImageVector, selected: Boolean, labels: Float, focusRequester: FocusRequester, onClick: () -> Unit) {
    Pressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(RailItemHeight),
        shape = RoundedCornerShape(50),
        focusedScale = 1.04f,
        color = Color.Transparent,
        border = false,
        focusRequester = focusRequester,
    ) { focused ->
        val fg = onFocusColor(focused, if (selected) Palette.text else Palette.textSecondary)
        Row(Modifier.fillMaxSize().padding(start = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(icon, label, Modifier.size(17.dp), colorFilter = ColorFilter.tint(fg))
            Spacer(Modifier.width(14.dp))
            BasicText(label, Modifier.graphicsLayer { alpha = labels }, style = LocalType.current.label.copy(color = fg), softWrap = false)
        }
    }
}

@Composable
private fun SignInBackground() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF26262B), Palette.base), radius = 2400f),
        ),
    )
}
