package app.cloudlight.tv.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cloudlight.tv.data.ActiveSession
import app.cloudlight.tv.data.AppState
import app.cloudlight.tv.data.Game
import app.cloudlight.tv.ui.components.GameArt
import app.cloudlight.tv.ui.components.GameLogo
import app.cloudlight.tv.ui.motion.reveal
import app.cloudlight.tv.ui.motion.revealAt
import app.cloudlight.tv.ui.components.GlassButton
import app.cloudlight.tv.ui.components.PivotScroll
import app.cloudlight.tv.ui.components.Pressable
import app.cloudlight.tv.ui.motion.Motion
import app.cloudlight.tv.ui.theme.LocalType
import app.cloudlight.tv.ui.theme.Palette
import kotlinx.coroutines.delay

private val ScreenStart = 40.dp
private val WideW = 244.dp

/** Tiles in a rail arrive left to right, a beat after their rail. */
private fun Modifier.cascade(rail: Int, index: Int): Modifier = revealAt(55L * rail + 45L * index, distance = 20.dp)
private const val SlideMs = 7000L

/** One page of the banner: a game and why it's there. */
private data class Slide(val game: Game, val tag: String, val session: ActiveSession? = null)

/**
 * Console-style home: a banner that cycles through featured games, then rails of games.
 * Moving onto a rail hands the banner (and background) to whatever is focused; coming back up
 * resumes the rotation where it left off.
 */
@Composable
fun HomeScreen(
    app: AppState,
    onFocusGame: (Game?) -> Unit,
    onOpen: (Game) -> Unit,
    onPlay: (Game) -> Unit,
    onResume: (ActiveSession) -> Unit,
) {
    val library = app.library
    val sessions = app.sessions
    val recent = remember(library) { library.filter { it.lastPlayedMs > 0 }.sortedByDescending { it.lastPlayedMs }.take(12) }
    val slides = remember(sessions, app.featured, recent) {
        val out = ArrayList<Slide>()
        for (s in sessions) app.gameFor(s)?.let { out += Slide(it, "Running in the cloud", s) }
        for (g in app.featured) if (out.none { it.game.id == g.id }) out += Slide(g, "Featured")
        if (out.size < 3) for (g in recent.take(5)) if (out.none { it.game.id == g.id }) out += Slide(g, "Jump back in")
        out.take(8)
    }
    var slide by remember { mutableIntStateOf(0) }
    var direction by remember { mutableIntStateOf(1) }
    var focusedTile by remember { mutableStateOf<Game?>(null) }
    val current = slides.getOrNull(slide.coerceIn(0, (slides.size - 1).coerceAtLeast(0)))
    val shown = focusedTile ?: current?.game
    val progress = remember { Animatable(0f) }

    fun step(d: Int) {
        if (slides.size < 2) return
        direction = d
        slide = (slide + d).mod(slides.size)
    }

    // The banner turns over on its own while you're on it, never while you're on a rail.
    LaunchedEffect(slide, focusedTile == null, slides.size) {
        progress.snapTo(0f)
        if (focusedTile != null || slides.size < 2) return@LaunchedEffect
        progress.animateTo(1f, tween(SlideMs.toInt(), easing = LinearEasing))
        step(1)
    }
    // Debounced so moving quickly along a rail doesn't strobe the background.
    LaunchedEffect(shown) {
        delay(140)
        onFocusGame(shown)
    }
    // Sessions started on another device show up while Home is open.
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            app.refreshSessions()
        }
    }

    if (slides.isEmpty() && library.isEmpty()) {
        EmptyState(app)
        return
    }

    val onTile: (Game) -> Unit = { focusedTile = it }
    PivotScroll(leadingFraction = 0.5f) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item(key = "hero") {
                Hero(
                    shown = shown,
                    slide = if (focusedTile == null) current else null,
                    direction = direction,
                    count = slides.size,
                    index = slide,
                    progress = { progress.value },
                    onFocused = { focusedTile = null },
                    onStep = ::step,
                    onPlay = { g -> current?.session?.takeIf { focusedTile == null }?.let(onResume) ?: onPlay(g) },
                    onOpen = onOpen,
                )
            }
            var row = 3
            val jump = sessions.mapNotNull { s -> app.gameFor(s)?.let { s to it } }
            if (jump.isNotEmpty() || recent.isNotEmpty()) {
                val r = row++
                item(key = "continue") {
                    val rest = recent.filter { g -> jump.none { it.second.id == g.id } }
                    Rail("Continue playing", Modifier.reveal(r)) {
                        itemsIndexed(jump, key = { _, it -> "s" + it.first.sessionId }) { i, (s, g) ->
                            Box(Modifier.cascade(r, i)) { SessionCard(s, g, onFocus = { onTile(g) }, onClick = { onResume(s) }) }
                        }
                        itemsIndexed(rest, key = { _, it -> "r" + it.id }) { i, g ->
                            Box(Modifier.cascade(r, jump.size + i)) { WideTile(g, onFocus = { onTile(g) }, onClick = { onOpen(g) }) }
                        }
                    }
                }
            }
            if (library.isNotEmpty()) {
                val r = row++
                item(key = "library") {
                    Rail("Your library", Modifier.reveal(r)) {
                        itemsIndexed(library.take(40), key = { _, it -> "l" + it.id }) { i, g ->
                            Box(Modifier.cascade(r, i)) { GameCard(g, onFocus = { onTile(g) }, onClick = { onOpen(g) }) }
                        }
                    }
                }
            }
            for (shelf in app.shelves) {
                val r = row++
                item(key = "shelf:" + shelf.id) {
                    Rail(shelf.title, Modifier.reveal(r)) {
                        itemsIndexed(shelf.games.take(30), key = { _, it -> shelf.id + it.id }) { i, g ->
                            Box(Modifier.cascade(r, i)) { GameCard(g, onFocus = { onTile(g) }, onClick = { onOpen(g) }) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Hero(
    shown: Game?,
    slide: Slide?,
    direction: Int,
    count: Int,
    index: Int,
    progress: () -> Float,
    onFocused: () -> Unit,
    onStep: (Int) -> Unit,
    onPlay: (Game) -> Unit,
    onOpen: (Game) -> Unit,
) {
    val type = LocalType.current
    var focusedButton by remember { mutableIntStateOf(-1) }
    Box(Modifier.fillMaxWidth().height(330.dp).padding(start = ScreenStart, end = ScreenStart, top = TopBarHeight + 46.dp)) {
        AnimatedContent(
            targetState = shown,
            contentKey = { it?.id },
            transitionSpec = {
                val d = if (slide != null) direction else 0
                (fadeIn(Motion.page()) + slideInHorizontally(Motion.pageOffset) { it / 14 * d })
                    .togetherWith(fadeOut(Motion.fade()) + slideOutHorizontally(Motion.pageOffset) { -it / 20 * d })
            },
            label = "hero",
        ) { g ->
            if (g == null) return@AnimatedContent
            Column(Modifier.widthIn(max = 440.dp)) {
                BasicText((slide?.tag ?: g.store ?: "").uppercase(), Modifier.reveal(0), style = type.overline)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.reveal(0, soft = true).height(84.dp).widthIn(max = 320.dp), contentAlignment = Alignment.BottomStart) {
                    GameLogo(g.logo, g.title, Modifier.height(84.dp).widthIn(max = 320.dp), alignment = Alignment.BottomStart)
                }
                Spacer(Modifier.height(12.dp))
                BasicText(metaLine(g), Modifier.reveal(1), style = type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // Buttons stay put while the slides change under them, so focus never jumps.
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 18.dp)
                .reveal(2)
                .onFocusChanged { if (it.hasFocus) onFocused() }
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        e.key == Key.DirectionLeft && focusedButton == 0 && count > 1 -> { onStep(-1); true }
                        e.key == Key.DirectionRight && focusedButton == 1 && count > 1 -> { onStep(1); true }
                        else -> false
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.onFocusChanged { if (it.isFocused) focusedButton = 0 }) {
                GlassButton(
                    if (slide?.session != null) "Resume" else "Play",
                    { shown?.let(onPlay) },
                    icon = Icons.Rounded.PlayArrow,
                    primary = true,
                    focusRequester = LocalSectionEntry.current,
                )
            }
            Box(Modifier.onFocusChanged { if (it.isFocused) focusedButton = 1 }) {
                GlassButton("Details", { shown?.let(onOpen) }, icon = Icons.Rounded.Info)
            }
        }
        if (count > 1) {
            val visible by animateFloatAsState(if (slide != null) 1f else 0f, Motion.fade(), label = "dots")
            Row(
                Modifier.align(Alignment.BottomEnd).padding(bottom = 30.dp).graphicsLayer { alpha = visible },
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until count) {
                    val active = i == index
                    val w by animateDpAsState(if (active) 22.dp else 5.dp, Motion.focus(), label = "dot")
                    Box(Modifier.width(w).height(5.dp).clip(RoundedCornerShape(50)).background(Palette.white.copy(alpha = 0.28f))) {
                        if (active) Box(Modifier.fillMaxSize().drawBehind { drawRect(Palette.white, size = Size(size.width * progress().coerceIn(0f, 1f), size.height)) })
                    }
                }
            }
        }
    }
}

fun metaLine(g: Game): String = listOfNotNull(
    g.publisher,
    g.store,
    g.genres.take(2).joinToString(", ").ifEmpty { null },
).joinToString("  ·  ")

@Composable
private fun Rail(title: String, modifier: Modifier = Modifier, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val type = LocalType.current
    // Only the title uses [modifier]; the tiles arrive on their own, one after another.
    Column {
        BasicText(title, modifier.padding(start = ScreenStart, bottom = 4.dp), style = type.headline)
        PivotScroll(leadingFraction = 0.045f) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScreenStart, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top,
                content = content,
            )
        }
    }
}

/** Artwork in a thin glass rim that turns white on focus, with the title revealed below. */
@Composable
fun ArtTile(
    game: Game?,
    url: String?,
    title: String,
    width: Dp,
    aspect: Float,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    caption: String? = null,
    alwaysShowTitle: Boolean = false,
) {
    val type = LocalType.current
    var focused by remember { mutableStateOf(false) }
    val reveal by animateFloatAsState(if (focused || alwaysShowTitle) 1f else 0f, Motion.fade(), label = "reveal")
    Column(Modifier.width(width)) {
        Pressable(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                },
            shape = RoundedCornerShape(10.dp),
            focusedScale = 1.08f,
            color = Palette.glassStrong,
            border = false,
        ) {
            GameArt(url, game?.title ?: title, Modifier.fillMaxSize().padding(1.5.dp).clip(RoundedCornerShape(8.5.dp)))
        }
        Spacer(Modifier.height(9.dp))
        Column(Modifier.graphicsLayer { alpha = reveal; translationY = (1f - reveal) * -6.dp.toPx() }) {
            BasicText(title, style = type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption != null) BasicText(caption, style = type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val CardW = 118.dp
private val CardH = 157.dp
private val CardOpen = 262.dp

/**
 * A game in a rail, as box art. Resting on it opens the card sideways, the way GeForce NOW
 * does: the game's banner, title and description slide out to the right of the art.
 */
@Composable
fun GameCard(g: Game, onFocus: () -> Unit, onClick: () -> Unit) {
    val type = LocalType.current
    var focused by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) {
            // A short pause, so sweeping along a rail doesn't open every card on the way.
            delay(120)
            open = true
        } else {
            open = false
        }
    }
    val t by animateFloatAsState(if (open) 1f else 0f, Motion.springOf(0.46f, 0.84f), label = "open")
    Pressable(
        onClick = onClick,
        modifier = Modifier
            .width(CardW + CardOpen * t.coerceAtLeast(0f))
            .height(CardH)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            },
        shape = RoundedCornerShape(12.dp),
        focusedScale = 1.04f,
        color = Palette.glassStrong,
        border = false,
    ) {
        Row(Modifier.fillMaxSize().padding(1.5.dp).clip(RoundedCornerShape(10.5.dp))) {
            GameArt(g.boxArt ?: g.banner, g.title, Modifier.width(CardW - 3.dp).fillMaxHeight())
            if (t > 0.01f) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Box(
                        Modifier
                            .wrapContentWidth(Alignment.Start, unbounded = true)
                            .width(CardOpen)
                            .fillMaxHeight(),
                    ) {
                        GameArt(g.banner ?: g.hero, "", Modifier.fillMaxSize(), placeholder = false)
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.9f), 0.65f to Color.Black.copy(alpha = 0.62f), 1f to Color.Black.copy(alpha = 0.35f)),
                            ),
                        )
                        Column(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 14.dp, end = 16.dp, bottom = 12.dp)
                                .graphicsLayer {
                                    val a = ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)
                                    alpha = a
                                    translationX = (1f - a) * 14.dp.toPx()
                                },
                        ) {
                            BasicText(g.title, style = type.bodyStrong.copy(color = Palette.white), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val meta = listOfNotNull(g.store, g.genres.firstOrNull()).joinToString("  ·  ")
                            if (meta.isNotEmpty()) BasicText(meta, style = type.caption.copy(color = Palette.white.copy(alpha = 0.7f)), maxLines = 1)
                            val about = g.description ?: g.publisher?.let { "By $it" }
                            if (about != null) {
                                Spacer(Modifier.height(5.dp))
                                BasicText(about, style = type.caption.copy(color = Palette.white.copy(alpha = 0.86f)), maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PosterTile(g: Game, width: Dp, onFocus: () -> Unit, onClick: () -> Unit) =
    ArtTile(g, g.boxArt ?: g.banner, g.title, width, 3f / 4f, onFocus, onClick)

@Composable
private fun WideTile(g: Game, onFocus: () -> Unit, onClick: () -> Unit) =
    ArtTile(g, g.banner ?: g.hero, g.title, WideW, 16f / 9f, onFocus, onClick, caption = g.store)

@Composable
private fun SessionCard(s: ActiveSession, g: Game?, onFocus: () -> Unit, onClick: () -> Unit) {
    val detail = listOfNotNull(s.phase, s.resolution?.let { r -> s.fps?.let { "$r, $it fps" } ?: r }).joinToString("  ·  ")
    ArtTile(g, g?.banner ?: g?.hero, g?.title ?: "Cloud session", WideW, 16f / 9f, onFocus, onClick, caption = detail, alwaysShowTitle = true)
}

@Composable
private fun EmptyState(app: AppState) {
    val type = LocalType.current
    Box(Modifier.fillMaxSize().padding(start = ScreenStart), contentAlignment = Alignment.CenterStart) {
        Column(Modifier.widthIn(max = 380.dp)) {
            when {
                app.loading -> {
                    BasicText("Loading your games", style = type.title)
                }
                app.error != null -> {
                    BasicText("Something went wrong", style = type.title)
                    Spacer(Modifier.height(8.dp))
                    BasicText(app.error!!, style = type.body)
                    Spacer(Modifier.height(18.dp))
                    GlassButton("Try again", app::refresh, icon = Icons.Rounded.Refresh, primary = true)
                }
                else -> {
                    BasicText("Your library is empty", style = type.title)
                    Spacer(Modifier.height(8.dp))
                    BasicText("Find games in Search, or add them in the GeForce NOW app, then refresh.", style = type.body)
                    Spacer(Modifier.height(18.dp))
                    GlassButton("Refresh", app::refresh, icon = Icons.Rounded.Refresh, primary = true)
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(app: AppState, onFocusGame: (Game?) -> Unit, onOpen: (Game) -> Unit) {
    val type = LocalType.current
    val games = remember(app.library) { app.library.sortedBy { it.title.lowercase() } }
    var focused by remember { mutableStateOf<Game?>(null) }
    LaunchedEffect(focused) {
        delay(160)
        if (focused != null) onFocusGame(focused)
    }
    if (games.isEmpty()) {
        EmptyState(app)
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.reveal(0).padding(start = ScreenStart, top = TopBarHeight + 12.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            BasicText("${games.size} games", style = type.headline)
            Spacer(Modifier.width(8.dp))
            BasicText("A to Z", style = type.caption, modifier = Modifier.padding(bottom = 1.dp))
        }
        PivotScroll(leadingFraction = 0.28f) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = ScreenStart, end = ScreenStart, top = 14.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(games.size, key = { games[it].id }) { i ->
                    val g = games[i]
                    val entry = if (i == 0) LocalSectionEntry.current else null
                    Box(Modifier.fillMaxWidth().revealAt(90L + (i / 6) * 80L + (i % 6) * 45L)) {
                        ArtTileFill(g, onFocus = { focused = g }, onClick = { onOpen(g) }, focusRequester = entry)
                    }
                }
            }
        }
    }
}

/** Poster tile that fills its grid cell. */
@Composable
internal fun ArtTileFill(g: Game, onFocus: () -> Unit, onClick: () -> Unit, focusRequester: androidx.compose.ui.focus.FocusRequester? = null) {
    val type = LocalType.current
    var focused by remember { mutableStateOf(false) }
    val reveal by animateFloatAsState(if (focused) 1f else 0f, Motion.fade(), label = "reveal")
    Column {
        Pressable(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                },
            shape = RoundedCornerShape(10.dp),
            focusedScale = 1.08f,
            color = Palette.glassStrong,
            border = false,
            focusRequester = focusRequester,
        ) {
            GameArt(g.boxArt ?: g.banner, g.title, Modifier.fillMaxSize().padding(1.5.dp).clip(RoundedCornerShape(8.5.dp)))
        }
        Spacer(Modifier.height(8.dp))
        BasicText(
            g.title,
            Modifier.graphicsLayer { alpha = 0.55f + 0.45f * reveal },
            style = type.caption.copy(color = if (focused) Palette.text else Palette.textSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

