package app.cloudlight.tv.stream

import org.webrtc.RTCStats
import org.webrtc.RTCStatsReport

/** One second of stream health, ready to show. Null fields are not known yet. */
data class StreamStats(
    val fps: Double? = null,
    val bitrateMbps: Double? = null,
    val rttMs: Double? = null,
    val packetLossPct: Double? = null,
    val jitterMs: Double? = null,
    val jitterBufferMs: Double? = null,
    val decodeMs: Double? = null,
    val framesDropped: Long = 0,
    val framesDecoded: Long = 0,
    val packetsLost: Long = 0,
    val nacks: Long = 0,
    val keyframeRequests: Long = 0,
    val freezes: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val codec: String? = null,
    val decoder: String? = null,
    val availableMbps: Double? = null,
    val transport: String? = null,
    val serverAddress: String? = null,
    val audioLossPct: Double? = null,
    val quality: Quality = Quality.UNKNOWN,
) {
    enum class Quality(val label: String) { UNKNOWN("Measuring"), GREAT("Great"), GOOD("Good"), FAIR("Fair"), POOR("Poor") }

    companion object {
        val Empty = StreamStats()
    }
}

/** Turns successive WebRTC stats reports into per-second rates. */
class StatsReader {
    private var lastAtUs = 0.0
    private var lastBytes = 0.0
    private var lastFramesDecoded = 0.0
    private var lastDecodeTime = 0.0
    private var lastPacketsReceived = 0.0
    private var lastPacketsLost = 0.0
    private var lastJbDelay = 0.0
    private var lastJbCount = 0.0
    private var lastAudioReceived = 0.0
    private var lastAudioLost = 0.0

    private fun num(s: RTCStats?, key: String): Double? = (s?.members?.get(key) as? Number)?.toDouble()
    private fun str(s: RTCStats?, key: String): String? = s?.members?.get(key) as? String

    fun read(report: RTCStatsReport): StreamStats {
        val all = report.statsMap
        val video = all.values.firstOrNull { it.type == "inbound-rtp" && str(it, "kind") == "video" }
        val audio = all.values.firstOrNull { it.type == "inbound-rtp" && str(it, "kind") == "audio" }
        val transport = all.values.firstOrNull { it.type == "transport" }
        val pair = str(transport, "selectedCandidatePairId")?.let { all[it] }
            ?: all.values.firstOrNull { it.type == "candidate-pair" && (it.members["nominated"] as? Boolean) == true && str(it, "state") == "succeeded" }
        val local = str(pair, "localCandidateId")?.let { all[it] }
        val remote = str(pair, "remoteCandidateId")?.let { all[it] }
        val codec = str(video, "codecId")?.let { all[it] }

        val now = report.timestampUs
        val dt = if (lastAtUs > 0) (now - lastAtUs) / 1e6 else 0.0
        lastAtUs = now

        val bytes = num(video, "bytesReceived") ?: 0.0
        val decoded = num(video, "framesDecoded") ?: 0.0
        val decodeTime = num(video, "totalDecodeTime") ?: 0.0
        val received = num(video, "packetsReceived") ?: 0.0
        val lost = num(video, "packetsLost") ?: 0.0
        val jb = num(video, "jitterBufferDelay") ?: 0.0
        val jbCount = num(video, "jitterBufferEmittedCount") ?: 0.0
        val aReceived = num(audio, "packetsReceived") ?: 0.0
        val aLost = num(audio, "packetsLost") ?: 0.0

        fun rate(cur: Double, prev: Double) = if (dt > 0 && cur >= prev) (cur - prev) / dt else null
        fun loss(r: Double, l: Double, pr: Double, pl: Double): Double? {
            val dl = l - pl
            val total = (r - pr) + dl
            return if (dt > 0 && total > 0) (dl.coerceAtLeast(0.0) / total * 100) else null
        }

        val bitrate = rate(bytes, lastBytes)?.let { it * 8 / 1e6 }
        val decodedFps = rate(decoded, lastFramesDecoded)
        val decodeMs = if (decoded > lastFramesDecoded) (decodeTime - lastDecodeTime) / (decoded - lastFramesDecoded) * 1000 else null
        val jbMs = if (jbCount > lastJbCount) (jb - lastJbDelay) / (jbCount - lastJbCount) * 1000 else null
        val pktLoss = loss(received, lost, lastPacketsReceived, lastPacketsLost)
        val audioLoss = loss(aReceived, aLost, lastAudioReceived, lastAudioLost)

        lastBytes = bytes
        lastFramesDecoded = decoded
        lastDecodeTime = decodeTime
        lastPacketsReceived = received
        lastPacketsLost = lost
        lastJbDelay = jb
        lastJbCount = jbCount
        lastAudioReceived = aReceived
        lastAudioLost = aLost

        val rtt = num(pair, "currentRoundTripTime")?.let { it * 1000 }
        val fps = num(video, "framesPerSecond") ?: decodedFps
        val proto = str(local, "protocol")?.uppercase()
        val kind = when (str(local, "candidateType")) {
            "relay" -> "relayed"
            "srflx", "prflx" -> "direct"
            "host" -> "direct"
            else -> null
        }
        val network = str(local, "networkType")?.let { if (it == "ethernet") "Ethernet" else if (it == "wifi") "Wi-Fi" else null }
        val addr = str(remote, "address")?.let { a -> num(remote, "port")?.let { "$a:${it.toInt()}" } ?: a }

        val quality = when {
            rtt == null && pktLoss == null -> StreamStats.Quality.UNKNOWN
            (rtt ?: 0.0) > 90 || (pktLoss ?: 0.0) > 3 -> StreamStats.Quality.POOR
            (rtt ?: 0.0) > 60 || (pktLoss ?: 0.0) > 1 -> StreamStats.Quality.FAIR
            (rtt ?: 0.0) > 35 || (pktLoss ?: 0.0) > 0.3 -> StreamStats.Quality.GOOD
            else -> StreamStats.Quality.GREAT
        }

        return StreamStats(
            fps = fps,
            bitrateMbps = bitrate,
            rttMs = rtt,
            packetLossPct = pktLoss,
            jitterMs = num(video, "jitter")?.let { it * 1000 },
            jitterBufferMs = jbMs,
            decodeMs = decodeMs,
            framesDropped = num(video, "framesDropped")?.toLong() ?: 0,
            framesDecoded = decoded.toLong(),
            packetsLost = lost.toLong(),
            nacks = num(video, "nackCount")?.toLong() ?: 0,
            keyframeRequests = ((num(video, "pliCount") ?: 0.0) + (num(video, "firCount") ?: 0.0)).toLong(),
            freezes = num(video, "freezeCount")?.toLong() ?: 0,
            width = VideoOutput.width.takeIf { it > 0 } ?: num(video, "frameWidth")?.toInt() ?: 0,
            height = VideoOutput.height.takeIf { it > 0 } ?: num(video, "frameHeight")?.toInt() ?: 0,
            codec = str(codec, "mimeType")?.substringAfter('/')?.uppercase()?.let { if (it == "H265") "HEVC" else it },
            decoder = VideoOutput.decoderName ?: str(video, "decoderImplementation"),
            availableMbps = num(pair, "availableIncomingBitrate")?.let { it / 1e6 },
            transport = listOfNotNull(proto, kind, network).joinToString(" · ").ifBlank { null },
            serverAddress = addr,
            audioLossPct = audioLoss,
            quality = quality,
        )
    }
}
