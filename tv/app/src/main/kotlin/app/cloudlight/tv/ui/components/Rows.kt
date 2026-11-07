package app.cloudlight.tv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.cloudlight.tv.ui.motion.Motion
import app.cloudlight.tv.ui.theme.LocalType
import app.cloudlight.tv.ui.theme.Palette
import kotlinx.coroutines.delay

/** Label and hint on the left, anything on the right; the shared look of every settings row. */
@Composable
private fun RowBody(label: String, hint: String?, focused: Boolean, icon: ImageVector?, trailing: @Composable () -> Unit) {
    val type = LocalType.current
    val fg = onFocusColor(focused)
    val sub = onFocusColor(focused, Palette.textSecondary)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Image(icon, null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(fg))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            BasicText(label, style = type.label.copy(color = fg))
            if (hint != null) {
                Spacer(Modifier.height(2.dp))
                BasicText(hint, style = type.caption.copy(color = sub))
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** A row that does something when pressed. */
@Composable
fun ActionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    focusRequester: FocusRequester? = null,
) {
    Pressable(onClick, modifier.fillMaxWidth(), RoundedCornerShape(12.dp), focusedScale = 1.02f, focusRequester = focusRequester) { focused ->
        RowBody(label, hint, focused, icon) {
            if (value != null) BasicText(value, style = LocalType.current.label.copy(color = onFocusColor(focused, Palette.textSecondary)))
        }
    }
}

/** An on/off row with an iOS-style switch. */
@Composable
fun ToggleRow(label: String, on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, hint: String? = null, icon: ImageVector? = null) {
    Pressable(onToggle, modifier.fillMaxWidth(), RoundedCornerShape(12.dp), focusedScale = 1.02f) { focused ->
        RowBody(label, hint, focused, icon) { Switch(on, focused) }
    }
}

@Composable
private fun Switch(on: Boolean, focused: Boolean) {
    val knob by animateDpAsState(if (on) 14.dp else 0.dp, Motion.focus(), label = "knob")
    val fill by animateFloatAsState(if (on) 1f else 0f, Motion.fade(), label = "fill")
    val track = if (focused) Palette.black.copy(alpha = 0.12f + 0.78f * fill) else Palette.white.copy(alpha = 0.14f + 0.76f * fill)
    val dot = if (focused) (if (on) Palette.white else Palette.black.copy(alpha = 0.55f)) else (if (on) Palette.black else Palette.white)
    Box(Modifier.size(width = 34.dp, height = 20.dp).background(track, RoundedCornerShape(50)).padding(3.dp)) {
        Box(Modifier.offset(x = knob).size(14.dp).background(dot, CircleShape))
    }
}

/**
 * A setting with a fixed set of values. Pressing it opens the choices inline, as chips you
 * move between with left and right; pressing a chip picks it, Back closes without change.
 */
@Composable
fun ChoiceRow(
    label: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    icon: ImageVector? = null,
) {
    val type = LocalType.current
    var open by remember { mutableStateOf(false) }
    val rowFocus = remember { FocusRequester() }
    val chipFocus = remember(options.size) { List(options.size) { FocusRequester() } }
    val listState = rememberLazyListState()
    BackHandler(enabled = open) {
        open = false
        runCatching { rowFocus.requestFocus() }
    }
    Column(modifier.fillMaxWidth()) {
        Pressable(
            onClick = { open = !open },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            focusedScale = 1.02f,
            color = if (open) Palette.glassStrong else Palette.glass,
            focusRequester = rowFocus,
        ) { focused ->
            RowBody(label, hint, focused, icon) {
                AnimatedContent(
                    targetState = options.getOrElse(selected) { "" },
                    transitionSpec = {
                        (slideInVertically(Motion.pageOffset) { it / 2 } + fadeIn(Motion.fade()))
                            .togetherWith(slideOutVertically(Motion.pageOffset) { -it / 2 } + fadeOut(Motion.fade()))
                    },
                    label = "value",
                ) { v -> BasicText(v, style = type.label.copy(color = onFocusColor(focused, Palette.textSecondary))) }
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(Motion.page()) + fadeIn(Motion.fade()),
            exit = shrinkVertically(Motion.page()) + fadeOut(Motion.fade()),
        ) {
            LaunchedEffect(Unit) {
                delay(40)
                val i = selected.coerceIn(0, options.lastIndex)
                listState.scrollToItem((i - 2).coerceAtLeast(0))
                runCatching { chipFocus[i].requestFocus() }
            }
            PivotScroll(leadingFraction = 0.3f) {
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().focusGroup(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(options) { i, o ->
                        Pressable(
                            onClick = {
                                onSelect(i)
                                open = false
                                runCatching { rowFocus.requestFocus() }
                            },
                            shape = RoundedCornerShape(50),
                            focusedScale = 1.08f,
                            color = if (i == selected) Palette.glassHeavy else Palette.glass,
                            focusRequester = chipFocus[i],
                        ) { focused ->
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (i == selected) {
                                    Box(Modifier.size(5.dp).background(onFocusColor(focused), CircleShape))
                                    Spacer(Modifier.width(7.dp))
                                }
                                BasicText(o, style = type.label.copy(color = onFocusColor(focused)))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A small heading above a group of rows. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    BasicText(text.uppercase(), modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp), style = LocalType.current.overline)
}
