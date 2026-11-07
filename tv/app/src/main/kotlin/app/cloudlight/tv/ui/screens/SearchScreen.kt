package app.cloudlight.tv.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import app.cloudlight.tv.data.AppState
import app.cloudlight.tv.data.Game
import app.cloudlight.tv.ui.components.PivotScroll
import app.cloudlight.tv.ui.components.Pressable
import app.cloudlight.tv.ui.components.onFocusColor
import app.cloudlight.tv.ui.motion.Motion
import app.cloudlight.tv.ui.motion.reveal
import app.cloudlight.tv.ui.motion.revealAt
import androidx.compose.ui.focus.focusRequester
import app.cloudlight.tv.ui.theme.LocalType
import app.cloudlight.tv.ui.theme.Palette
import kotlinx.coroutines.delay

private val Keys = "abcdefghijklmnopqrstuvwxyz1234567890".map { it.toString() }

private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

/**
 * Searches everything GeForce NOW streams. Library matches show at once while the server
 * search runs; a controller types on the built-in keyboard, a real keyboard types straight in.
 */
@Composable
fun SearchScreen(app: AppState, onFocusGame: (Game?) -> Unit, onOpen: (Game) -> Unit) {
    val type = LocalType.current
    val entry = LocalSectionEntry.current
    var query by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf<Game?>(null) }
    var results by remember { mutableStateOf<List<Game>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        delay(160)
        if (focused != null) onFocusGame(focused)
    }
    // Before anything is typed: a mix of what you own and what you could play, side by side.
    val seed = remember { System.currentTimeMillis() / 3_600_000L }
    val browse = remember(app.library, app.featured, app.shelves) {
        val rng = java.util.Random(seed)
        val sources = buildList {
            add(app.library.shuffled(rng))
            add(app.featured)
            for (sh in app.shelves) add(sh.games.shuffled(rng))
        }.filter { it.isNotEmpty() }
        val owned = app.library.associateBy { it.id }
        val out = LinkedHashMap<String, Game>()
        var i = 0
        while (out.size < 60 && sources.any { i < it.size }) {
            for (src in sources) src.getOrNull(i)?.let { g -> out.putIfAbsent(g.id, owned[g.id] ?: g) }
            i++
        }
        out.values.take(60)
    }
    LaunchedEffect(query, app.library, browse) {
        val q = norm(query)
        failed = false
        if (q.isEmpty()) {
            searching = false
            results = browse
            return@LaunchedEffect
        }
        val local = app.library.asSequence()
            .map { g -> g to norm(g.title) }
            .filter { (_, t) -> t.contains(q) }
            .sortedWith(compareBy({ !it.second.startsWith(q) }, { it.second.length }))
            .map { it.first }
            .toList()
        results = local
        // Wait for a pause in typing before asking the server.
        delay(280)
        searching = true
        try {
            val remote = app.search(query.trim())
            val ids = local.map { it.id }.toSet()
            results = local + remote.filter { it.id !in ids }
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (_: Throwable) {
            failed = true
        } finally {
            searching = false
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .padding(start = 40.dp, end = 36.dp, top = TopBarHeight + 12.dp)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val n = e.nativeKeyEvent
                when {
                    n.keyCode == AndroidKeyEvent.KEYCODE_DEL -> {
                        query = query.dropLast(1)
                        true
                    }
                    n.unicodeChar != 0 && n.unicodeChar.toChar().let { it.isLetterOrDigit() || it == ' ' } &&
                        (n.device?.keyboardType == android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC) -> {
                        query += n.unicodeChar.toChar().lowercaseChar()
                        true
                    }
                    else -> false
                }
            },
    ) {
        Column(Modifier.width(276.dp)) {
            Spacer(Modifier.height(8.dp))
            QueryField(query, Modifier.reveal(1))
            Spacer(Modifier.height(12.dp))
            Column(Modifier.reveal(2).focusGroup(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (row in Keys.chunked(6)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (k in row) {
                            val first = k == Keys[0] && entry != null
                            Key(k, if (first) Modifier.size(41.dp).focusRequester(entry!!) else Modifier.size(41.dp)) { query += k }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Key("Space", Modifier.width(133.dp).height(36.dp)) { if (query.isNotEmpty() && !query.endsWith(' ')) query += " " }
                    Key("Delete", Modifier.width(87.dp).height(36.dp)) { query = query.dropLast(1) }
                    Key("Clear", Modifier.width(41.dp).height(36.dp), small = true) { query = "" }
                }
            }
        }
        Spacer(Modifier.width(30.dp))
        Column(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = when {
                    query.isBlank() -> if (results.isEmpty()) "Search every game on GeForce NOW" else "Games to play"
                    results.isEmpty() && searching -> "Searching…"
                    results.isEmpty() && failed -> "Search isn't reachable right now"
                    results.isEmpty() -> "Nothing found for “${query.trim()}”"
                    else -> "${results.size} ${if (results.size == 1) "result" else "results"}"
                },
                transitionSpec = { fadeIn(Motion.fade()) togetherWith fadeOut(Motion.fade()) },
                modifier = Modifier.reveal(1).padding(top = 8.dp, bottom = 6.dp),
                label = "summary",
            ) { t -> BasicText(t, style = type.headline) }
            PivotScroll(leadingFraction = 0.25f) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(results.size, key = { results[it].id }) { i ->
                        val g = results[i]
                        Box(Modifier.fillMaxWidth().revealAt(110L + (i / 5) * 80L + (i % 5) * 45L)) {
                            ArtTileFill(g, onFocus = { focused = g }, onClick = { onOpen(g) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueryField(query: String, modifier: Modifier = Modifier) {
    val type = LocalType.current
    val blink = rememberInfiniteTransition(label = "caret")
    val caret by blink.animateFloat(1f, 0f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "caretAlpha")
    Row(
        modifier.fillMaxWidth().height(40.dp).background(Palette.glassStrong, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cursor = @Composable { Box(Modifier.width(1.5.dp).height(16.dp).graphicsLayer { alpha = caret }.background(Palette.white)) }
        if (query.isEmpty()) {
            // The caret sits where typing will start, ahead of the placeholder.
            cursor()
            Spacer(Modifier.width(3.dp))
            BasicText("Game title", style = type.bodyStrong.copy(color = Palette.textTertiary))
        } else {
            BasicText(query, style = type.headline, maxLines = 1)
            Spacer(Modifier.width(2.dp))
            cursor()
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, small: Boolean = false, onClick: () -> Unit) {
    val type = LocalType.current
    Pressable(onClick, modifier, RoundedCornerShape(9.dp), focusedScale = 1.1f, color = Palette.glass) { focused ->
        val lift by animateFloatAsState(if (focused) 1f else 0f, Motion.focus(), label = "key")
        BasicText(
            if (label.length == 1) label.uppercase() else label,
            Modifier.graphicsLayer { translationY = -lift },
            style = (if (small || label.length > 1) type.caption else type.label).copy(color = onFocusColor(focused)),
        )
    }
}
