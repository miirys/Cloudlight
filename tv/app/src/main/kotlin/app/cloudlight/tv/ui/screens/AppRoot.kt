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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import app.cloudlight.tv.ui.components.Avatar
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalDensity
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

/** Height of the top bar; pages leave this much room above their content. */
val TopBarHeight = 58.dp

/**
 * The element a section wants focus on when you come down from the top bar (Home's Play
 * button, the first settings category and so on). Screens attach it with focusRequester.
 */
val LocalSectionEntry = compositionLocalOf<FocusRequester?> { null }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppRoot(hub: PadHub, app: AppState, settings: Settings, streamer: Streamer, onExit: () -> Unit) {
    var section by remember { mutableStateOf(Section.HOME) }
    var direction by remember { mutableIntStateOf(0) }
    val entry = remember { Section.entries.associateWith { FocusRequester() } }
    val content = remember { FocusRequester() }
    var detail by remember { mutableStateOf<Game?>(null) }
    var backdrop by remember { mutableStateOf<String?>(null) }
    val railFocus = remember { Section.entries.associateWith { FocusRequester() } }
    val streaming = streamer.active

    fun go(s: Section) {
        if (s == section) return
        direction = if (s.ordinal > section.ordinal) 1 else -1
        section = s
    }

    /** Moves focus from the top bar into the page: its chosen element, else its first one. */
    fun enterContent(): Boolean =
        runCatching { entry.getValue(section).requestFocus(); true }.getOrDefault(false) ||
            runCatching { content.requestFocus(); true }.getOrDefault(false)

    // Focusing a tab shows its page after a short pause, so the highlighted tab and the page
    // always agree, and sweeping across the bar doesn't build every page on the way.
    var hovering by remember { mutableStateOf<Section?>(null) }
    LaunchedEffect(hovering) {
        val h = hovering ?: return@LaunchedEffect
        delay(170)
        go(h)
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
                // Content first, so the bar draws over Home's banner.
                Box(Modifier.fillMaxSize().focusRequester(content).focusGroup()) {
                    AnimatedContent(
                        targetState = if (!app.signedIn && section != Section.SETTINGS) null else section,
                        transitionSpec = {
                            // The old page drifts away from the direction of travel and dims;
                            // the new one slides in from the other side while its parts arrive
                            // one after another (see reveal).
                            val d = direction
                            (fadeIn(tween(240, delayMillis = 60)) + slideInHorizontally(Motion.springOf(0.6f, 0.86f, IntOffset(1, 1))) { it / 16 * d })
                                .togetherWith(
                                    fadeOut(tween(170)) +
                                        slideOutHorizontally(Motion.springOf(0.5f, 1f, IntOffset(1, 1))) { -it / 22 * d } +
                                        scaleOut(tween(260), targetScale = 0.96f),
                                )
                        },
                        label = "section",
                    ) { s ->
                        RevealPage(key = s, dx = direction) {
                          CompositionLocalProvider(LocalSectionEntry provides s?.let { entry.getValue(it) }) {
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
                }
                if (app.signedIn) {
                    TopBar(
                        section, railFocus, app,
                        onSelect = ::go,
                        onFocusTab = { hovering = it },
                        onDown = ::enterContent,
                    )
                }
                ProblemNote(app)
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

    val focusManager = LocalFocusManager.current
    LaunchedEffect(app.signedIn) {
        delay(200)
        // Start on Home's banner, the way a console lands on its featured game.
        if (!enterContent()) {
            runCatching { railFocus.getValue(section).requestFocus() }
            runCatching { focusManager.moveFocus(FocusDirection.Down) }
        }
    }
    // Coming back from a stream: put focus somewhere sensible again.
    LaunchedEffect(streaming) {
        if (!streaming) {
            delay(250)
            if (!enterContent()) runCatching { railFocus.getValue(section).requestFocus() }
        }
    }
    // A brand-new library: show the most recent game until something is focused.
    LaunchedEffect(app.library) {
        if (backdrop == null) backdrop = app.library.firstOrNull()?.hero
    }
}

/**
 * Console-style top bar: the clock on the left, the sections in the middle on a pill that
 * glides between them, and the account on the right. LB and RB switch sections from anywhere.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TopBar(
    section: Section,
    focus: Map<Section, FocusRequester>,
    app: AppState,
    onSelect: (Section) -> Unit,
    onFocusTab: (Section?) -> Unit,
    onDown: () -> Boolean,
) {
    val type = LocalType.current
    var focused by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (focused) 1f else 0f, Motion.fade(), label = "bar")
    val widths = remember { mutableStateMapOf<Section, Int>() }
    val offsets = remember { mutableStateMapOf<Section, Int>() }
    val density = LocalDensity.current
    val pillX by animateDpAsState(with(density) { (offsets[section] ?: 0).toDp() }, Motion.focus(), label = "pillX")
    val pillW by animateDpAsState(with(density) { (widths[section] ?: 0).toDp() }, Motion.focus(), label = "pillW")
    Box(
        Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 1f to Color.Transparent))
            .padding(horizontal = 40.dp),
    ) {
        Clock(Modifier.align(Alignment.CenterStart))
        Box(
            Modifier
                .align(Alignment.Center)
                .background(Palette.black.copy(alpha = 0.18f + 0.14f * glow), RoundedCornerShape(50))
                .padding(4.dp)
                .focusProperties { enter = { focus.getValue(section) } }
                .onFocusChanged {
                    focused = it.hasFocus
                    if (!it.hasFocus) onFocusTab(null)
                }
                .onPreviewKeyEvent { e ->
                    // Down always lands somewhere sensible in the page below.
                    e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown && onDown()
                }
                .focusGroup(),
        ) {
            if (pillW > 0.dp) {
                Box(Modifier.offset(x = pillX).width(pillW).height(30.dp).background(Palette.glassHeavy, RoundedCornerShape(50)))
            }
            Row {
                for (s in Section.entries) {
                    Box(
                        Modifier.onGloballyPositioned {
                            widths[s] = it.size.width
                            offsets[s] = it.positionInParent().x.toInt()
                        },
                    ) {
                        TabItem(
                            s.label, s.icon,
                            selected = s == section,
                            focusRequester = focus.getValue(s),
                            onFocused = { onFocusTab(s) },
                            onClick = { onSelect(s) },
                        )
                    }
                }
            }
        }
        val p = app.profile
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            BasicText(p?.name ?: "", style = type.label.copy(color = Palette.textSecondary), maxLines = 1)
            Spacer(Modifier.width(10.dp))
            Avatar(p?.avatar, Modifier.size(30.dp))
        }
    }
}

@Composable
private fun TabItem(label: String, icon: ImageVector, selected: Boolean, focusRequester: FocusRequester, onFocused: () -> Unit, onClick: () -> Unit) {
    Pressable(
        onClick = onClick,
        modifier = Modifier.height(30.dp).onFocusChanged { if (it.isFocused) onFocused() },
        shape = RoundedCornerShape(50),
        focusedScale = 1.06f,
        color = Color.Transparent,
        border = false,
        focusRequester = focusRequester,
    ) { focused ->
        val fg = onFocusColor(focused, if (selected) Palette.text else Palette.textSecondary)
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(icon, null, Modifier.size(13.dp), colorFilter = ColorFilter.tint(fg))
            Spacer(Modifier.width(7.dp))
            BasicText(label, style = LocalType.current.label.copy(color = fg), softWrap = false)
        }
    }
}

@Composable
private fun Clock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L)
        }
    }
    val text = remember(now) { android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(now)) }
    BasicText(text, modifier, style = LocalType.current.headline)
}

/** Says once, at startup, that the app closed unexpectedly last time, then gets out of the way. */
@Composable
private fun ProblemNote(app: AppState) {
    val problem = app.problem
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(problem) {
        if (problem == null) return@LaunchedEffect
        delay(900)
        visible = true
        delay(9000)
        visible = false
        delay(600)
        app.problem = null
    }
    Box(Modifier.fillMaxSize().padding(bottom = 28.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible && problem != null,
            enter = fadeIn(Motion.fade()) + slideInVertically(Motion.springOf(0.55f, 0.8f, IntOffset(1, 1))) { it },
            exit = fadeOut(Motion.fade()) + slideOutVertically(Motion.pageOffset) { it / 2 },
        ) {
            val type = LocalType.current
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .background(Palette.glassStrong, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                BasicText("The app closed unexpectedly last time", style = type.bodyStrong)
                Spacer(Modifier.height(3.dp))
                BasicText(
                    "${problem?.summary.orEmpty()}. The details are in Settings, under About.",
                    style = type.caption,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
