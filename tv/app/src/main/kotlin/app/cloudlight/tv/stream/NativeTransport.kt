package app.cloudlight.tv.stream

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.annotation.Keep
import app.cloudlight.tv.CrashLog
import app.cloudlight.tv.data.CloudSession
import com.opencloudgaming.opennow.NvstBridge
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.EncodedImage
import org.webrtc.VideoCodecStatus
import org.webrtc.VideoDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * A stream over NVIDIA's native protocol (NVST) instead of WebRTC. The protocol itself,
 * its RTSP handshake, encryption, error correction and input channels, lives in
 * libopennow_nvst (tv/nvst, from OpenNOW). This class feeds what it delivers to the
 * hardware decoder and the speakers, and passes input back.
 *
 * It exists because GeForce NOW only hands out 4K rigs to its native protocol.
 *
 * Video always goes through [DirectDecoder], straight onto the stream screen's surface.
 */
@Keep
class NativeTransport(
    private val session: CloudSession,
    private val config: StreamEngine.Config,
    private val events: Events,
) {
    interface Events {
        /** "connected", "streaming", "input-ready" (detail: protocol version), "input-unavailable". */
        fun onEvent(kind: String, detail: String)
        fun onStats(stats: StreamStats)
        fun onFailed(reason: String)
    }

    private var handle = 0L
    @Volatile private var stopped = false
    @Volatile var inputReady = false
        private set
    @Volatile var muted = false
    @Volatile var volume = 1.0

    private var decoder: DirectDecoder? = null
    private var audio: NativeAudio? = null
    private var audioFailed = false
    private var needsKeyframe = true
    private val firstDecoded = AtomicBoolean()
    private val lastDecodedAt = AtomicLong(SystemClock.elapsedRealtime())
    private val decodedFrames = AtomicInteger()
    private val receivedFrames = AtomicInteger()
    private val receivedBytes = AtomicLong()
    private var statsAt = SystemClock.elapsedRealtime()
    private var codecName: String? = null
    @Volatile private var decodeMs: Double? = null
    private val buffers = ArrayBlockingQueue<ByteBuffer>(8)

    private val inputWorker = ThreadPoolExecutor(
        1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(128),
        { task ->
            Thread({
                runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY) }
                task.run()
            }, "nvst-input")
        },
        ThreadPoolExecutor.AbortPolicy(),
    )

    fun start() {
        Thread({
            try {
                runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY) }
                CrashLog.step("native-load")
                handle = NvstBridge.create()
                CrashLog.step("native-connect")
                // Blocks until the stream ends, calling the three onNative methods below.
                NvstBridge.run(handle, context(), this)
            } catch (t: Throwable) {
                // UnsatisfiedLinkError lands here when the library isn't in this build.
                Log.e(TAG, "native stream failed", t)
                if (!stopped) events.onFailed(
                    if (t is LinkageError) "this build has no native streaming library for this device"
                    else "${t.javaClass.simpleName}: ${t.message}",
                )
            } finally {
                stopped = true
                inputReady = false
                inputWorker.shutdownNow()
                runCatching { decoder?.release() }
                runCatching { audio?.close() }
            }
        }, "nvst-media").start()
    }

    fun stop() {
        if (stopped) return
        stopped = true
        inputReady = false
        inputWorker.shutdownNow()
        val h = handle
        if (h != 0L) runCatching { NvstBridge.stop(h) }
    }

    fun requestKeyframe() {
        val h = handle
        if (h != 0L && !stopped) runCatching { NvstBridge.keyframe(h) }
    }

    fun sendInput(bytes: ByteArray, partial: Boolean): Boolean {
        if (stopped || !inputReady) return false
        return try {
            inputWorker.execute {
                if (!stopped) runCatching { NvstBridge.input(handle, bytes, partial) }
            }
            true
        } catch (_: RejectedExecutionException) {
            false
        }
    }

    /** What the library needs to know about the session and the stream that was asked for. */
    private fun context(): String {
        val mbps = (config.maxKbps / 1000).coerceIn(1, 200)
        val maxKbps = mbps * 1000
        val minKbps = minOf(5_000, maxKbps)
        val s = JSONObject()
            .put("sessionId", session.id)
            .put("serverIp", session.serverIp.orEmpty())
            .put("rtspsEndpoints", JSONArray(session.rtsps))
        // The library wants the media peer as a plain address. Without one it uses the peer the
        // rig names during the handshake, which is the usual case.
        session.nativeMedia?.let { m ->
            address(m.ip)?.let { ip -> s.put("mediaConnectionInfo", JSONObject().put("ip", ip).put("port", m.port)) }
        }
        val settings = JSONObject()
            .put("resolution", "${config.width}x${config.height}")
            .put("fps", config.fps)
            .put("codec", codec())
            .put("colorQuality", if (config.bitDepth >= 10 && codec() != "H264") "10bit_420" else "8bit_420")
            .put("maxBitrateMbps", mbps)
            .put(
                "networkAdaptation",
                JSONObject().put("dynamicStreamingMode", 0).put("minimumBitrateKbps", minKbps).put("initialBitrateKbps", maxOf(minKbps, maxKbps / 4)),
            )
            .put("hdrEnabled", false)
        return JSONObject().put("session", s).put("settings", settings).put("shortcuts", JSONObject()).toString()
    }

    private fun codec() = nativeCodec(config.codec)

    /** [host] as a numeric address: as given, read out of an NVIDIA host name, or looked up. */
    private fun address(host: String): String? =
        Sdp.publicIp(host) ?: runCatching { java.net.InetAddress.getByName(host).hostAddress }.getOrNull()

    // ---------- Called by the library, by name, on the media thread ----------

    @Keep
    fun onNativeInput(bytes: ByteArray) = Unit // Rumble and other server notices: not used yet.

    @Keep
    fun onNativeEvent(kind: String, detail: String) {
        try {
            if (stopped) return
            when (kind) {
                "input-ready" -> inputReady = true
                "input-unavailable" -> inputReady = false
                "connected" -> {
                    statsAt = SystemClock.elapsedRealtime()
                    lastDecodedAt.set(statsAt)
                }
                "error" -> {
                    inputReady = false
                    events.onFailed(detail.ifBlank { "the native stream stopped" })
                    return
                }
                "network" -> {
                    network(detail)
                    return
                }
            }
            events.onEvent(kind, detail)
        } catch (t: Throwable) {
            // An exception escaping into the library would end the stream thread uncleanly.
            Log.w(TAG, "event $kind failed", t)
        }
    }

    /** Once a second: "jitter,loss,rtt,duplicates". */
    private fun network(detail: String) {
        val now = SystemClock.elapsedRealtime()
        if (firstDecoded.get() && now - lastDecodedAt.get() > 8_000) {
            events.onFailed("the decoder made no progress for eight seconds")
            return
        }
        val v = detail.split(',')
        fun num(i: Int) = v.getOrNull(i)?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
        val seconds = ((now - statsAt) / 1000.0).coerceAtLeast(0.001)
        statsAt = now
        val loss = num(1)?.takeIf { it <= 100 }
        val rtt = num(2)
        events.onStats(
            StreamStats(
                fps = decodedFrames.getAndSet(0) / seconds,
                receivedFps = receivedFrames.getAndSet(0) / seconds,
                bitrateMbps = receivedBytes.getAndSet(0) * 8 / seconds / 1e6,
                rttMs = rtt,
                packetLossPct = loss,
                jitterMs = num(0),
                decodeMs = decodeMs,
                width = VideoOutput.width,
                height = VideoOutput.height,
                codec = codecName?.let { if (it == "H265") "HEVC" else it },
                decoder = VideoOutput.decoderName,
                transport = "Native (NVST)",
                serverAddress = session.serverIp,
                quality = when {
                    rtt == null && loss == null -> StreamStats.Quality.UNKNOWN
                    (rtt ?: 0.0) > 90 || (loss ?: 0.0) > 3 -> StreamStats.Quality.POOR
                    (rtt ?: 0.0) > 60 || (loss ?: 0.0) > 1 -> StreamStats.Quality.FAIR
                    (rtt ?: 0.0) > 35 || (loss ?: 0.0) > 0.3 -> StreamStats.Quality.GOOD
                    else -> StreamStats.Quality.GREAT
                },
            ),
        )
    }

    /**
     * One encoded video picture or Opus packet. [bytes] is only valid during this call.
     * Returns whether the picture was taken; a refusal makes the library ask for a key frame.
     */
    @Keep
    fun onNativeMedia(codec: String, bytes: ByteBuffer, timestampNs: Long, keyframe: Boolean, contiguous: Boolean): Boolean {
        try {
            if (stopped) return false
            if (codec.equals("opus", true)) {
                audio(bytes, timestampNs / 1000)
                return true
            }
            return video(codec, bytes, timestampNs, keyframe, contiguous)
        } catch (t: Throwable) {
            Log.w(TAG, "media failed", t)
            needsKeyframe = true
            return false
        }
    }

    private fun audio(bytes: ByteBuffer, ptsUs: Long) {
        if (!config.audio || audioFailed) return
        val out = audio ?: try {
            CrashLog.step("native-audio")
            NativeAudio().also { audio = it }
        } catch (t: Throwable) {
            Log.w(TAG, "no sound output", t)
            audioFailed = true
            return
        }
        out.feed(bytes, ptsUs, if (muted) 0f else volume.toFloat().coerceIn(0f, 1f))
    }

    private fun video(codec: String, bytes: ByteBuffer, timestampNs: Long, keyframe: Boolean, contiguous: Boolean): Boolean {
        receivedFrames.incrementAndGet()
        receivedBytes.addAndGet(bytes.remaining().toLong())
        if (!contiguous) needsKeyframe = true
        if (needsKeyframe && !keyframe) return false
        val d = decoder ?: run {
            val mime = TvDecoderFactory.mimeOf(codec) ?: throw IllegalStateException("no decoder for $codec")
            val info = TvDecoderFactory.find(mime) ?: throw IllegalStateException("this device has no hardware $codec decoder")
            CrashLog.step("decoder-create")
            val created = DirectDecoder(mime, info.name)
            val status = created.initDecode(
                VideoDecoder.Settings(2, config.width, config.height),
                VideoDecoder.Callback { _, ms, _ ->
                    if (!stopped) {
                        decodeMs = ms?.toDouble()
                        decodedFrames.incrementAndGet()
                        lastDecodedAt.set(SystemClock.elapsedRealtime())
                        if (firstDecoded.compareAndSet(false, true)) events.onEvent("streaming", "")
                    }
                },
            )
            check(status == VideoCodecStatus.OK) { "decoder start failed: $status" }
            codecName = codec.uppercase()
            decoder = created
            created
        }
        val reusable = buffers.poll()
        val buffer = if (reusable != null && reusable.capacity() >= bytes.remaining()) reusable else ByteBuffer.allocateDirect(bytes.remaining())
        buffer.clear()
        buffer.put(bytes)
        buffer.flip()
        val image = EncodedImage.builder()
            .setBuffer(buffer) { buffers.offer(buffer) }
            // No size: the decoder learns it from the stream itself.
            .setEncodedWidth(0)
            .setEncodedHeight(0)
            .setCaptureTimeNs(timestampNs)
            .setFrameType(if (keyframe) EncodedImage.FrameType.VideoFrameKey else EncodedImage.FrameType.VideoFrameDelta)
            .setRotation(0)
            .createEncodedImage()
        return try {
            val accepted = d.decode(image, null) == VideoCodecStatus.OK
            needsKeyframe = !accepted
            accepted
        } finally {
            image.release()
        }
    }

    companion object {
        private const val TAG = "NativeTransport"

        /** The codec a native session is asked for: the chosen one, or HEVC when left on automatic. */
        fun nativeCodec(wanted: String): String = when (wanted.uppercase()) {
            "H264" -> "H264"
            "AV1" -> "AV1"
            else -> "H265"
        }
    }
}

/** Opus to the speakers through Android's own decoder. Never blocks the media thread. */
private class NativeAudio {
    private val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
    private val track: AudioTrack = try {
        open()
    } catch (t: Throwable) {
        runCatching { codec.release() }
        throw t
    }
    private val info = MediaCodec.BufferInfo()
    private val pending = ArrayDeque<ByteBuffer>()
    private var pendingBytes = 0

    /** Starts the decoder and the speaker line. Stereo, 48 kHz, as the rig sends it. */
    private fun open(): AudioTrack {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, 48000, 2)
        val head = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN)
        head.put("OpusHead".toByteArray(Charsets.US_ASCII))
        head.put(1.toByte()).put(2.toByte()).putShort(0.toShort()).putInt(48000).putShort(0.toShort()).put(0.toByte())
        format.setByteBuffer("csd-0", ByteBuffer.wrap(head.array()))
        format.setByteBuffer("csd-1", nanos(0))
        format.setByteBuffer("csd-2", nanos(80_000_000))
        codec.configure(format, null, null, 0)
        codec.start()
        val out = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build(),
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
            )
            .setBufferSizeInBytes(maxOf(3840, AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            check(out.state == AudioTrack.STATE_INITIALIZED) { "audio output unavailable" }
            out.play()
        } catch (t: Throwable) {
            runCatching { out.release() }
            throw t
        }
        return out
    }

    private fun nanos(value: Long): ByteBuffer {
        val b = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
        b.putLong(value)
        b.flip()
        return b
    }

    fun feed(bytes: ByteBuffer, ptsUs: Long, gain: Float) {
        track.setVolume(gain)
        drain()
        val index = codec.dequeueInputBuffer(0)
        if (index >= 0) {
            val size = bytes.remaining()
            val input = codec.getInputBuffer(index)
            if (input == null || size > input.capacity()) {
                codec.queueInputBuffer(index, 0, 0, ptsUs, 0)
            } else {
                input.clear()
                input.put(bytes)
                codec.queueInputBuffer(index, 0, size, ptsUs, 0)
            }
        }
        repeat(8) {
            val output = codec.dequeueOutputBuffer(info, 0)
            if (output < 0) return
            try {
                codec.getOutputBuffer(output)?.let { pcm ->
                    pcm.position(info.offset)
                    pcm.limit(info.offset + info.size)
                    if (pending.isEmpty()) track.write(pcm, pcm.remaining(), AudioTrack.WRITE_NON_BLOCKING)
                    if (pcm.hasRemaining()) {
                        // Keep at most 100 ms waiting, so a stalled output can't pile sound up.
                        while (pending.isNotEmpty() && pendingBytes + pcm.remaining() > 19200) {
                            pendingBytes -= pending.removeFirst().remaining()
                        }
                        if (pcm.remaining() <= 19200) {
                            val kept = ByteBuffer.allocateDirect(pcm.remaining()).apply { put(pcm); flip() }
                            pendingBytes += kept.remaining()
                            pending.addLast(kept)
                        }
                    }
                }
            } finally {
                codec.releaseOutputBuffer(output, false)
            }
        }
    }

    private fun drain() {
        while (pending.isNotEmpty()) {
            val pcm = pending.first()
            val written = track.write(pcm, pcm.remaining(), AudioTrack.WRITE_NON_BLOCKING)
            if (written <= 0) return
            pendingBytes -= written
            if (pcm.hasRemaining()) return
            pending.removeFirst()
        }
    }

    fun close() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { track.release() }
    }
}
