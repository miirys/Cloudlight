package app.cloudlight.tv.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.cloudlight.tv.ui.motion.Motion
import app.cloudlight.tv.ui.motion.reveal
import app.cloudlight.tv.ui.theme.LocalType
import app.cloudlight.tv.ui.theme.Palette
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Where the focused row is, so one highlight can glide from row to row instead of each row
 * lighting up on its own. Also carries the focused row's hint for the footer.
 */
@Stable
class SettingsFocus(
    /**
     * When true, left and right change a value as soon as its row is focused (fine for a
     * single column like the in-game menu). When false, OK starts changing the value, so left
     * and right stay free for moving between columns.
     */
    val inlineValues: Boolean = false,
) {
    var top by mutableFloatStateOf(0f)
    var height by mutableFloatStateOf(0f)
    var active by mutableStateOf(false)
    var hint by mutableStateOf<String?>(null)
    internal var owner by mutableIntStateOf(0)
    internal var next = 0
}

private val LocalSettingsFocus = compositionLocalOf<SettingsFocus?> { null }

/**
 * A plain list of settings with a single white highlight that slides to the focused row.
 * Rows are separated by hairlines; nothing is boxed.
 */
@Composable
fun SettingsList(state: SettingsFocus = remember { SettingsFocus() }, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val top = remember { Animatable(0f) }
    val height = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(state.top, state.height, state.active) {
        if (!state.active) return@LaunchedEffect
        if (!placed) {
            // First focus: appear in place rather than flying in from the top.
            top.snapTo(state.top)
            height.snapTo(state.height)
            placed = true
            return@LaunchedEffect
        }
        coroutineScope {
            launch { top.animateTo(state.top, Motion.springOf(0.34f, 0.86f)) }
            launch { height.animateTo(state.height, Motion.springOf(0.34f, 0.86f)) }
        }
    }
    val alpha by animateFloatAsState(if (state.active) 1f else 0f, Motion.fade(), label = "highlight")
    CompositionLocalProvider(LocalSettingsFocus provides state) {
        Box(modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .offset { IntOffset(0, top.value.roundToInt()) }
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { height.value.toDp() })
                    .graphicsLayer { this.alpha = alpha }
                    .background(Palette.white, RoundedCornerShape(12.dp)),
            )
            Column { content() }
        }
    }
}

/** Shared plumbing for a row: focus reporting, hover-to-focus, layout tracking. */
@Composable
private fun SettingRowBase(
    hint: String?,
    onKey: (KeyEvent) -> Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    onFocus: (Boolean) -> Unit = {},
    content: @Composable (focused: Boolean) -> Unit,
) {
    val state = LocalSettingsFocus.current
    val id = remember { (state?.next ?: 0).also { state?.next = it + 1 } + 1 }
    var focused by remember { mutableStateOf(false) }
    var top by remember { mutableFloatStateOf(0f) }
    var h by remember { mutableFloatStateOf(0f) }
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val requester = focusRequester ?: remember { FocusRequester() }
    LaunchedEffect(hovered) { if (hovered && !focused) runCatching { requester.requestFocus() } }
    LaunchedEffect(focused, top, h, hint) {
        if (focused && state != null) {
            state.owner = id
            state.top = top
            state.height = h
            state.hint = hint
            state.active = true
        } else if (!focused && state != null && state.owner == id) {
            state.active = false
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .reveal(id, distance = 22.dp)
            .onGloballyPositioned {
                top = it.positionInParent().y
                h = it.size.height.toFloat()
            }
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                onFocus(it.isFocused)
            }
            .onPreviewKeyEvent { e -> onKey(e) }
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
    ) {
        content(focused)
    }
}

@Composable
private fun RowLayout(label: String, focused: Boolean, trailing: @Composable () -> Unit) {
    val fg = onFocusColor(focused)
    Column {
        Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(label, Modifier.weight(1f), style = LocalType.current.bodyStrong.copy(color = fg))
            trailing()
        }
        Divider()
    }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(0.75.dp).background(Palette.hairline))
}

/**
 * A setting with a fixed list of values. OK starts changing it: left and right step through
 * the values, and OK, back, up or down finish. (In the in-game menu, left and right change it
 * straight away.) The value slides in from the side it came from.
 */
@Composable
fun ValueSetting(
    label: String,
    options: List<String>,
    index: Int,
    onSelect: (Int) -> Unit,
    hint: String? = null,
    focusRequester: FocusRequester? = null,
) {
    val state = LocalSettingsFocus.current
    val inline = state?.inlineValues ?: true
    var dir by remember { mutableIntStateOf(1) }
    var editing by remember { mutableStateOf(false) }
    var okHeld by remember { mutableStateOf(false) }
    val i = index.coerceIn(0, (options.size - 1).coerceAtLeast(0))
    fun go(d: Int, wrap: Boolean) {
        val n = if (wrap) (i + d).mod(options.size) else (i + d).coerceIn(0, options.lastIndex)
        if (n != i) {
            dir = if (wrap && d > 0 && n < i) 1 else d
            onSelect(n)
        }
    }
    BackHandler(enabled = editing) { editing = false }
    val live = inline || editing
    SettingRowBase(
        hint = if (editing) "Left and right to change, OK when done." else hint,
        onKey = { e ->
            val down = e.type == KeyEventType.KeyDown
            when (e.key) {
                Key.DirectionLeft -> if (live) { if (down) go(-1, false); true } else false
                Key.DirectionRight -> if (live) { if (down) go(1, false); true } else false
                Key.DirectionUp, Key.DirectionDown -> { if (editing) editing = false; false }
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> when {
                    inline -> false
                    else -> {
                        // Toggle once per press, however long OK is held.
                        if (down && !okHeld) editing = !editing
                        okHeld = down
                        true
                    }
                }
                else -> false
            }
        },
        onClick = { go(1, true) },
        focusRequester = focusRequester,
        onFocus = { if (!it) editing = false },
    ) { focused ->
        RowLayout(label, focused) {
            val fg = onFocusColor(focused, Palette.textSecondary)
            val arrows by animateFloatAsState(if (focused) (if (live) 1f else 0.3f) else 0f, Motion.fade(), label = "arrows")
            val edit by animateFloatAsState(if (editing) 1f else 0f, Motion.focus(), label = "edit")
            val valueColor = if (editing) Palette.white else fg
            Row(
                Modifier
                    .graphicsLayer { val sc = 1f + 0.04f * edit; scaleX = sc; scaleY = sc }
                    .background(Palette.black.copy(alpha = 0.88f * edit), RoundedCornerShape(50))
                    .padding(horizontal = 4.dp + 4.dp * edit, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val arrowTint = if (editing) Palette.white else fg
                Image(
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null,
                    Modifier.size(16.dp).graphicsLayer { alpha = arrows * if (i > 0) 1f else 0.25f },
                    colorFilter = ColorFilter.tint(arrowTint),
                )
                AnimatedContent(
                    targetState = i,
                    transitionSpec = {
                        val d = dir
                        (slideInHorizontally(Motion.pageOffset) { it / 3 * d } + fadeIn(Motion.fade()))
                            .togetherWith(slideOutHorizontally(Motion.pageOffset) { -it / 3 * d } + fadeOut(Motion.fade()))
                    },
                    contentAlignment = Alignment.Center,
                    label = "value",
                ) { v ->
                    BasicText(options.getOrElse(v) { "" }, Modifier.padding(horizontal = 4.dp), style = LocalType.current.label.copy(color = valueColor))
                }
                Image(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                    Modifier.size(16.dp).graphicsLayer { alpha = arrows * if (i < options.lastIndex) 1f else 0.25f },
                    colorFilter = ColorFilter.tint(arrowTint),
                )
            }
        }
    }
}

/** An on/off setting. OK flips it (in the in-game menu, left and right also set it). */
@Composable
fun SwitchSetting(label: String, on: Boolean, onToggle: () -> Unit, hint: String? = null) {
    val inline = LocalSettingsFocus.current?.inlineValues ?: true
    SettingRowBase(
        hint = hint,
        onKey = { e ->
            val down = e.type == KeyEventType.KeyDown
            when {
                !inline -> false
                e.key == Key.DirectionLeft -> { if (down && on) onToggle(); true }
                e.key == Key.DirectionRight -> { if (down && !on) onToggle(); true }
                else -> false
            }
        },
        onClick = onToggle,
    ) { focused ->
        RowLayout(label, focused) { SettingSwitch(on, focused) }
    }
}

@Composable
private fun SettingSwitch(on: Boolean, focused: Boolean) {
    val knob by animateFloatAsState(if (on) 1f else 0f, Motion.focus(), label = "knob")
    val track = if (focused) Palette.black.copy(alpha = 0.12f + 0.78f * knob) else Palette.white.copy(alpha = 0.16f + 0.74f * knob)
    val dot = if (focused) (if (on) Palette.white else Palette.black.copy(alpha = 0.5f)) else (if (on) Palette.black else Palette.white)
    Box(Modifier.size(width = 34.dp, height = 20.dp).background(track, RoundedCornerShape(50)).padding(3.dp)) {
        Box(Modifier.offset { IntOffset((knob * 14.dp.toPx()).roundToInt(), 0) }.size(14.dp).background(dot, CircleShape))
    }
}

/** A row that does something, with an optional value on the right. */
@Composable
fun ActionSetting(label: String, onClick: () -> Unit, value: String? = null, hint: String? = null) {
    SettingRowBase(hint = hint, onKey = { false }, onClick = onClick) { focused ->
        RowLayout(label, focused) {
            if (value != null) BasicText(value, style = LocalType.current.label.copy(color = onFocusColor(focused, Palette.textSecondary)))
        }
    }
}

/** A read-only line: label and value, not focusable. */
@Composable
fun InfoLine(label: String, value: String) {
    Column {
        Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(label, Modifier.weight(1f), style = LocalType.current.body)
            Spacer(Modifier.width(12.dp))
            BasicText(value, style = LocalType.current.bodyStrong, maxLines = 1)
        }
        Divider()
    }
}

/** A small heading that starts a group of rows. */
@Composable
fun SettingsGroup(text: String) {
    val state = LocalSettingsFocus.current
    val id = remember { (state?.next ?: 0).also { state?.next = it + 1 } }
    BasicText(text, Modifier.reveal(id, distance = 22.dp).padding(start = 16.dp, top = 18.dp, bottom = 4.dp), style = LocalType.current.caption.copy(color = Palette.textTertiary))
}
