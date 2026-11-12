package app.cloudlight.tv.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.cloudlight.tv.input.Btn
import app.cloudlight.tv.input.PadHub
import app.cloudlight.tv.input.PadInfo
import app.cloudlight.tv.input.PadState
import app.cloudlight.tv.ui.motion.Motion
import app.cloudlight.tv.ui.motion.reveal
import app.cloudlight.tv.ui.motion.revealAt
import app.cloudlight.tv.ui.theme.LocalType
import app.cloudlight.tv.ui.theme.Palette
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Connected controllers, each drawn as a live controller: sticks move, triggers fill and
 * buttons light as you press them, so it doubles as a quick tester for drift and dead keys.
 */
@Composable
internal fun ControllerPage(hub: PadHub) {
    val pads by hub.pads.collectAsState()
    val type = LocalType.current
    if (pads.isEmpty()) {
        NoControllers()
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BasicText(
            if (pads.size == 1) "1 controller connected" else "${pads.size} controllers connected",
            Modifier.reveal(0).padding(start = 4.dp),
            style = type.caption.copy(color = Palette.textTertiary),
        )
        if (pads.size == 1) {
            PadCard(1, pads[0], large = true, modifier = Modifier.fillMaxWidth().reveal(1, soft = true))
        } else {
            pads.chunked(2).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEachIndexed { c, p ->
                        PadCard(r * 2 + c + 1, p, large = false, modifier = Modifier.weight(1f).revealAt(70L + r * 110L + c * 60L, soft = true))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        BasicText(
            "Hold Start during a game to open the menu. LB and RB switch tabs here.",
            Modifier.reveal(3).padding(start = 4.dp),
            style = type.caption,
        )
    }
}

@Composable
private fun NoControllers() {
    val type = LocalType.current
    val breathe by rememberInfiniteTransition(label = "idle").animateFloat(
        0.35f, 0.7f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "breathe",
    )
    val measurer = rememberTextMeasurer()
    Column(
        Modifier.fillMaxSize().padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.width(300.dp).aspectRatio(1.8f).reveal(0, soft = true).graphicsLayer { alpha = breathe }) {
            drawPad(PadState.Idle, Lights.None, measurer)
        }
        Spacer(Modifier.height(18.dp))
        BasicText("No controllers connected", Modifier.reveal(1), style = type.title)
        Spacer(Modifier.height(6.dp))
        BasicText(
            "Pair one in your TV's Bluetooth settings, or plug in a USB controller or its receiver. " +
                "If the TV asks whether Cloudlight may use a USB device, tick \"Always\" so it never asks again.",
            Modifier.reveal(2).widthIn(max = 420.dp),
            style = type.body.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
        )
    }
}

@Composable
private fun PadCard(player: Int, p: PadInfo, large: Boolean, modifier: Modifier = Modifier) {
    val type = LocalType.current
    val s = p.state
    val lights = rememberLights(s.buttons)
    val measurer = rememberTextMeasurer()
    Column(
        modifier
            .background(Palette.glass, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).background(Palette.white, CircleShape), contentAlignment = Alignment.Center) {
                BasicText("$player", style = type.label.copy(color = Palette.black))
            }
            Spacer(Modifier.width(10.dp))
            BasicText(p.name, Modifier.weight(1f), style = type.headline, maxLines = 1)
            Tag(p.route)
            if (p.reportsPerSecond > 0) {
                Spacer(Modifier.width(6.dp))
                Tag("${p.reportsPerSecond} Hz")
            }
        }
        Spacer(Modifier.height(if (large) 14.dp else 10.dp))
        Canvas(Modifier.fillMaxWidth().aspectRatio(if (large) 2.5f else 1.75f)) {
            drawPad(s, lights, measurer)
        }
        Spacer(Modifier.height(if (large) 14.dp else 10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Readout("Left stick", stick(s.lx, s.ly))
            Readout("Right stick", stick(s.rx, s.ry))
            Readout("LT", "${(s.lt * 100).roundToInt()} %")
            Readout("RT", "${(s.rt * 100).roundToInt()} %")
        }
    }
}

private fun stick(x: Float, y: Float): String {
    fun f(v: Float) = (v * 100).roundToInt().let { if (abs(it) < 1) "0" else "$it" }
    return "${f(x)}, ${f(-y)}"
}

@Composable
private fun Tag(text: String) {
    BasicText(
        text,
        Modifier.background(Palette.glassStrong, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp),
        style = LocalType.current.caption,
    )
}

@Composable
private fun Readout(label: String, value: String) {
    Column {
        BasicText(label, style = LocalType.current.caption.copy(color = Palette.textTertiary))
        BasicText(value, style = LocalType.current.label)
    }
}

/** How lit each button is, 0 to 1, animated so presses flash on and fade off. */
private class Lights(private val values: Map<Int, Float>) {
    operator fun get(bit: Int) = values[bit] ?: 0f

    companion object {
        val None = Lights(emptyMap())
    }
}

private val AllButtons = listOf(
    Btn.A, Btn.B, Btn.X, Btn.Y, Btn.LB, Btn.RB, Btn.VIEW, Btn.MENU, Btn.LS, Btn.RS, Btn.GUIDE,
    Btn.UP, Btn.DOWN, Btn.LEFT, Btn.RIGHT,
)

@Composable
private fun rememberLights(buttons: Int): Lights {
    val map = HashMap<Int, Float>()
    for (b in AllButtons) {
        val v by animateFloatAsState(if (buttons and b != 0) 1f else 0f, if (buttons and b != 0) Motion.press() else Motion.fade(), label = "btn")
        map[b] = v
    }
    return Lights(map)
}

private val Idle = Color.White.copy(alpha = 0.10f)
private val Edge = Color.White.copy(alpha = 0.30f)

private fun lerp(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
    a.alpha + (b.alpha - a.alpha) * t,
)

/** Draws a generic controller in the middle of the canvas, lit from [s] and [lights]. */
private fun DrawScope.drawPad(s: PadState, lights: Lights, measurer: TextMeasurer) {
    val cw = min(size.width, size.height * 1.6f)
    val ch = cw / 1.6f
    val ox = (size.width - cw) / 2f
    val oy = (size.height - ch) / 2f
    fun x(f: Float) = ox + f * cw
    fun y(f: Float) = oy + f * ch
    fun d(f: Float) = f * cw
    val line = 1.2.dp.toPx()

    fun fill(t: Float) = lerp(Idle, Color.White, t)

    // Triggers: they fill from the outside in as they are squeezed.
    for ((left, v) in listOf(true to s.lt, false to s.rt)) {
        val r = if (left) Rect(x(0.17f), y(0.0f), x(0.33f), y(0.075f)) else Rect(x(0.67f), y(0.0f), x(0.83f), y(0.075f))
        val cr = CornerRadius(r.height / 2)
        drawRoundRect(Idle, r.topLeft, r.size, cr)
        val w = r.width * v.coerceIn(0f, 1f)
        if (w > 0.5f) {
            val left0 = if (left) r.left else r.right - w
            drawRoundRect(Color.White, Offset(left0, r.top), Size(w, r.height), cr)
        }
        drawRoundRect(Edge, r.topLeft, r.size, cr, style = Stroke(line))
    }
    // Bumpers.
    for ((bit, r) in listOf(Btn.LB to Rect(x(0.15f), y(0.1f), x(0.35f), y(0.175f)), Btn.RB to Rect(x(0.65f), y(0.1f), x(0.85f), y(0.175f)))) {
        drawRoundRect(fill(lights[bit]), r.topLeft, r.size, CornerRadius(r.height / 2))
        drawRoundRect(Edge, r.topLeft, r.size, CornerRadius(r.height / 2), style = Stroke(line))
    }

    // Body: a rounded slab with two grips.
    val body = Path().apply {
        val slab = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(x(0.1f), y(0.19f), x(0.9f), y(0.68f)), CornerRadius(d(0.09f)))) }
        val grips = Path().apply {
            addOval(Rect(x(0.1f), y(0.32f), x(0.38f), y(0.99f)))
            addOval(Rect(x(0.62f), y(0.32f), x(0.9f), y(0.99f)))
        }
        op(slab, grips, PathOperation.Union)
    }
    drawPath(body, Color.White.copy(alpha = 0.05f))
    drawPath(body, Color.White.copy(alpha = 0.22f), style = Stroke(line))

    // Sticks: a ring, and a cap that follows the stick and lights when clicked.
    fun stick(cx: Float, cy: Float, sx: Float, sy: Float, bit: Int) {
        val c = Offset(x(cx), y(cy))
        drawCircle(Color.Black.copy(alpha = 0.25f), d(0.068f), c)
        drawCircle(Edge, d(0.068f), c, style = Stroke(line))
        val cap = Offset(c.x + sx * d(0.03f), c.y + sy * d(0.03f))
        drawCircle(lerp(Color.White.copy(alpha = 0.55f), Color.White, lights[bit]), d(0.045f), cap)
        if (lights[bit] > 0.01f) drawCircle(Color.White.copy(alpha = 0.35f * lights[bit]), d(0.08f), c, style = Stroke(line * 2))
    }
    stick(0.27f, 0.38f, s.lx, s.ly, Btn.LS)
    stick(0.61f, 0.6f, s.rx, s.ry, Btn.RS)

    // D-pad: four arms around a plain centre.
    val dc = Offset(x(0.39f), y(0.6f))
    val a = d(0.036f)
    val len = d(0.06f)
    val arms = listOf(
        Btn.UP to Rect(dc.x - a / 2, dc.y - len, dc.x + a / 2, dc.y - a / 2),
        Btn.DOWN to Rect(dc.x - a / 2, dc.y + a / 2, dc.x + a / 2, dc.y + len),
        Btn.LEFT to Rect(dc.x - len, dc.y - a / 2, dc.x - a / 2, dc.y + a / 2),
        Btn.RIGHT to Rect(dc.x + a / 2, dc.y - a / 2, dc.x + len, dc.y + a / 2),
    )
    drawRect(Idle, Offset(dc.x - a / 2, dc.y - a / 2), Size(a, a))
    for ((bit, r) in arms) drawRoundRect(fill(lights[bit]), r.topLeft, r.size, CornerRadius(d(0.008f)))

    // Face buttons, lettered.
    val fc = Offset(x(0.73f), y(0.38f))
    val off = d(0.052f)
    val r = d(0.027f)
    val style = TextStyle(fontSize = (r * 0.95f).toSp(), fontWeight = FontWeight.SemiBold)
    for ((bit, pos, letter) in listOf(
        Triple(Btn.Y, Offset(0f, -off), "Y"),
        Triple(Btn.B, Offset(off, 0f), "B"),
        Triple(Btn.A, Offset(0f, off), "A"),
        Triple(Btn.X, Offset(-off, 0f), "X"),
    )) {
        val c = fc + pos
        val t = lights[bit]
        drawCircle(fill(t), r, c)
        drawCircle(Edge, r, c, style = Stroke(line))
        val layout = measurer.measure(letter, style.copy(color = lerp(Color.White.copy(alpha = 0.7f), Color.Black, t)))
        drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
    }

    // View, menu and guide.
    val pill = Size(d(0.04f), d(0.022f))
    for ((bit, cx) in listOf(Btn.VIEW to 0.44f, Btn.MENU to 0.56f)) {
        val tl = Offset(x(cx) - pill.width / 2, y(0.37f) - pill.height / 2)
        drawRoundRect(fill(lights[bit]), tl, pill, CornerRadius(pill.height / 2))
        drawRoundRect(Edge, tl, pill, CornerRadius(pill.height / 2), style = Stroke(line))
    }
    val g = Offset(x(0.5f), y(0.26f))
    drawCircle(fill(lights[Btn.GUIDE]), d(0.03f), g)
    drawCircle(Edge, d(0.03f), g, style = Stroke(line))
}
