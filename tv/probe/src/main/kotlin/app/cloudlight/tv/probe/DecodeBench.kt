package app.cloudlight.tv.probe

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.SystemClock
import android.view.Surface
import java.nio.ByteBuffer

/**
 * Decodes the bundled game-shaped clips on the real hardware decoder and measures two things:
 * how many frames a second it can decode flat out, and how long each frame takes from
 * "packet handed to the decoder" to "frame ready" when fed at the real frame rate.
 * The second number is the one that matters for streaming latency.
 */
class DecodeBench(private val context: Context, private val log: (String) -> Unit) {

    data class Clip(val asset: String, val label: String, val fps: Int)

    val clips = listOf(
        Clip("hevc_1080p120.mp4", "HEVC 1080p120 @ 100 Mbps", 120),
        Clip("hevc_2160p60.mp4", "HEVC 2160p60 @ 135 Mbps", 60),
        Clip("av1_1080p120.mp4", "AV1 1080p120 @ 100 Mbps", 120),
        Clip("av1_2160p60.mp4", "AV1 2160p60 @ 100 Mbps", 60),
    )

    private class Sample(val data: ByteArray, val keyframe: Boolean)

    fun runAll(surface: Surface?): String = buildString {
        for (clip in clips) {
            val line = try {
                run(clip, surface)
            } catch (t: Throwable) {
                "${clip.label}: failed (${t.javaClass.simpleName}: ${t.message})"
            }
            log(line.lineSequence().first())
            append(line).append('\n')
        }
    }

    private fun run(clip: Clip, surface: Surface?): String {
        val (format, samples) = load(clip.asset) ?: return "${clip.label}: clip not bundled in this build"
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val decoder = pickDecoder(format) ?: return "${clip.label}: no decoder for $mime at this size"
        val loops = 3
        val flat = decode(decoder, format, samples, loops, surface, paceFps = 0)
        val paced = decode(decoder, format, samples, loops, surface, paceFps = clip.fps)
        val sb = StringBuilder()
        sb.append("${clip.label} on $decoder: flat-out ${"%.1f".format(flat.fps)} fps")
        sb.append(if (flat.fps >= clip.fps * 1.1) " (enough)" else " (TOO SLOW for ${clip.fps})")
        sb.append(", low-latency mode ${if (flat.lowLatency) "on" else "unavailable"}\n")
        sb.append("    at ${clip.fps} fps: decode latency avg ${ms(paced.avgUs)}, p95 ${ms(paced.p95Us)}, max ${ms(paced.maxUs)}; ")
        sb.append("frames held by decoder ${paced.heldFrames}; late frames ${paced.late} of ${paced.frames}")
        return sb.toString()
    }

    private fun ms(us: Long) = "%.1f ms".format(us / 1000.0)

    private fun load(asset: String): Pair<MediaFormat, List<Sample>>? {
        val afd = try {
            context.assets.openFd(asset)
        } catch (t: Throwable) {
            return null
        }
        val ex = MediaExtractor()
        try {
            ex.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            val track = (0 until ex.trackCount).firstOrNull {
                ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return null
            ex.selectTrack(track)
            val format = ex.getTrackFormat(track)
            val buf = ByteBuffer.allocate(8 * 1024 * 1024)
            val samples = ArrayList<Sample>()
            while (true) {
                buf.clear()
                val n = ex.readSampleData(buf, 0)
                if (n < 0) break
                val bytes = ByteArray(n)
                buf.get(bytes, 0, n)
                samples += Sample(bytes, ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                ex.advance()
            }
            return format to samples
        } finally {
            ex.release()
            afd.close()
        }
    }

    private fun pickDecoder(format: MediaFormat): String? {
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val w = format.getInteger(MediaFormat.KEY_WIDTH)
        val h = format.getInteger(MediaFormat.KEY_HEIGHT)
        val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                try {
                    info.getCapabilitiesForType(mime).videoCapabilities?.isSizeSupported(w, h) == true
                } catch (t: Throwable) {
                    false
                }
        }
        return infos.sortedBy { if (isHardware(it)) 0 else 1 }.firstOrNull()?.name
    }

    private fun isHardware(info: MediaCodecInfo) =
        if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated
        else !(info.name.startsWith("OMX.google.") || info.name.startsWith("c2.android."))

    private class Result(
        val fps: Double, val avgUs: Long, val p95Us: Long, val maxUs: Long,
        val heldFrames: Int, val late: Int, val frames: Int, val lowLatency: Boolean,
    )

    private fun decode(
        name: String, source: MediaFormat, samples: List<Sample>, loops: Int, surface: Surface?, paceFps: Int,
    ): Result {
        val codec = MediaCodec.createByCodecName(name)
        val mime = source.getString(MediaFormat.KEY_MIME)!!
        val lowLatency = Build.VERSION.SDK_INT >= 30 &&
            codec.codecInfo.getCapabilitiesForType(mime).isFeatureSupported("low-latency")
        val format = MediaFormat.createVideoFormat(mime, source.getInteger(MediaFormat.KEY_WIDTH), source.getInteger(MediaFormat.KEY_HEIGHT))
        for (key in listOf("csd-0", "csd-1", "csd-2")) {
            if (source.containsKey(key)) format.setByteBuffer(key, source.getByteBuffer(key))
        }
        format.setInteger(MediaFormat.KEY_PRIORITY, 0)
        if (lowLatency) format.setInteger("low-latency", 1)
        if (paceFps == 0) format.setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())
        try {
            codec.configure(format, surface, null, 0)
        } catch (t: Throwable) {
            // Some decoders reject the operating-rate hint; try once without it.
            codec.reset()
            format.removeKey(MediaFormat.KEY_OPERATING_RATE)
            codec.configure(format, surface, null, 0)
        }
        codec.start()

        val total = samples.size * loops
        val frameUs = if (paceFps > 0) 1_000_000L / paceFps else 1_000_000L / 120
        val queuedAt = LongArray(total)
        val latencies = ArrayList<Long>(total)
        val info = MediaCodec.BufferInfo()
        var fed = 0
        var out = 0
        var late = 0
        var maxInFlight = 0
        val start = SystemClock.elapsedRealtimeNanos()
        val deadline = start + 30_000_000_000L
        var nextFeedNs = start

        try {
            while (out < total && SystemClock.elapsedRealtimeNanos() < deadline) {
                val now = SystemClock.elapsedRealtimeNanos()
                if (fed < total && (paceFps == 0 || now >= nextFeedNs)) {
                    val idx = codec.dequeueInputBuffer(0)
                    if (idx >= 0) {
                        val s = samples[fed % samples.size]
                        val buf = codec.getInputBuffer(idx)!!
                        buf.clear()
                        buf.put(s.data)
                        queuedAt[fed] = SystemClock.elapsedRealtimeNanos()
                        codec.queueInputBuffer(idx, 0, s.data.size, fed * frameUs, if (s.keyframe) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                        fed++
                        if (paceFps > 0) nextFeedNs += 1_000_000_000L / paceFps
                        maxInFlight = maxOf(maxInFlight, fed - out)
                    }
                }
                val o = codec.dequeueOutputBuffer(info, if (paceFps > 0) 200 else 1000)
                if (o >= 0) {
                    val frame = (info.presentationTimeUs / frameUs).toInt()
                    if (frame in 0 until total) {
                        val lat = (SystemClock.elapsedRealtimeNanos() - queuedAt[frame]) / 1000
                        latencies += lat
                        if (paceFps > 0 && lat > 1_000_000L / paceFps) late++
                    }
                    codec.releaseOutputBuffer(o, surface != null && paceFps > 0)
                    out++
                }
            }
        } finally {
            codec.stop()
            codec.release()
        }
        val elapsedS = (SystemClock.elapsedRealtimeNanos() - start) / 1e9
        val sorted = latencies.sorted()
        return Result(
            fps = out / elapsedS,
            avgUs = if (sorted.isEmpty()) 0 else sorted.sum() / sorted.size,
            p95Us = if (sorted.isEmpty()) 0 else sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.size - 1)],
            maxUs = sorted.lastOrNull() ?: 0,
            heldFrames = (maxInFlight - 1).coerceAtLeast(0),
            late = late,
            frames = out,
            lowLatency = lowLatency,
        )
    }
}
