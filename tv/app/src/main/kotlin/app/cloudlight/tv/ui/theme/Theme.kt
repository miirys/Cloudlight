package app.cloudlight.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.cloudlight.tv.R

/**
 * Monochrome glass. All colour on screen comes from the game artwork; the interface itself
 * is white at different strengths over a blurred, darkened copy of that art.
 */
object Palette {
    val black = Color(0xFF000000)
    val base = Color(0xFF09090B)
    val white = Color(0xFFFFFFFF)
    val text = Color(0xFFFFFFFF)
    val textSecondary = Color(0x9EFFFFFF) // 62 %
    val textTertiary = Color(0x66FFFFFF) // 40 %
    val glass = Color(0x14FFFFFF) // 8 %
    val glassStrong = Color(0x24FFFFFF) // 14 %
    val glassHeavy = Color(0x38FFFFFF) // 22 %
    val hairline = Color(0x1FFFFFFF) // 12 %
    val hairlineStrong = Color(0x3DFFFFFF) // 24 %
    val scrim = Color(0xCC000000)
    val good = Color(0xFFB9F6CA)
    val warn = Color(0xFFFFE0A3)
}

private val inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val interDisplay = FontFamily(
    Font(R.font.interdisplay_semibold, FontWeight.SemiBold),
    Font(R.font.interdisplay_bold, FontWeight.Bold),
)

/** Sizes are for the 960 × 540 dp layout space the whole app is drawn in (see MainActivity). */
class Type(
    val display: TextStyle,
    val title: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
)

val CloudlightType = Type(
    display = TextStyle(fontFamily = interDisplay, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp, color = Palette.text),
    title = TextStyle(fontFamily = interDisplay, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 27.sp, letterSpacing = (-0.3).sp, color = Palette.text),
    headline = TextStyle(fontFamily = inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = (-0.1).sp, color = Palette.text),
    body = TextStyle(fontFamily = inter, fontWeight = FontWeight.Normal, fontSize = 11.5.sp, lineHeight = 16.5.sp, color = Palette.textSecondary),
    bodyStrong = TextStyle(fontFamily = inter, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 16.5.sp, color = Palette.text),
    label = TextStyle(fontFamily = inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, color = Palette.text),
    caption = TextStyle(fontFamily = inter, fontWeight = FontWeight.Medium, fontSize = 9.5.sp, lineHeight = 13.sp, color = Palette.textSecondary),
    overline = TextStyle(fontFamily = inter, fontWeight = FontWeight.SemiBold, fontSize = 8.5.sp, lineHeight = 11.sp, letterSpacing = 1.2.sp, color = Palette.textTertiary),
)

val LocalType = staticCompositionLocalOf { CloudlightType }

@Composable
fun CloudlightTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalType provides CloudlightType, content = content)
}
