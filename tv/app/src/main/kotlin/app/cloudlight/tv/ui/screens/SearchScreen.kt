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
import app.cloudlight.tv.ui.theme.LocalType
import app.cloudlight.tv.ui.theme.Palette
import kotlinx.coroutines.delay

private val Keys = "abcdefghijklmnopqrstuvwxyz1234567890".map { it.toString() }

private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

/**
 * Finds games in the library and the whole GeForce NOW catalog. A controller types on the
 * built-in keyboard; a real keyboard types straight in.
 */
@Composable
fun SearchScreen(app: AppState, onFocusGame: (Game?) -> Unit, onOpen: (Game) -> Unit) {
    val type = LocalType.current
    var query by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf<Game?>(null) }
    LaunchedEffect(Unit) { app.loadCatalog() }
    LaunchedEffect(focused) {
        delay(160)
        if (focused != null) onFocusGame(focused)
    }

    val results = remember(query, app.library, app.catalog) {
        val q = norm(query)
        if (q.isEmpty()) {
            app.library.filter { it.lastPlayedMs > 0 }.sortedByDescending { it.lastPlayedMs }.take(20)
        } else {
            val owned = app.library.map { it.id }.toSet()
            val all = app.library + app.catalog.filter { it.id !in owned }
            all.asSequence()
                .map { g -> g to norm(g.title) }
                .filter { (_, t) -> t.contains(q) }
                .sortedWith(compareBy({ !it.second.startsWith(q) }, { it.first.id !in owned }, { it.second.length }))
                .map { it.first }
                .take(60)
                .toList()
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .padding(start = 40.dp, end = 36.dp, top = 34.dp)
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
            BasicText("Search", Modifier.reveal(0), style = type.title)
            Spacer(Modifier.height(14.dp))
            QueryField(query, Modifier.reveal(1))
            Spacer(Modifier.height(12.dp))
            Column(Modifier.reveal(2).focusGroup(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (row in Keys.chunked(6)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (k in row) Key(k, Modifier.size(41.dp)) { query += k }
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
                    query.isBlank() -> if (results.isEmpty()) "Type a title to search every game on GeForce NOW." else "Recently played"
                    results.isEmpty() && app.catalogLoading -> "Searching the catalog…"
                    results.isEmpty() -> "No games match “${query.trim()}”."
                    else -> "${results.size}${if (results.size == 60) "+" else ""} ${if (results.size == 1) "game" else "games"}"
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
                        Box(Modifier.fillMaxWidth().reveal(2 + i / 5)) {
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
        if (query.isEmpty()) {
            BasicText("Game title", style = type.bodyStrong.copy(color = Palette.textTertiary))
        } else {
            BasicText(query, style = type.headline, maxLines = 1)
        }
        Spacer(Modifier.width(2.dp))
        Box(Modifier.width(1.5.dp).height(16.dp).graphicsLayer { alpha = caret }.background(Palette.white))
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
