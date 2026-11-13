package app.cloudlight.tv.stream

import android.media.MediaCodec
import app.cloudlight.tv.CrashLog
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.Process
import android.util.Log
import android.view.Surface
import org.webrtc.EncodedImage
import org.webrtc.JavaI420Buffer
import org.webrtc.VideoCodecInfo
import org.webrtc.VideoCodecStatus
import org.webrtc.VideoDecoder
import org.webrtc.VideoDecoderFactory
import org.webrtc.VideoFrame
import java.util.concurrent.ConcurrentHashMap

/**
 * Where decoded video goes. The stream screen hands us its surface; the decoder renders
 * straight into it, so a frame goes from the network to the panel without a copy or an extra
 * GPU pass, at the stream's full resolution.
 */
object VideoOutput {
    @Volatile var surface: Surface? = null
        set(value) {
            field = value
            generation++
        }

    /** Bumped whenever the surface changes, so the decoder knows to start over. */
    @Volatile var generation = 0
        private set

    /** Asks the server for a fresh key frame (set by the running engine). */
    @Volatile var keyframeRequester: (() -> Unit)? = null

    /** Size and codec of what is on screen, for the stats overlay. */
    @Volatile var width = 0
    @Volatile var height = 0
    @Volatile var decoderName: String? = null

    /** The stream's frame rate, told to the decoder. */
    @Volatile var fps = 0

    /** Frames handed to the panel since the stream began. */
    @Volatile var rendered = 0L

    @Volatile var onFirstFrame: (() -> Unit)? = null

    /**
     * Use WebRTC's own decoder and renderer instead of the direct path. It costs a GPU copy
     * but avoids the low-level decoder features that some TV chips mishandle.
     */
    @Volatile var compatible = false

    /** Where frames go in compatible mode (the stream screen's renderer). */
    @Volatile var renderer: org.webrtc.VideoSink? = null
}

/** Advertises what this device can decode in hardware, with parameters GeForce NOW accepts. */
class TvDecoderFactory : VideoDecoderFactory {

    private val supported: Array<VideoCodecInfo> by lazy {
        val out = ArrayList<VideoCodecInfo>()
        if (find(MIME_HEVC) != null) {
            // Main profile, main tier, level 5.1: enough for 4K at 60 and 1440p at 120.
            out += VideoCodecInfo("H265", mapOf("profile-id" to "1", "tier-flag" to "0", "level-id" to "153"), emptyList())
        }
        if (find(MIME_AV1) != null) out += VideoCodecInfo("AV1", mapOf("profile" to "0"), emptyList())
        if (find(MIME_AVC) != null) {
            for (profile in listOf("640c1f", "42e01f")) {
                out += VideoCodecInfo(
                    "H264",
                    mapOf("profile-level-id" to profile, "level-asymmetry-allowed" to "1", "packetization-mode" to "1"),
                    emptyList(),
                )
            }
        }
        out.toTypedArray()
    }

    /** WebRTC's own factories, sharing the renderer's GL context, as OpenNOW uses them. */
    private val stockHardware by lazy { org.webrtc.HardwareVideoDecoderFactory(Rtc.egl.eglBaseContext) }
    private val stockDefault by lazy { org.webrtc.DefaultVideoDecoderFactory(Rtc.egl.eglBaseContext) }

    /**
     * On the standard path, WebRTC's own codec list: its usual codecs plus the hardware HEVC
     * and AV1 decoders, with the parameters WebRTC itself generates for them.
     */
    private val stockCodecs: Array<VideoCodecInfo> by lazy {
        val advanced = setOf("H265", "AV1")
        val base = runCatching { stockDefault.supportedCodecs.filter { it.name.uppercase() !in advanced } }.getOrDefault(emptyList())
        val hw = runCatching { stockHardware.supportedCodecs.filter { it.name.uppercase() in advanced } }.getOrDefault(emptyList())
        (base + hw).distinctBy { "${it.name.uppercase()}:${it.params.toSortedMap()}" }.toTypedArray()
    }

    override fun getSupportedCodecs(): Array<VideoCodecInfo> =
        if (VideoOutput.compatible) stockCodecs.takeIf { it.isNotEmpty() } ?: supported else supported

    override fun createDecoder(info: VideoCodecInfo): VideoDecoder? {
        CrashLog.step("decoder-create")
        if (VideoOutput.compatible) {
            val hw = runCatching { stockHardware.createDecoder(info) }
                .onFailure { Log.w("Decoders", "stock decoder unavailable", it) }.getOrNull()
            // H.264 may fall back to software; HEVC and AV1 only ever use hardware.
            val stock = hw ?: if (info.name.equals("H264", true)) runCatching { stockDefault.createDecoder(info) }.getOrNull() else null
            if (stock != null) {
                VideoOutput.decoderName = stock.implementationName
                // Hand WebRTC its own decoder object, untouched. Wrapping it in a class of ours
                // crashed natively on the first box this ran on as the video started.
                return stock
            }
        }
        val mime = mimeOf(info.name) ?: return null
        val codec = find(mime) ?: return null
        return DirectDecoder(mime, codec.name)
    }

    companion object {
        const val MIME_AVC = "video/avc"
        const val MIME_HEVC = "video/hevc"
        const val MIME_AV1 = "video/av01"

        fun mimeOf(name: String) = when (name.uppercase()) {
            "H264" -> MIME_AVC
            "H265", "HEVC" -> MIME_HEVC
            "AV1" -> MIME_AV1
            else -> null
        }

        private val cache = ConcurrentHashMap<String, Found>()

        private class Found(val info: MediaCodecInfo?)

        /** The best hardware decoder for [mime], skipping software and secure-only ones. */
        fun find(mime: String): MediaCodecInfo? = cache.getOrPut(mime) {
            val all = runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos }.getOrDefault(emptyArray())
            val candidates = all.filter { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                    !info.name.endsWith(".secure") && isHardware(info)
            }
            Found(candidates.firstOrNull())
        }.info

        private fun isHardware(info: MediaCodecInfo): Boolean {
            if (Build.VERSION.SDK_INT >= 29) return info.isHardwareAccelerated && !info.isSoftwareOnly
            val n = info.name.lowercase()
            return !(n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.startsWith("omx.ffmpeg.") || n.contains("sw"))
        }

        /**
         * Decoders whose low-latency direct mode is unreliable: start those on WebRTC's own
         * path. Matching is on the decoder's name, which names the chip family.
         */
        fun preferStock(): Boolean {
            val names = listOf(MIME_HEVC, MIME_AVC, MIME_AV1).mapNotNull { find(it)?.name?.lowercase() }
            return names.any { "realtek" in it || ".rtk" in it || "c2.rtk" in it }
        }

        /**
         * Amlogic boxes decode onto a hardware video layer under the interface. Their decoders
         * want the real picture size, the key-frame flag on key frames, and the vendor
         * low-latency switch, or they hold several frames back.
         */
        val amlogic: Boolean by lazy {
            Build.HARDWARE.contains("amlogic", ignoreCase = true) ||
                listOf(MIME_HEVC, MIME_AVC).any { find(it)?.name?.contains("amlogic", ignoreCase = true) == true }
        }

        /** Short, readable decoder description for the stats overlay. */
        fun describe(mime: String): String? = find(mime)?.name
    }
}

/**
 * A WebRTC video decoder backed by MediaCodec in low-latency mode, rendering directly to
 * [VideoOutput.surface]. WebRTC still gets a (tiny) frame per decoded picture so its timing
 * and statistics keep working.
 */
class DirectDecoder(private val mime: String, private val codecName: String) : VideoDecoder {

    private var callback: VideoDecoder.Callback? = null
    private var codec: MediaCodec? = null
    private var configuredGeneration = -1
    private var needKeyframe = true
    private var width = 1920
    private var height = 1080
    @Volatile private var running = false
    private var output: Thread? = null

    /** presentation time (µs) -> when decoding started (ns) and the frame's capture time (ns). */
    private val pending = ConcurrentHashMap<Long, LongArray>()
    private var lastPts = 0L

    // WebRTC wants a frame object back; one tiny shared buffer serves every frame.
    private val stub: JavaI420Buffer = JavaI420Buffer.allocate(2, 2)

    // Every parameter WebRTC's native side passes can be null (it always passes a null
    // DecodeInfo), and a Kotlin non-null parameter throws on entry, before any try block. An
    // exception thrown inside a native call aborts the process: that was the "crash as the
    // video starts" on this path, on every codec. So these stay nullable.
    override fun initDecode(settings: VideoDecoder.Settings?, callback: VideoDecoder.Callback?): VideoCodecStatus = try {
        Log.i(TAG, "init $codecName ${settings?.width}x${settings?.height}")
        CrashLog.step("decoder-init")
        this.callback = callback
        if (settings != null && settings.width > 0 && settings.height > 0) {
            width = settings.width
            height = settings.height
        }
        VideoCodecStatus.OK
    } catch (t: Throwable) {
        Log.w(TAG, "init crashed", t)
        VideoCodecStatus.ERROR
    }

    override fun getImplementationName(): String = codecName

    override fun decode(frame: EncodedImage?, info: VideoDecoder.DecodeInfo?): VideoCodecStatus = try {
        if (frame == null) VideoCodecStatus.NO_OUTPUT else decodeFrame(frame)
    } catch (t: Throwable) {
        // This runs inside a native call; an exception escaping here aborts the process.
        Log.w(TAG, "decode crashed", t)
        runCatching { teardown() }
        needKeyframe = true
        VideoCodecStatus.ERROR
    }

    private fun decodeFrame(frame: EncodedImage): VideoCodecStatus {
        CrashLog.step("decoding")
        val surface = VideoOutput.surface
        if (surface == null || !surface.isValid) {
            teardown()
            needKeyframe = true
            return VideoCodecStatus.NO_OUTPUT
        }
        if (frame.encodedWidth > 0 && frame.encodedHeight > 0) {
            width = frame.encodedWidth
            height = frame.encodedHeight
        }
        val key = frame.frameType == EncodedImage.FrameType.VideoFrameKey
        if (codec == null || configuredGeneration != VideoOutput.generation) {
            teardown()
            needKeyframe = true
            // Start the decoder on a key frame, so it is set up for the stream's real size.
            if (!key) {
                runCatching { VideoOutput.keyframeRequester?.invoke() }
                return VideoCodecStatus.NO_OUTPUT
            }
            if (!configure(surface)) return VideoCodecStatus.ERROR
        }
        if (needKeyframe) {
            if (!key) {
                runCatching { VideoOutput.keyframeRequester?.invoke() }
                return VideoCodecStatus.NO_OUTPUT
            }
            needKeyframe = false
        }
        val c = codec ?: return VideoCodecStatus.ERROR
        return try {
            val index = c.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (index < 0) {
                // The decoder is backed up; skip and ask for a clean restart point.
                needKeyframe = true
                runCatching { VideoOutput.keyframeRequester?.invoke() }
                return VideoCodecStatus.NO_OUTPUT
            }
            val buf = c.getInputBuffer(index) ?: return VideoCodecStatus.ERROR
            val data = frame.buffer
            val size = data.remaining()
            if (size > buf.capacity()) {
                c.queueInputBuffer(index, 0, 0, 0, 0)
                needKeyframe = true
                return VideoCodecStatus.ERROR
            }
            buf.clear()
            buf.put(data)
            // Presentation times must be unique and rising; the capture time nearly always is.
            var pts = frame.captureTimeNs / 1000
            if (pts <= lastPts) pts = lastPts + 1
            lastPts = pts
            pending[pts] = longArrayOf(System.nanoTime(), frame.captureTimeNs)
            if (pending.size > 64) pending.keys.sorted().take(pending.size - 32).forEach { pending.remove(it) }
            c.queueInputBuffer(index, 0, size, pts, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            VideoCodecStatus.OK
        } catch (t: Throwable) {
            Log.w(TAG, "decode failed", t)
            teardown()
            needKeyframe = true
            VideoCodecStatus.ERROR
        }
    }

    private fun configure(surface: Surface): Boolean = try {
        val c = MediaCodec.createByCodecName(codecName)
        val caps = runCatching { c.codecInfo.getCapabilitiesForType(mime) }.getOrNull()
        fun format(fast: Boolean) = MediaFormat.createVideoFormat(mime, width, height).apply {
            if (caps?.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback) == true) {
                setInteger(MediaFormat.KEY_MAX_WIDTH, maxOf(width, 3840))
                setInteger(MediaFormat.KEY_MAX_HEIGHT, maxOf(height, 2160))
            }
            setInteger(MediaFormat.KEY_PRIORITY, 0)
            if (fast && VideoOutput.fps >= 60) setInteger(MediaFormat.KEY_FRAME_RATE, VideoOutput.fps)
            if (Build.VERSION.SDK_INT >= 30 && caps?.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency) == true) {
                setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }
            // Vendor switches that stop decoders from holding frames back, only for the vendors
            // whose decoders are known to handle them well.
            val n = codecName.lowercase()
            if ("qti" in n || "qcom" in n) setInteger("vendor.qti-ext-dec-low-latency.enable", 1)
            if ("mtk" in n || "amlogic" in n) setInteger("vendor.low-latency.enable", 1)
            // Decode as fast as frames arrive rather than at a playback pace.
            if (fast) setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())
        }
        try {
            c.configure(format(fast = true), surface, null, 0)
        } catch (t: Throwable) {
            // Some decoders reject the operating-rate hint; try once without it.
            Log.w(TAG, "configure with operating rate failed", t)
            c.reset()
            c.configure(format(fast = false), surface, null, 0)
        }
        c.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT)
        c.start()
        codec = c
        configuredGeneration = VideoOutput.generation
        VideoOutput.decoderName = codecName
        running = true
        output = Thread({ drain(c) }, "video-out").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        true
    } catch (t: Throwable) {
        Log.e(TAG, "could not start $codecName", t)
        codec = null
        false
    }

    private fun drain(c: MediaCodec) {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY) }
        try {
            drainLoop(c)
        } catch (t: Throwable) {
            // Never let the output thread take the app down; the next key frame restarts it.
            Log.w(TAG, "output thread stopped", t)
        }
    }

    private fun drainLoop(c: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        var first = true
        while (running && codec === c) {
            val index = try {
                c.dequeueOutputBuffer(info, OUTPUT_TIMEOUT_US)
            } catch (t: Throwable) {
                break
            }
            when {
                index >= 0 -> {
                    // Show it now: frames are never held back for pacing.
                    runCatching { c.releaseOutputBuffer(index, true) }
                    VideoOutput.rendered++
                    if (first) {
                        first = false
                        CrashLog.step("video")
                        runCatching { VideoOutput.onFirstFrame?.invoke() }
                    }
                    val times = pending.remove(info.presentationTimeUs)
                    val decodeMs = times?.let { ((System.nanoTime() - it[0]) / 1_000_000).toInt() }
                    val captureNs = times?.get(1) ?: (info.presentationTimeUs * 1000)
                    stub.retain()
                    val f = VideoFrame(stub, 0, captureNs)
                    runCatching { callback?.onDecodedFrame(f, decodeMs, null) }
                    f.release()
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> runCatching {
                    val fmt = c.outputFormat
                    val w = if (fmt.containsKey("crop-right")) fmt.getInteger("crop-right") - fmt.getInteger("crop-left") + 1 else fmt.getInteger(MediaFormat.KEY_WIDTH)
                    val h = if (fmt.containsKey("crop-bottom")) fmt.getInteger("crop-bottom") - fmt.getInteger("crop-top") + 1 else fmt.getInteger(MediaFormat.KEY_HEIGHT)
                    VideoOutput.width = w
                    VideoOutput.height = h
                }
            }
        }
    }

    private fun teardown() {
        running = false
        val c = codec ?: return
        codec = null
        output?.let { t ->
            runCatching { t.join(200) }
        }
        output = null
        runCatching { c.stop() }
        runCatching { c.release() }
        pending.clear()
    }

    override fun release(): VideoCodecStatus {
        runCatching { teardown() }
        callback = null
        return VideoCodecStatus.OK
    }

    companion object {
        private const val TAG = "DirectDecoder"
        private const val INPUT_TIMEOUT_US = 30_000L
        private const val OUTPUT_TIMEOUT_US = 20_000L
    }
}
