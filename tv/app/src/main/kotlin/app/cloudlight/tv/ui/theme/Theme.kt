package app.cloudlight.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Cloudlight colours: deep ink, lavender light, warm highlights. */
object Palette {
    val ink = Color(0xFF0E0E13)
    val inkRaised = Color(0xFF16161D)
    val surface = Color(0xFF1C1C25)
    val surfaceHigh = Color(0xFF262632)
    val line = Color(0x1FFFFFFF)
    val text = Color(0xFFF4F3F8)
    val textDim = Color(0xFFA7A5B6)
    val textFaint = Color(0xFF6E6C7E)
    val lavender = Color(0xFFB4A7F5)
    val lavenderDeep = Color(0xFF7B6BE0)
    val dawn = Color(0xFFFFB38A)
    val good = Color(0xFF7FE0B0)
    val warn = Color(0xFFFFD27A)
    val bad = Color(0xFFFF8A8A)

    val glow = Brush.linearGradient(listOf(lavender, dawn))
    val backdrop = Brush.radialGradient(
        listOf(Color(0xFF231F3A), ink),
        radius = 2200f,
    )
}

class Type(
    val display: TextStyle,
    val title: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
)

private val family = FontFamily.SansSerif

val CloudlightType = Type(
    display = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 44.sp, letterSpacing = (-0.5).sp, color = Palette.text),
    title = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-0.3).sp, color = Palette.text),
    heading = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Palette.text),
    body = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp, color = Palette.textDim),
    label = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Palette.text),
    caption = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.4.sp, color = Palette.textFaint),
)

val LocalType = staticCompositionLocalOf { CloudlightType }

@Composable
fun CloudlightTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalType provides CloudlightType, content = content)
}
