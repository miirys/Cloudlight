package app.cloudlight.tv.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.cloudlight.tv.Settings
import app.cloudlight.tv.ui.components.PillButton
import app.cloudlight.tv.ui.components.PivotScroll
import app.cloudlight.tv.ui.components.Pressable
import app.cloudlight.tv.ui.motion.Motion
import app.cloudlight.tv.ui.theme.LocalType
import app.cloudlight.tv.ui.theme.Palette

private class Card(val title: String, val body: String, val tint: Color)

@Composable
fun HomeScreen(settings: Settings, onOpenSettings: () -> Unit) {
    val type = LocalType.current
    val cards = listOf(
        Card("Stream quality", "${settings.mode.label}, ${settings.bitrateMbps} Mbps, HEVC", Palette.lavender),
        Card("Controller", "8BitDo 2.4 GHz dongle, Bluetooth pads and remotes", Palette.dawn),
        Card("Display", "Check which refresh rates your TV offers", Palette.good),
        Card("Resume", "Sessions started on other devices will show here", Palette.warn),
    )
    PivotScroll(leadingFraction = 0.2f) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            item { Hero(onOpenSettings) }
            item {
                Column {
                    BasicText("Get started", Modifier.padding(start = 64.dp, bottom = 16.dp), style = type.heading)
                    PivotScroll(leadingFraction = 0.05f) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 64.dp, vertical = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            items(cards, key = { it.title }) { c -> InfoTile(c, onOpenSettings) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(onPrimary: () -> Unit) {
    val type = LocalType.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 64.dp)
            .padding(top = 24.dp)
            .height(320.dp)
            .background(
                Brush.linearGradient(listOf(Color(0xFF2B2550), Color(0xFF1A1A24), Color(0xFF3A2430))),
                RoundedCornerShape(28.dp),
            ),
    ) {
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(48.dp),
        ) {
            BasicText("CONTINUE", style = type.caption.copy(color = Palette.lavender))
            Spacer(Modifier.height(10.dp))
            BasicText("Nothing running yet", style = type.display)
            Spacer(Modifier.height(10.dp))
            BasicText(
                "Sign in arrives in the next build. A game you start on your PC or phone will appear here, ready to pick up.",
                Modifier.width(620.dp),
                style = type.body,
            )
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PillButton("Stream settings", onPrimary, primary = true)
                PillButton("Controller check", onPrimary)
            }
        }
    }
}

@Composable
private fun InfoTile(c: Card, onClick: () -> Unit) {
    val type = LocalType.current
    Pressable(onClick = onClick, modifier = Modifier.size(width = 340.dp, height = 190.dp), shape = RoundedCornerShape(22.dp)) { focused ->
        val glow by animateFloatAsState(if (focused) 1f else 0.35f, Motion.fade(), label = "glow")
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = glow }
                .background(Brush.radialGradient(listOf(c.tint.copy(alpha = 0.35f), Color.Transparent), radius = 520f), RoundedCornerShape(22.dp)),
        )
        Column(Modifier.padding(26.dp).align(Alignment.BottomStart)) {
            BasicText(c.title, style = type.heading)
            Spacer(Modifier.height(6.dp))
            BasicText(c.body, style = type.body.copy(lineHeight = type.body.lineHeight))
        }
    }
}

@Composable
fun LibraryScreen() {
    val type = LocalType.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText("Your library", style = type.title)
            Spacer(Modifier.height(12.dp))
            BasicText("Games appear here once you sign in.", style = type.body)
        }
    }
}
