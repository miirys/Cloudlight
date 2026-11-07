package app.cloudlight.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import app.cloudlight.tv.R
import app.cloudlight.tv.ui.theme.Palette

/**
 * The account picture. Accounts without one get the default picture, which also shows while
 * a real one loads or if it can't be fetched.
 */
@Composable
fun Avatar(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(Palette.glassStrong)) {
        Image(painterResource(R.drawable.avatar_default), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (url != null) GameArt(url, "", Modifier.fillMaxSize(), placeholder = false)
    }
}
