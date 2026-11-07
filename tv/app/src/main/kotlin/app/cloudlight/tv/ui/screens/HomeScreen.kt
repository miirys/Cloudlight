package app.cloudlight.tv.ui.screens

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import app.cloudlight.tv.ui.components.GlassButton
import app.cloudlight.tv.ui.components.PivotScroll
import app.cloudlight.tv.ui.components.Pressable
import app.cloudlight.tv.ui.motion.Motion
import app.cloudlight.tv.ui.theme.LocalType
import app.cloudlight.tv.ui.theme.Palette
import kotlinx.coroutines.delay

private val ScreenStart = 40.dp

@Composable
fun HomeScreen(app: AppState, onFocusGame: (Game?) -> Unit, onOpen: (Game) -> Unit) {
    val type = LocalType.current
    val library = app.library
    val recent = remember(library) { library.filter { it.lastPlayedMs > 0 }.sortedByDescending { it.lastPlayedMs }.take(12) }
    val favorites = remember(library) { library.filter { it.favorite }.take(20) }
    val sessions = app.sessions
    var featured by remember { mutableStateOf<Game?>(null) }
    val hero = featured ?: sessions.firstNotNullOfOrNull(app::gameFor) ?: recent.firstOrNull() ?: library.firstOrNull()

    // Debounced so scrolling quickly along a row does not strobe the background.
    LaunchedEffect(hero) {
        delay(160)
        onFocusGame(hero)
    }
    // Sessions started on another device show up here while Home is open.
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            app.refreshSessions()
        }
    }

    if (library.isEmpty()) {
        EmptyState(app)
        return
    }

    val focusGame: (Game) -> Unit = { featured = it }
    PivotScroll(leadingFraction = 0.56f) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item(key = "hero") { Hero(hero, sessions.firstOrNull { s -> hero != null && app.gameFor(s) == hero }, onOpen) }
            if (sessions.isNotEmpty()) {
                item(key = "continue") {
                    Shelf("Continue") {
                        items(sessions, key = { it.sessionId }) { s ->
                            val g = app.gameFor(s)
                            SessionCard(s, g, onFocus = { g?.let(focusGame) }, onClick = { g?.let(onOpen) })
                        }
                    }
                }
            }
            if (recent.isNotEmpty()) {
                item(key = "recent") {
                    Shelf("Recently played") {
                        items(recent, key = { it.id }) { g -> WideTile(g, onFocus = { focusGame(g) }, onClick = { onOpen(g) }) }
                    }
                }
            }
            if (favorites.isNotEmpty()) {
                item(key = "favorites") {
                    Shelf("Favorites") {
                        items(favorites, key = { it.id }) { g -> PosterTile(g, 96.dp, onFocus = { focusGame(g) }, onClick = { onOpen(g) }) }
                    }
                }
            }
            item(key = "library") {
                Shelf("Your library", "${library.size} games") {
                    items(library.take(40), key = { it.id }) { g -> PosterTile(g, 96.dp, onFocus = { focusGame(g) }, onClick = { onOpen(g) }) }
                }
            }
        }
    }
}

@Composable
private fun Hero(game: Game?, session: ActiveSession?, onOpen: (Game) -> Unit) {
    val type = LocalType.current
    Box(Modifier.fillMaxWidth().height(318.dp).padding(start = ScreenStart, top = 64.dp)) {
        AnimatedContent(
            targetState = game,
            contentKey = { it?.id },
            transitionSpec = {
                (fadeIn(Motion.page()) + slideInVertically(Motion.pageOffset) { it / 24 }) togetherWith fadeOut(Motion.fade())
            },
            label = "hero",
        ) { g ->
            if (g == null) return@AnimatedContent
            Column(Modifier.widthIn(max = 420.dp)) {
                BasicText(
                    when {
                        session != null -> "RUNNING IN THE CLOUD"
                        g.lastPlayedMs > 0 -> "RECENTLY PLAYED"
                        else -> "IN YOUR LIBRARY"
                    },
                    style = type.overline,
                )
                Spacer(Modifier.height(10.dp))
                if (g.logo != null) {
                    GameArt(
                        g.logo, g.title,
                        Modifier.heightIn(max = 78.dp).widthIn(max = 300.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    BasicText(g.title, style = type.display, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(12.dp))
                BasicText(metaLine(g), style = type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton(if (session != null) "Resume" else "Play", { onOpen(g) }, icon = Icons.Rounded.PlayArrow, primary = true)
                    GlassButton("Details", { onOpen(g) }, icon = Icons.Rounded.Info)
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
private fun Shelf(title: String, detail: String? = null, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val type = LocalType.current
    Column {
        Row(Modifier.padding(start = ScreenStart, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
            BasicText(title, style = type.headline)
            if (detail != null) {
                Spacer(Modifier.width(8.dp))
                BasicText(detail, style = type.caption)
            }
        }
        PivotScroll(leadingFraction = 0.045f) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScreenStart, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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

@Composable
fun PosterTile(g: Game, width: Dp, onFocus: () -> Unit, onClick: () -> Unit) =
    ArtTile(g, g.boxArt ?: g.banner, g.title, width, 3f / 4f, onFocus, onClick)

@Composable
private fun WideTile(g: Game, onFocus: () -> Unit, onClick: () -> Unit) =
    ArtTile(g, g.banner ?: g.hero, g.title, 176.dp, 16f / 9f, onFocus, onClick, caption = g.store)

@Composable
private fun SessionCard(s: ActiveSession, g: Game?, onFocus: () -> Unit, onClick: () -> Unit) {
    val detail = listOfNotNull(s.phase, s.resolution?.let { r -> s.fps?.let { "$r at $it fps" } ?: r }).joinToString("  ·  ")
    ArtTile(g, g?.banner ?: g?.hero, g?.title ?: "Cloud session", 240.dp, 16f / 9f, onFocus, onClick, caption = detail, alwaysShowTitle = true)
}

@Composable
private fun EmptyState(app: AppState) {
    val type = LocalType.current
    Box(Modifier.fillMaxSize().padding(start = ScreenStart), contentAlignment = Alignment.CenterStart) {
        Column(Modifier.widthIn(max = 380.dp)) {
            when {
                app.loading -> {
                    BasicText("Loading your library", style = type.title)
                    Spacer(Modifier.height(8.dp))
                    BasicText("Fetching your games and any sessions running on other devices.", style = type.body)
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
                    BasicText("Add games on play.geforcenow.com or in the GeForce NOW app, then refresh.", style = type.body)
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
        Row(Modifier.padding(start = ScreenStart, top = 34.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            BasicText("Library", style = type.title)
            Spacer(Modifier.width(10.dp))
            BasicText("${games.size} games, A to Z", style = type.caption, modifier = Modifier.padding(bottom = 3.dp))
        }
        PivotScroll(leadingFraction = 0.28f) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = ScreenStart, end = ScreenStart, top = 14.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(games, key = { it.id }) { g ->
                    Box(Modifier.fillMaxWidth()) {
                        ArtTileFill(g, onFocus = { focused = g }, onClick = { onOpen(g) })
                    }
                }
            }
        }
    }
}

/** Poster tile that fills its grid cell. */
@Composable
private fun ArtTileFill(g: Game, onFocus: () -> Unit, onClick: () -> Unit) {
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

