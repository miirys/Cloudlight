package app.cloudlight.tv.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import app.cloudlight.tv.ui.motion.Motion
import app.cloudlight.tv.ui.theme.Palette

/**
 * The living background: the focused game's artwork twice. Once tiny and heavily blurred to
 * fill the screen with its colour (this is what makes panels read as frosted glass), and
 * once sharp in the top right, dissolving into the dark so text on the left stays calm.
 */
@Composable
fun Backdrop(url: String?, modifier: Modifier = Modifier, sharpArt: Boolean = true) {
    // Everything here is drawn once into its own layer, so while the rows and panels in front
    // animate, each frame costs one copy of that layer instead of redrawing two images and
    // four full-screen gradients at the screen's full resolution.
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .background(Palette.base),
    ) {
        AnimatedContent(
            targetState = url,
            transitionSpec = { fadeIn(Motion.springOf(0.9f, 1f)) togetherWith fadeOut(Motion.springOf(0.9f, 1f)) },
            label = "backdrop",
        ) { u ->
            Box(Modifier.fillMaxSize()) {
                if (u != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(u)
                            .size(Size(384, 216))
                            .transformations(BlurTransformation(radius = 10, passes = 3))
                            .memoryCacheKey("blur:$u")
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f },
                    )
                    if (sharpArt) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(u).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopEnd,
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .fillMaxHeight(0.82f)
                                .align(Alignment.TopEnd)
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    // Fade the art out towards the left and the bottom.
                                    drawRect(
                                        Brush.horizontalGradient(0f to Color.Transparent, 0.45f to Color.Black.copy(alpha = 0.85f), 1f to Color.Black),
                                        blendMode = BlendMode.DstIn,
                                    )
                                    drawRect(
                                        Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black.copy(alpha = 0.8f), 1f to Color.Transparent),
                                        blendMode = BlendMode.DstIn,
                                    )
                                },
                        )
                    }
                }
            }
        }
        // Scrims keep text legible on any artwork without flattening it.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.78f), 0.5f to Color.Black.copy(alpha = 0.35f), 1f to Color.Black.copy(alpha = 0.05f)),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)),
            ),
        )
    }
}

/**
 * Cheap, good-looking blur: the image is already tiny (384 × 216), so a few box-blur passes
 * approximate a Gaussian in a couple of milliseconds even on a modest TV CPU.
 */
class BlurTransformation(private val radius: Int, private val passes: Int) : Transformation {
    override val cacheKey: String = "blur-$radius-$passes"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        val src = input.copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(passes) {
            boxBlur(px, tmp, w, h, radius, horizontal = true)
            boxBlur(tmp, px, w, h, radius, horizontal = false)
        }
        src.setPixels(px, 0, w, 0, 0, w, h)
        return src
    }

    private fun boxBlur(inp: IntArray, out: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = r * 2 + 1
        for (line in 0 until lines) {
            fun idx(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + c else c * w + line
            }
            var a = 0; var rr = 0; var g = 0; var b = 0
            for (i in -r..r) {
                val p = inp[idx(i)]
                a += p ushr 24; rr += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
            }
            for (i in 0 until len) {
                out[idx(i)] = ((a / div) shl 24) or ((rr / div) shl 16) or ((g / div) shl 8) or (b / div)
                val add = inp[idx(i + r + 1)]
                val sub = inp[idx(i - r)]
                a += (add ushr 24) - (sub ushr 24)
                rr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                g += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                b += (add and 0xFF) - (sub and 0xFF)
            }
        }
    }
}
