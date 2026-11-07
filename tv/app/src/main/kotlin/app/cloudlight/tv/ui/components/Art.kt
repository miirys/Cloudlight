package app.cloudlight.tv.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cloudlight.tv.ui.theme.LocalType
import app.cloudlight.tv.ui.theme.Palette
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Game artwork with a quiet placeholder (the title on glass) while loading or when missing. */
@Composable
fun GameArt(url: String?, title: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val placeholder = @Composable {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Palette.glassStrong, Palette.glass))),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(title, Modifier.padding(10.dp), style = LocalType.current.label.copy(color = Palette.textSecondary, textAlign = TextAlign.Center))
        }
    }
    if (url == null) {
        Box(modifier) { placeholder() }
        return
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(260).build(),
        contentDescription = title,
        contentScale = contentScale,
        modifier = modifier,
        loading = { placeholder() },
        error = { placeholder() },
    )
}

/** A crisp QR code, drawn black on white with nearest-neighbour scaling so modules stay sharp. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val bitmap = remember(text) {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
        val w = matrix.width
        val h = matrix.height
        val px = IntArray(w * h) { i -> if (matrix.get(i % w, i / w)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Image(bitmap, contentDescription = "QR code", modifier = modifier, filterQuality = FilterQuality.None, contentScale = ContentScale.Fit)
}
