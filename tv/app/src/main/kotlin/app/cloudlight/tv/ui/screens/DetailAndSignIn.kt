package app.cloudlight.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudlight.tv.data.AppState
import app.cloudlight.tv.data.Game
import app.cloudlight.tv.ui.components.Backdrop
import app.cloudlight.tv.ui.components.GameArt
import app.cloudlight.tv.ui.components.GlassButton
import app.cloudlight.tv.ui.components.QrCode
import app.cloudlight.tv.ui.components.glass
import app.cloudlight.tv.ui.motion.Motion
import app.cloudlight.tv.ui.theme.LocalType
import app.cloudlight.tv.ui.theme.Palette
import kotlinx.coroutines.delay

@Composable
fun GameDetailScreen(app: AppState, game: Game, onClose: () -> Unit) {
    val type = LocalType.current
    val play = remember { FocusRequester() }
    var notice by remember { mutableStateOf(false) }
    val session = app.sessions.firstOrNull { app.gameFor(it) == game }
    BackHandler(onBack = onClose)
    LaunchedEffect(game.id) {
        delay(60)
        runCatching { play.requestFocus() }
    }
    LaunchedEffect(notice) {
        if (notice) {
            delay(4200)
            notice = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        Backdrop(game.hero)
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 64.dp, bottom = 64.dp)
                .widthIn(max = 460.dp),
        ) {
            if (game.logo != null) {
                GameArt(game.logo, game.title, Modifier.heightIn(max = 96.dp).widthIn(max = 340.dp), contentScale = ContentScale.Fit)
            } else {
                BasicText(game.title, style = type.display, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(14.dp))
            BasicText(metaLine(game), style = type.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (session != null) {
                Spacer(Modifier.height(10.dp))
                BasicText("Running in the cloud: ${session.phase.lowercase()}.", style = type.bodyStrong)
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(if (session != null) "Resume" else "Play", { notice = true }, icon = Icons.Rounded.PlayArrow, primary = true, focusRequester = play)
                GlassButton("Back", onClose, icon = Icons.Rounded.ArrowBack)
            }
            val stores = game.variants.map { Game.storeName(it.store) }.distinct()
            if (stores.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                BasicText("Available through ${stores.joinToString(", ")}", style = type.caption)
            }
        }
        AnimatedVisibility(
            visible = notice,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp),
            enter = fadeIn(Motion.page()) + slideInVertically(Motion.pageOffset) { -it },
            exit = fadeOut(Motion.fade()) + slideOutVertically(Motion.pageOffset) { -it },
        ) {
            Box(Modifier.glass(RoundedCornerShape(50), strong = true).background(Palette.black.copy(alpha = 0.35f), RoundedCornerShape(50)).padding(horizontal = 20.dp, vertical = 11.dp)) {
                BasicText("Streaming arrives in the next Cloudlight build.", style = type.bodyStrong)
            }
        }
    }
}

@Composable
fun SignInScreen(app: AppState) {
    val type = LocalType.current
    LaunchedEffect(Unit) {
        if (app.signIn is AppState.SignIn.Idle) app.beginSignIn()
    }
    Box(Modifier.fillMaxSize().padding(horizontal = 64.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(380.dp)) {
                BasicText("Sign in to GeForce NOW", style = type.display)
                Spacer(Modifier.height(14.dp))
                BasicText(
                    "Scan the code with your phone and approve the sign-in there. Your library and any sessions running on your other devices will appear here.",
                    style = type.body,
                )
                Spacer(Modifier.height(22.dp))
                AnimatedContent(
                    targetState = app.signIn,
                    contentKey = { it::class },
                    transitionSpec = { fadeIn(Motion.page()) togetherWith fadeOut(Motion.fade()) },
                    label = "signin-status",
                ) { s ->
                    when (s) {
                        is AppState.SignIn.Waiting -> Column {
                            BasicText("No phone handy?", style = type.bodyStrong)
                            Spacer(Modifier.height(4.dp))
                            BasicText("Go to ${s.challenge.verificationUri.removePrefix("https://")} and enter this code:", style = type.body)
                            Spacer(Modifier.height(10.dp))
                            BasicText(s.challenge.userCode, style = type.title.copy(letterSpacing = 4.sp))
                        }
                        is AppState.SignIn.Failed -> Column {
                            BasicText(s.message, style = type.bodyStrong)
                            Spacer(Modifier.height(14.dp))
                            GlassButton("Get a new code", app::beginSignIn, icon = Icons.Rounded.Refresh, primary = true)
                        }
                        else -> BasicText("Preparing a sign-in code…", style = type.body)
                    }
                }
            }
            Spacer(Modifier.width(64.dp))
            Box(
                Modifier.size(230.dp).glass(RoundedCornerShape(24.dp), strong = true).padding(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                val s = app.signIn
                AnimatedContent(
                    targetState = (s as? AppState.SignIn.Waiting)?.challenge?.verificationUriComplete,
                    transitionSpec = { fadeIn(Motion.page()) togetherWith fadeOut(Motion.fade()) },
                    label = "qr",
                ) { url ->
                    if (url != null) {
                        Box(Modifier.size(194.dp).background(Palette.white, RoundedCornerShape(12.dp)).padding(10.dp)) {
                            QrCode(url, Modifier.fillMaxSize())
                        }
                    } else {
                        Box(Modifier.size(194.dp), contentAlignment = Alignment.Center) {
                            BasicText("…", style = type.title.copy(color = Palette.textTertiary))
                        }
                    }
                }
            }
        }
    }
}

