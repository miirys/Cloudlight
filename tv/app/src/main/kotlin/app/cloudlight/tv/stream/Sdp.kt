package app.cloudlight.tv.stream

/**
 * Text surgery on session descriptions. GeForce NOW's offers need a few fixes before a stock
 * WebRTC stack accepts them, and the answer travels with NVIDIA's own stream parameters.
 */
object Sdp {

    data class IceCredentials(val ufrag: String, val pwd: String, val fingerprint: String)

    private val dottedIp = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private val zeroCandidate = Regex("""(a=candidate:\S+\s+\d+\s+\w+\s+\d+\s+)0\.0\.0\.0(\s+)""")
    private val thresholdLine = Regex("""a=ri\.partialReliableThresholdMs:(\d+)""", RegexOption.IGNORE_CASE)

    /** "80-250-97-40.cloud.example" and "80.250.97.40" both mean 80.250.97.40. */
    fun publicIp(host: String): String? {
        if (dottedIp.matches(host)) return host
        val parts = host.substringBefore('.').split('-')
        return if (parts.size == 4 && parts.all { p -> p.length in 1..3 && p.all(Char::isDigit) }) parts.joinToString(".") else null
    }

    /** The server writes 0.0.0.0 where its address belongs; put the real one in. */
    fun fixServerIp(sdp: String, server: String): String {
        val ip = publicIp(server) ?: return sdp
        return zeroCandidate.replace(sdp.replace("c=IN IP4 0.0.0.0", "c=IN IP4 $ip"), "\$1$ip\$2")
    }

    fun value(sdp: String, prefix: String): String? =
        sdp.lineSequence().firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.trim()

    fun credentials(sdp: String) = IceCredentials(
        ufrag = value(sdp, "a=ice-ufrag:").orEmpty(),
        pwd = value(sdp, "a=ice-pwd:").orEmpty(),
        fingerprint = value(sdp, "a=fingerprint:sha-256 ").orEmpty(),
    )

    fun partialReliableThreshold(sdp: String): Int? =
        thresholdLine.find(sdp)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }

    private fun codecName(raw: String) = raw.uppercase().let { if (it == "HEVC") "H265" else it }

    private fun lines(sdp: String) = sdp.split("\r\n", "\n")

    private fun eol(sdp: String) = if (sdp.contains("\r\n")) "\r\n" else "\n"

    /** Video codecs the offer contains, by name (H264, H265, AV1…). */
    fun offeredCodecs(sdp: String): Set<String> {
        val out = HashSet<String>()
        var video = false
        for (l in lines(sdp)) {
            if (l.startsWith("m=")) video = l.startsWith("m=video")
            if (video && l.startsWith("a=rtpmap:")) {
                out += codecName(l.substringAfter(' ').substringBefore('/'))
            }
        }
        return out
    }

    /**
     * Leaves only [codec] and its retransmission streams in the video section, so the answer
     * cannot settle on anything else. HEVC is also asked for in the main tier, which every TV
     * decoder handles.
     */
    fun keepCodec(sdp: String, codec: String): String {
        val all = lines(sdp)
        val nameOf = HashMap<String, String>()
        val aptOf = HashMap<String, String>()
        var video = false
        for (l in all) {
            if (l.startsWith("m=")) video = l.startsWith("m=video")
            if (!video) continue
            if (l.startsWith("a=rtpmap:")) {
                val pt = l.removePrefix("a=rtpmap:").substringBefore(' ')
                nameOf[pt] = codecName(l.substringAfter(' ').substringBefore('/'))
            } else if (l.startsWith("a=fmtp:")) {
                val pt = l.removePrefix("a=fmtp:").substringBefore(' ')
                Regex("""(?:^|;)\s*apt=(\d+)""").find(l.substringAfter(' '))?.let { aptOf[pt] = it.groupValues[1] }
            }
        }
        val wanted = nameOf.filterValues { it == codec }.keys
        if (wanted.isEmpty()) return sdp
        val allowed = HashSet(wanted)
        for ((pt, apt) in aptOf) if (apt in wanted && nameOf[pt] == "RTX") allowed += pt

        val out = ArrayList<String>(all.size)
        video = false
        for (l in all) {
            if (l.startsWith("m=")) video = l.startsWith("m=video")
            if (video && l.startsWith("m=video")) {
                val parts = l.split(' ')
                val pts = parts.drop(3).filter { it in allowed }
                val ordered = pts.filter { it in wanted } + pts.filter { it !in wanted }
                out += if (ordered.isEmpty()) l else (parts.take(3) + ordered).joinToString(" ")
                continue
            }
            if (video && (l.startsWith("a=rtpmap:") || l.startsWith("a=fmtp:") || l.startsWith("a=rtcp-fb:"))) {
                val pt = l.substringAfter(':').substringBefore(' ')
                if (pt !in allowed) continue
                if (codec == "H265" && l.startsWith("a=fmtp:")) {
                    out += l.replace("tier-flag=1", "tier-flag=0")
                    continue
                }
            }
            out += l
        }
        return out.joinToString(eol(sdp))
    }

    /** The codec our answer settled on: the first payload type on the video line. */
    fun negotiatedCodec(sdp: String): String? {
        val all = lines(sdp)
        val pt = all.firstOrNull { it.startsWith("m=video") }?.split(' ')?.getOrNull(3) ?: return null
        return all.firstOrNull { it.startsWith("a=rtpmap:$pt ") }?.let { codecName(it.substringAfter(' ').substringBefore('/')) }
    }

    /** Tells the server how much we can take, and asks for stereo game audio. */
    fun mungeAnswer(sdp: String, videoKbps: Int): String {
        val all = lines(sdp)
        val out = ArrayList<String>(all.size + 4)
        for ((i, l) in all.withIndex()) {
            out += if (l.startsWith("a=fmtp:") && l.contains("minptime=") && !l.contains("stereo=1")) "$l;stereo=1" else l
            val video = l.startsWith("m=video")
            if ((video || l.startsWith("m=audio")) && all.getOrNull(i + 1)?.startsWith("b=") != true) {
                out += "b=AS:${if (video) videoKbps else 128}"
            }
        }
        return out.joinToString(eol(sdp))
    }

    data class StreamParams(
        val width: Int,
        val height: Int,
        val viewportWidth: Int,
        val viewportHeight: Int,
        val fps: Int,
        val maxKbps: Int,
        val codec: String,
        val bitDepth: Int,
        val partialReliableMs: Int,
        val ice: IceCredentials,
    )

    /**
     * NVIDIA's companion description. It carries the ICE credentials again plus the encoder
     * and pacing settings the server applies. We ask for a fixed resolution (no dynamic
     * downscaling), adaptive bitrate between 35 % and 100 % of the cap, and the high frame rate
     * tuning when 90 fps or more is requested.
     */
    fun nvst(p: StreamParams): String {
        val minKbps = maxOf(5_000, p.maxKbps * 35 / 100)
        val startKbps = maxOf(minKbps, p.maxKbps * 70 / 100)
        val highFps = p.fps >= 90
        val fps120 = p.fps == 120
        val av1 = p.codec == "AV1"
        val sb = StringBuilder(4096)
        fun a(s: String) = sb.append("a=").append(s).append('\n')

        sb.append("v=0\no=SdpTest test_id_13 14 IN IPv4 127.0.0.1\ns=-\nt=0 0\n")
        a("general.icePassword:${p.ice.pwd}")
        a("general.iceUserNameFragment:${p.ice.ufrag}")
        a("general.dtlsFingerprint:${p.ice.fingerprint}")

        sb.append("m=video 0 RTP/AVP\n")
        a("msid:fbc-video-0")
        a("vqos.fec.rateDropWindow:10")
        a("vqos.fec.minRequiredFecPackets:2")
        a("vqos.fec.repairMinPercent:5")
        a("vqos.fec.repairPercent:5")
        a("vqos.fec.repairMaxPercent:35")
        a("vqos.drc.enable:0")
        a("vqos.dfc.enable:0")
        a("video.dx9EnableNv12:1")
        a("video.dx9EnableHdr:1")
        a("vqos.qpg.enable:1")
        a("vqos.resControl.qp.qpg.featureSetting:7")
        a("bwe.useOwdCongestionControl:1")
        a("video.enableRtpNack:1")
        a("vqos.bw.txRxLag.minFeedbackTxDeltaMs:200")
        a("vqos.drc.bitrateIirFilterFactor:18")
        a("video.packetSize:1140")
        a("packetPacing.minNumPacketsPerGroup:15")
        if (highFps) {
            a("bwe.iirFilterFactor:8")
            a("video.encoderFeatureSetting:47")
            a("video.encoderPreset:6")
            a("vqos.resControl.cpmRtc.badNwSkipFramesCount:600")
            a("vqos.resControl.cpmRtc.decodeTimeThresholdMs:9")
            a("video.fbcDynamicFpsGrabTimeoutMs:${if (fps120) 6 else 18}")
            a("vqos.resControl.cpmRtc.serverResolutionUpdateCoolDownCount:${if (fps120) 6000 else 12000}")
        }
        if (p.fps >= 240) {
            a("video.enableNextCaptureMode:1")
            a("vqos.maxStreamFpsEstimate:240")
            a("video.videoSplitEncodeStripsPerFrame:3")
            a("video.updateSplitEncodeStateDynamically:1")
        }
        a("vqos.adjustStreamingFpsDuringOutOfFocus:1")
        a("vqos.resControl.cpmRtc.ignoreOutOfFocusWindowState:1")
        a("vqos.resControl.perfHistory.rtcIgnoreOutOfFocusWindowState:1")
        a("vqos.resControl.cpmRtc.featureMask:0")
        a("vqos.resControl.cpmRtc.enable:0")
        a("vqos.resControl.cpmRtc.minResolutionPercent:100")
        a("vqos.resControl.cpmRtc.resolutionChangeHoldonMs:999999")
        a("packetPacing.numGroups:${if (fps120) 3 else 5}")
        a("packetPacing.maxDelayUs:1000")
        a("packetPacing.minNumPacketsFrame:10")
        a("video.rtpNackQueueLength:1024")
        a("video.rtpNackQueueMaxPackets:512")
        a("video.rtpNackMaxPacketCount:25")
        a("vqos.drc.qpMaxResThresholdAdj:4")
        a("vqos.grc.qpMaxResThresholdAdj:4")
        a("vqos.drc.iirFilterFactor:100")
        if (av1) {
            for (prefix in listOf("drc", "grc")) {
                a("vqos.$prefix.minQpHeadroom:20")
                a("vqos.$prefix.lowerQpThreshold:100")
                a("vqos.$prefix.upperQpThreshold:200")
                a("vqos.$prefix.minAdaptiveQpThreshold:180")
                a("vqos.$prefix.qpCodecThresholdAdj:0")
                a("vqos.$prefix.qpMaxResThresholdAdj:20")
            }
            a("vqos.dfc.minQpHeadroom:20")
            a("vqos.dfc.qpLowerLimit:100")
            a("vqos.dfc.qpMaxUpperLimit:200")
            a("vqos.dfc.qpMinUpperLimit:180")
            a("vqos.dfc.qpMaxResThresholdAdj:20")
            a("vqos.dfc.qpCodecThresholdAdj:0")
            a("video.minQp:25")
            a("video.enableAv1RcPrecisionFactor:1")
        }
        a("video.clientViewportWd:${p.viewportWidth}")
        a("video.clientViewportHt:${p.viewportHeight}")
        a("video.maxFPS:${p.fps}")
        a("video.initialBitrateKbps:$startKbps")
        a("video.initialPeakBitrateKbps:${p.maxKbps}")
        a("vqos.bw.maximumBitrateKbps:${p.maxKbps}")
        a("vqos.bw.minimumBitrateKbps:$minKbps")
        a("vqos.bw.peakBitrateKbps:${p.maxKbps}")
        a("vqos.bw.serverPeakBitrateKbps:${p.maxKbps}")
        a("vqos.bw.enableBandwidthEstimation:1")
        a("vqos.bw.disableBitrateLimit:0")
        a("vqos.grc.maximumBitrateKbps:${p.maxKbps}")
        a("vqos.grc.enable:0")
        a("video.maxNumReferenceFrames:4")
        a("video.mapRtpTimestampsToFrames:1")
        a("video.encoderCscMode:3")
        a("video.dynamicRangeMode:0")
        a("video.bitDepth:${p.bitDepth}")
        a("video.scalingFeature1:${if (av1) 1 else 0}")
        a("video.prefilterParams.prefilterModel:0")

        sb.append("m=audio 0 RTP/AVP\n")
        a("msid:audio")
        sb.append("m=mic 0 RTP/AVP\n")
        a("msid:mic")
        a("rtpmap:0 PCMU/8000")
        sb.append("m=application 0 RTP/AVP\n")
        a("msid:input_1")
        a("ri.partialReliableThresholdMs:${p.partialReliableMs}")
        return sb.toString()
    }
}
