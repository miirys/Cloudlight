package app.cloudlight.tv.stream

import android.media.MediaFormat
import android.opengl.EGL14
import android.opengl.EGLSurface
import android.util.Log
import org.webrtc.GlRectDrawer
import org.webrtc.RendererCommon
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Where WebRTC's hardware decoder calls into the app. The calls are added to
 * org.webrtc.AndroidVideoDecoder at build time (see buildSrc, WebRtcTextureDelivery), so the
 * three @JvmStatic names and signatures here must stay in step with that patch.
 *
 * Why it exists: WebRTC's decoder owns a single texture. It renders a decoded picture into it,
 * hands it to the renderer, and only takes the next picture once the renderer has let go. A
 * picture that is decoded while the previous one is still being drawn is thrown away. At
 * 60 fps the renderer is always done in time; at 120 fps a frame lasts 8.3 ms, drawing plus
 * waiting for the screen regularly takes longer, and roughly one frame in four was dropped,
 * which is the "80 to 90 fps on a 120 fps stream" the stats showed.
 *
 * Here the decoder waits for the previous picture instead, for at most one frame time. The
 * wait is short because the renderer is only ever a few milliseconds behind, and the limit
 * means a renderer that really can't keep up still sheds frames rather than building delay.
 */
object VideoHooks {
    private const val TAG = "VideoHooks"

    private val lock = ReentrantLock()
    private val handedOver = lock.newCondition()

    /** True from the moment a picture is rendered into the texture until it is handed on. */
    private var inFlight = false

    /** Longest wait for the previous picture, in nanoseconds: one frame of the stream. */
    @Volatile private var budgetNs = 8_333_333L

    /** The stream's frame rate, declared to the decoder so it runs at full speed. */
    @Volatile private var fps = 0

    /**
     * Whether to declare the frame rate and realtime priority to the decoder. Set from
     * Settings.decoderHints when a stream starts; crash recovery turns that off if the app
     * goes down while the decoder is starting.
     */
    @Volatile var tuneDecoder = true

    /** Pictures the decoder had to wait for, and ones it gave up on, since the stream began. */
    @Volatile var waited = 0L
        private set
    @Volatile var skipped = 0L
        private set

    /**
     * How the renderer hands pictures to the screen: 1 queues each one for its own refresh
     * (smooth, about a frame of delay), 0 always shows the newest (least delay, but two
     * pictures landing in one refresh lose one).
     */
    @Volatile var swapInterval = 1

    /** Called when a stream starts. */
    fun begin(streamFps: Int) {
        fps = streamFps
        budgetNs = (1_000_000_000L / streamFps.coerceIn(24, 240)).coerceIn(4_000_000L, 20_000_000L)
        reset()
        waited = 0
        skipped = 0
    }

    /** Called when a decoder is created or restarted: nothing of the old one is in flight. */
    fun reset() = lock.withLock {
        inFlight = false
        handedOver.signalAll()
    }

    /**
     * Runs on the decoder's output thread just before it renders a picture into the texture.
     * If the previous picture is still on its way to the renderer, wait for it (bounded).
     * When the wait runs out the decoder's own check drops this picture, as it always did.
     */
    @JvmStatic
    fun beforeTexture() {
        try {
            lock.withLock {
                if (inFlight) {
                    waited++
                    var left = budgetNs
                    while (inFlight && left > 0) left = handedOver.awaitNanos(left)
                    if (inFlight) {
                        skipped++
                        return
                    }
                }
                inFlight = true
            }
        } catch (_: InterruptedException) {
            // The decoder is shutting down; let it get on with it.
            Thread.currentThread().interrupt()
        } catch (t: Throwable) {
            Log.w(TAG, "gate failed", t)
        }
    }

    /** Runs when the decoder passes a rendered picture on; the texture is free for the next. */
    @JvmStatic
    fun textureDelivered() {
        try {
            lock.withLock {
                inFlight = false
                handedOver.signalAll()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "hand-over failed", t)
        }
    }

    /**
     * Builds the decoder's format. Stock WebRTC passes only the size, which leaves the decoder
     * guessing the frame rate; many assume 30 or 60 and clock themselves accordingly. Saying
     * "120 fps, realtime" is what MediaCodec's own documentation asks high frame rate players
     * to do.
     */
    @JvmStatic
    fun videoFormat(mime: String, width: Int, height: Int): MediaFormat {
        val format = MediaFormat.createVideoFormat(mime, width, height)
        val rate = fps
        if (tuneDecoder && rate >= 60) {
            try {
                format.setInteger(MediaFormat.KEY_FRAME_RATE, rate)
                format.setInteger(MediaFormat.KEY_PRIORITY, 0)
                format.setInteger(MediaFormat.KEY_OPERATING_RATE, rate)
            } catch (t: Throwable) {
                Log.w(TAG, "format hints failed", t)
            }
        }
        return format
    }
}

/**
 * WebRTC's standard drawer, plus the buffer hand-over mode from [VideoHooks.swapInterval].
 * The renderer gives no access to its GL thread or surface, but it calls the drawer on that
 * thread with the surface current, which is exactly where the mode has to be set.
 */
class PacedDrawer(private val inner: RendererCommon.GlDrawer = GlRectDrawer()) : RendererCommon.GlDrawer {
    private var surface: EGLSurface? = null
    private var applied = -1

    private fun pace() {
        try {
            val want = VideoHooks.swapInterval
            val current = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
            if (current == surface && want == applied) return
            surface = current
            applied = want
            if (current != null && current != EGL14.EGL_NO_SURFACE) {
                EGL14.eglSwapInterval(EGL14.eglGetCurrentDisplay(), want)
            }
        } catch (t: Throwable) {
            Log.w("PacedDrawer", "swap interval not set", t)
        }
    }

    override fun drawOes(oesTextureId: Int, texMatrix: FloatArray, frameWidth: Int, frameHeight: Int, viewportX: Int, viewportY: Int, viewportWidth: Int, viewportHeight: Int) {
        pace()
        inner.drawOes(oesTextureId, texMatrix, frameWidth, frameHeight, viewportX, viewportY, viewportWidth, viewportHeight)
    }

    override fun drawRgb(textureId: Int, texMatrix: FloatArray, frameWidth: Int, frameHeight: Int, viewportX: Int, viewportY: Int, viewportWidth: Int, viewportHeight: Int) {
        pace()
        inner.drawRgb(textureId, texMatrix, frameWidth, frameHeight, viewportX, viewportY, viewportWidth, viewportHeight)
    }

    override fun drawYuv(yuvTextures: IntArray, texMatrix: FloatArray, frameWidth: Int, frameHeight: Int, viewportX: Int, viewportY: Int, viewportWidth: Int, viewportHeight: Int) {
        pace()
        inner.drawYuv(yuvTextures, texMatrix, frameWidth, frameHeight, viewportX, viewportY, viewportWidth, viewportHeight)
    }

    override fun release() = inner.release()
}
