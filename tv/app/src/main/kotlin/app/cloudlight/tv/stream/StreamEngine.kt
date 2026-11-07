package app.cloudlight.tv.stream

import android.content.Context
import android.media.AudioAttributes
import android.os.SystemClock
import android.util.Log
import app.cloudlight.tv.data.CloudSession
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SoftwareVideoEncoderFactory
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** The process-wide WebRTC factory: our direct decoder for video, low-latency stereo audio. */
object Rtc {
    private var factory: PeerConnectionFactory? = null

    /** Shared GL context for the compatible video path's decoder and renderer. */
    val egl: org.webrtc.EglBase by lazy { org.webrtc.EglBase.create() }

    private const val TRIALS = "WebRTC-Video-H26xPacketBuffer/Enabled/WebRTC-ZeroPlayoutDelay/min_pacing:0ms,max_decode_queue_size:8/"

    @Synchronized
    fun factory(context: Context): PeerConnectionFactory {
        factory?.let { return it }
        val app = context.applicationContext
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(app).setFieldTrials(TRIALS).createInitializationOptions(),
        )
        val audio = JavaAudioDeviceModule.builder(app)
            .setUseStereoOutput(true)
            // The low-latency output path makes some TV audio drivers throw inside WebRTC's
            // native audio thread, which ends the app. The standard path is safe everywhere.
            .setUseLowLatency(false)
            .setUseHardwareAcousticEchoCanceler(false)
            .setUseHardwareNoiseSuppressor(false)
            .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                override fun onWebRtcAudioTrackInitError(message: String?) { Log.w("Rtc", "audio init: $message") }
                override fun onWebRtcAudioTrackStartError(code: JavaAudioDeviceModule.AudioTrackStartErrorCode?, message: String?) { Log.w("Rtc", "audio start: $message") }
                override fun onWebRtcAudioTrackError(message: String?) { Log.w("Rtc", "audio: $message") }
            })
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            .createAudioDeviceModule()
        return PeerConnectionFactory.builder()
            .setAudioDeviceModule(audio)
            .setVideoDecoderFactory(TvDecoderFactory())
            .setVideoEncoderFactory(SoftwareVideoEncoderFactory())
            .createPeerConnectionFactory()
            .also { factory = it }
    }
}

/**
 * One live stream: the peer connection to the rig, its input channels and its statistics.
 * Everything that touches the connection runs on one worker thread, in order.
 */
class StreamEngine(
    private val context: Context,
    private val session: CloudSession,
    private val config: Config,
    private val listener: Listener,
) {
    data class Config(
        val width: Int,
        val height: Int,
        val fps: Int,
        val maxKbps: Int,
        /** "H265", "H264", "AV1" or "AUTO". */
        val codec: String,
        val bitDepth: Int,
    )

    interface Listener {
        fun onProgress(step: String)
        fun onConnected()
        fun onFirstFrame()
        fun onInputReady()
        fun onStats(stats: StreamStats)
        fun onTimeWarning(code: Int, secondsLeft: Int?)
        fun onClosed(reason: String, error: Boolean)
    }

    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "stream").apply { priority = Thread.MAX_PRIORITY } }
    private var pc: PeerConnection? = null
    private var signaling: Signaling? = null
    private var input: DataChannel? = null
    private var partial: DataChannel? = null
    private var control: DataChannel? = null
    private var audio: AudioTrack? = null
    private var videoTrack: VideoTrack? = null
    private val pendingCandidates = ArrayList<IceCandidate>()
    private var remoteSet = false
    private var gathered: CountDownLatch? = null
    private var heartbeat: ScheduledFuture<*>? = null
    private var statsTask: ScheduledFuture<*>? = null
    private val statsReader = StatsReader()
    @Volatile private var closed = false
    @Volatile private var lastKeyframeAt = 0L

    val encoder = InputEncoder()
    @Volatile var inputReady = false
        private set

    /** What actually got negotiated, for the stats overlay. */
    @Volatile var codec: String? = null
        private set

    /**
     * The direct path renders inside the decoder, so this sees only tiny placeholder frames.
     * In compatible mode the real pictures arrive here and go on to the screen's renderer.
     */
    private var compatFrames = 0L
    private val sink = VideoSink { frame ->
        if (!VideoOutput.compatible) return@VideoSink
        try {
            VideoOutput.renderer?.onFrame(frame)
            VideoOutput.rendered++
            VideoOutput.width = frame.rotatedWidth
            VideoOutput.height = frame.rotatedHeight
            if (compatFrames++ == 0L) VideoOutput.onFirstFrame?.invoke()
        } catch (t: Throwable) {
            Log.w(TAG, "render failed", t)
        }
    }

    /**
     * Queues [block] on the worker. Work that arrives after shutdown is dropped, and a failure
     * inside it ends the stream with a message instead of taking the app down: most of these
     * calls come from WebRTC's native threads, where an escaping exception is fatal.
     */
    private fun post(block: () -> Unit) {
        if (worker.isShutdown) return
        try {
            worker.execute { guarded(block) }
        } catch (_: RejectedExecutionException) {
        }
    }

    private fun every(seconds: Long, block: () -> Unit): ScheduledFuture<*>? = try {
        worker.scheduleWithFixedDelay({ guarded(block) }, seconds, seconds, TimeUnit.SECONDS)
    } catch (_: RejectedExecutionException) {
        null
    }

    private fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.e(TAG, "stream task failed", t)
            if (!closed) fail("The stream stopped because of an internal error (${t.javaClass.simpleName}).")
        }
    }

    /** For callbacks on WebRTC and OkHttp threads: never let an exception escape. */
    private inline fun safe(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.e(TAG, "$what failed", t)
        }
    }

    fun start() {
        VideoOutput.rendered = 0
        VideoOutput.width = 0
        VideoOutput.height = 0
        VideoOutput.keyframeRequester = { requestKeyframe("decoder") }
        VideoOutput.onFirstFrame = { listener.onFirstFrame() }
        post { open() }
    }

    private fun open() {
        try {
            listener.onProgress("Connecting to the rig")
            val factory = Rtc.factory(context)
            val servers = session.ice.map { spec ->
                PeerConnection.IceServer.builder(spec.urls).apply {
                    spec.username?.let(::setUsername)
                    spec.credential?.let(::setPassword)
                }.createIceServer()
            }.ifEmpty {
                listOf(
                    PeerConnection.IceServer.builder("stun:s1.stun.gamestream.nvidia.com:19308").createIceServer(),
                    PeerConnection.IceServer.builder(listOf("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302")).createIceServer(),
                )
            }
            val rtc = PeerConnection.RTCConfiguration(servers).apply {
                bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
                keyType = PeerConnection.KeyType.ECDSA
            }
            pc = factory.createPeerConnection(rtc, observer) ?: throw IllegalStateException("no peer connection")
            val url = session.signalingUrl ?: throw IllegalStateException("The rig didn't say where to connect.")
            signaling = Signaling(url, session.id, signalingEvents).also { it.connect() }
        } catch (t: Throwable) {
            fail("Couldn't set up the stream: ${t.message ?: "unknown error"}")
        }
    }

    private val signalingEvents = object : Signaling.Events {
        override fun onOpen() = safe("open") { listener.onProgress("Waiting for the rig") }
        override fun onOffer(sdp: String) = post { answer(sdp) }
        override fun onRemoteCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) = post {
            val c = IceCandidate(sdpMid ?: sdpMLineIndex.toString(), sdpMLineIndex, candidate)
            if (remoteSet) pc?.addIceCandidate(c) else pendingCandidates += c
        }
        override fun onClosed(reason: String) = safe("signaling close") {
            if (!closed && !inputReady) fail("The rig closed the connection ($reason).")
        }
    }

    private fun pickCodec(offer: String): String {
        val offered = Sdp.offeredCodecs(offer)
        val decodable = { name: String -> TvDecoderFactory.mimeOf(name)?.let { TvDecoderFactory.find(it) } != null }
        val wanted = config.codec.uppercase()
        if (wanted != "AUTO" && wanted in offered && decodable(wanted)) return wanted
        return listOf("H265", "AV1", "H264").firstOrNull { it in offered && decodable(it) } ?: "H264"
    }

    private fun answer(offerSdp: String) {
        val pc = pc ?: return
        try {
            listener.onProgress("Negotiating the stream")
            val serverIp = session.media?.ip ?: session.serverIp.orEmpty()
            var offer = if (serverIp.isNotEmpty()) Sdp.fixServerIp(offerSdp, serverIp) else offerSdp
            val serverUfrag = Sdp.value(offer, "a=ice-ufrag:").orEmpty()
            val threshold = Sdp.partialReliableThreshold(offer) ?: 300
            val chosen = pickCodec(offer)
            offer = Sdp.keepCodec(offer, chosen)

            // Input channels, before the answer, so they ride the offer's data section.
            input = pc.createDataChannel("input_channel_v1", DataChannel.Init().apply { ordered = true })?.also { ch ->
                ch.registerObserver(object : DataChannel.Observer {
                    override fun onBufferedAmountChange(previous: Long) = Unit
                    override fun onStateChange() = Unit
                    override fun onMessage(buffer: DataChannel.Buffer) = safe("input message") { handshake(buffer.data) }
                })
            }
            partial = pc.createDataChannel(
                "input_channel_partially_reliable",
                DataChannel.Init().apply {
                    ordered = false
                    maxRetransmitTimeMs = threshold
                },
            )

            await("offer") { obs -> pc.setRemoteDescription(obs, SessionDescription(SessionDescription.Type.OFFER, offer)) }
            remoteSet = true
            // Receive only: the rig's microphone line would otherwise start recording, which
            // needs a permission we don't ask for and fails on some TVs.
            for (t in pc.transceivers) {
                runCatching {
                    val type = t.mediaType
                    if (type == org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO || type == org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO) {
                        t.direction = RtpTransceiver.RtpTransceiverDirection.RECV_ONLY
                    }
                }
            }
            pendingCandidates.forEach { pc.addIceCandidate(it) }
            pendingCandidates.clear()

            var created: SessionDescription? = null
            await("answer") { obs ->
                pc.createAnswer(
                    object : SdpObserver by obs {
                        override fun onCreateSuccess(sdp: SessionDescription) {
                            created = sdp
                            obs.onSetSuccess()
                        }
                    },
                    MediaConstraints(),
                )
            }
            val local = SessionDescription(SessionDescription.Type.ANSWER, Sdp.mungeAnswer(created!!.description, config.maxKbps))
            gathered = CountDownLatch(1)
            await("local") { obs -> pc.setLocalDescription(obs, local) }
            gathered?.await(5, TimeUnit.SECONDS)

            val final = pc.localDescription?.description ?: local.description
            codec = Sdp.negotiatedCodec(final) ?: chosen
            val nvst = Sdp.nvst(
                Sdp.StreamParams(
                    width = config.width,
                    height = config.height,
                    viewportWidth = config.width,
                    viewportHeight = config.height,
                    fps = config.fps,
                    maxKbps = config.maxKbps,
                    codec = codec ?: chosen,
                    bitDepth = if (codec == "H264") 8 else config.bitDepth,
                    partialReliableMs = threshold,
                    ice = Sdp.credentials(final),
                ),
            )
            signaling?.sendAnswer(final, nvst)

            // The rig runs ICE-lite and may not send candidates; tell ICE where it is.
            session.media?.let { m ->
                val ip = Sdp.publicIp(m.ip) ?: m.ip
                val ufrag = if (serverUfrag.isNotEmpty()) " generation 0 ufrag $serverUfrag" else ""
                pc.addIceCandidate(IceCandidate("0", 0, "candidate:1 1 udp 2130706431 $ip ${m.port} typ host$ufrag"))
                pc.addIceCandidate(IceCandidate("0", 0, "candidate:2 1 tcp 1518149375 $ip ${m.port} typ host tcptype active$ufrag"))
            }
            listener.onProgress("Starting the video")
            statsTask = every(1) { pollStats() }
        } catch (t: Throwable) {
            Log.e(TAG, "negotiation failed", t)
            fail("The stream couldn't be negotiated: ${t.message ?: "unknown error"}")
        }
    }

    /** Runs one SDP step and waits for it, so the negotiation reads top to bottom. */
    private fun await(what: String, step: (SdpObserver) -> Unit) {
        val done = CountDownLatch(1)
        var error: String? = null
        val obs = object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) = done.countDown()
            override fun onSetSuccess() = done.countDown()
            override fun onCreateFailure(reason: String?) {
                error = reason ?: "failed"
                done.countDown()
            }
            override fun onSetFailure(reason: String?) {
                error = reason ?: "failed"
                done.countDown()
            }
        }
        step(obs)
        if (!done.await(10, TimeUnit.SECONDS)) throw IllegalStateException("$what timed out")
        error?.let { throw IllegalStateException("$what: $it") }
    }

    /** The server opens input with its protocol version; from then on we may send. */
    private fun handshake(data: ByteBuffer) {
        if (inputReady || data.remaining() < 2) return
        val b = ByteArray(data.remaining()).also { data.get(it) }
        val first = (b[0].toInt() and 0xff) or ((b[1].toInt() and 0xff) shl 8)
        val version = when {
            first == 526 -> if (b.size >= 4) (b[2].toInt() and 0xff) or ((b[3].toInt() and 0xff) shl 8) else 2
            b[0].toInt() == 0x0e -> first
            else -> return
        }
        encoder.version = version
        inputReady = true
        heartbeat = every(2) { sendReliable(encoder.heartbeat()) }
        listener.onInputReady()
    }

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) = Unit
        override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) = safe("connection change") {
            when (state) {
                PeerConnection.PeerConnectionState.CONNECTED -> listener.onConnected()
                PeerConnection.PeerConnectionState.FAILED -> fail("The connection to the rig was lost.")
                else -> Unit
            }
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = safe("gathering") {
            if (state == PeerConnection.IceGatheringState.COMPLETE) gathered?.countDown()
        }
        override fun onIceCandidate(c: IceCandidate) = safe("candidate") {
            signaling?.sendCandidate(c.sdp, c.sdpMid, c.sdpMLineIndex)
        }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
        override fun onAddStream(stream: MediaStream?) = Unit
        override fun onRemoveStream(stream: MediaStream?) = Unit
        override fun onDataChannel(channel: DataChannel) = safe("data channel") {
            if (channel.label() != "control_channel") return@safe
            control = channel
            channel.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(previous: Long) = Unit
                override fun onStateChange() = Unit
                override fun onMessage(buffer: DataChannel.Buffer) = safe("control message") { controlMessage(buffer.data) }
            })
        }
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) = Unit
        override fun onTrack(transceiver: RtpTransceiver) = safe("track") {
            when (val track = transceiver.receiver?.track()) {
                is VideoTrack -> {
                    videoTrack = track
                    track.addSink(sink)
                }
                is AudioTrack -> {
                    audio = track
                    applyVolume()
                }
            }
        }
    }

    private fun controlMessage(data: ByteBuffer) {
        val text = ByteArray(data.remaining()).also { data.get(it) }.toString(Charsets.UTF_8)
        val timer = runCatching { JSONObject(text).optJSONObject("timerNotification") }.getOrNull() ?: return
        val left = timer.optInt("secondsLeft", -1).takeIf { it >= 0 }
        listener.onTimeWarning(timer.optInt("code", 0), left)
    }

    private fun pollStats() {
        val pc = pc ?: return
        pc.getStats { report ->
            safe("stats") {
                val s = runCatching { statsReader.read(report) }.getOrNull() ?: return@safe
                listener.onStats(s.copy(codec = s.codec ?: codec))
            }
        }
    }

    // ---------- Input ----------

    private fun DataChannel.sendBytes(b: ByteArray) {
        if (state() == DataChannel.State.OPEN) send(DataChannel.Buffer(ByteBuffer.wrap(b), true))
    }

    fun sendReliable(b: ByteArray) {
        if (!closed) input?.sendBytes(b)
    }

    /** Controller reports go on the partially reliable channel: a late report is useless. */
    fun sendGamepad(slot: Int, connected: Int, buttons: Int, lt: Int, rt: Int, lx: Int, ly: Int, rx: Int, ry: Int) {
        if (!inputReady || closed) return
        val p = partial
        if (p != null && p.state() == DataChannel.State.OPEN) {
            p.sendBytes(encoder.gamepad(slot, connected, buttons, lt, rt, lx, ly, rx, ry, partiallyReliable = true))
        } else {
            sendReliable(encoder.gamepad(slot, connected, buttons, lt, rt, lx, ly, rx, ry, partiallyReliable = false))
        }
    }

    fun sendKey(down: Boolean, key: Keymap.Key, modifiers: Int = 0) {
        if (inputReady) sendReliable(encoder.key(down, key.vk, key.scan, modifiers))
    }

    fun sendMouseMove(dx: Int, dy: Int) {
        if (inputReady && (dx != 0 || dy != 0)) sendReliable(encoder.mouseMove(dx, dy))
    }

    fun sendMouseButton(down: Boolean, button: Int) {
        if (inputReady) sendReliable(encoder.mouseButton(down, button))
    }

    fun sendWheel(delta: Int) {
        if (inputReady && delta != 0) sendReliable(encoder.wheel(delta))
    }

    /** Taps a key combination: all down in order, then all up in reverse. */
    fun sendCombo(vararg keys: Keymap.Key) {
        post {
            var mods = 0
            for (k in keys) {
                if (k == Keymap.ALT) mods = mods or InputEncoder.MOD_ALT
                sendKey(true, k, mods)
                SystemClock.sleep(30)
            }
            for (k in keys.reversed()) {
                sendKey(false, k, mods)
                if (k == Keymap.ALT) mods = mods and InputEncoder.MOD_ALT.inv()
                SystemClock.sleep(20)
            }
        }
    }

    fun requestKeyframe(reason: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastKeyframeAt < 400) return
        lastKeyframeAt = now
        post {
            val c = control
            if (c != null && c.state() == DataChannel.State.OPEN) {
                val msg = JSONObject().put("type", "request_keyframe").put("reason", reason).put("backlogFrames", 0).put("attempt", 1)
                c.send(DataChannel.Buffer(ByteBuffer.wrap(msg.toString().toByteArray()), false))
            } else {
                signaling?.requestKeyframe(reason)
            }
        }
    }

    // ---------- Audio ----------

    @Volatile var volume = 1.0
        set(value) {
            field = value.coerceIn(0.0, 1.0)
            post { applyVolume() }
        }

    @Volatile var muted = false
        set(value) {
            field = value
            post { applyVolume() }
        }

    private fun applyVolume() {
        val a = audio ?: return
        runCatching {
            a.setEnabled(!muted)
            // WebRTC's gain runs 0 to 10; 1 is unchanged.
            a.setVolume(volume)
        }
    }

    // ---------- Shutdown ----------

    private fun fail(reason: String) {
        if (closed) return
        listener.onClosed(reason, error = true)
        stop()
    }

    fun stop() {
        if (closed) return
        closed = true
        VideoOutput.keyframeRequester = null
        VideoOutput.onFirstFrame = null
        try {
            worker.execute { shutdown() }
        } catch (_: RejectedExecutionException) {
        }
    }

    private fun shutdown() {
        try {
            heartbeat?.cancel(false)
            statsTask?.cancel(false)
            runCatching { signaling?.close() }
            runCatching { videoTrack?.removeSink(sink) }
            runCatching { input?.close() }
            runCatching { partial?.close() }
            runCatching { pc?.close() }
            runCatching { pc?.dispose() }
        } catch (t: Throwable) {
            Log.e(TAG, "shutdown failed", t)
        } finally {
            pc = null
            worker.shutdown()
        }
    }

    companion object {
        private const val TAG = "StreamEngine"
    }
}
