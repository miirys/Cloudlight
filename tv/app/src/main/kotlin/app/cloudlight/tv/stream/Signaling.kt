package app.cloudlight.tv.stream

import app.cloudlight.tv.data.Nvidia
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * The WebSocket that sets up the stream: the rig sends its offer and network candidates, we
 * send our answer and ours. Messages between the two peers ride inside `peer_msg` envelopes,
 * and anything with an `ackid` is acknowledged.
 */
class Signaling(
    private val url: String,
    private val sessionId: String,
    private val events: Events,
) {
    interface Events {
        fun onOpen()
        fun onOffer(sdp: String)
        fun onRemoteCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int)
        fun onClosed(reason: String)
    }

    private val peerName = "peer-${Random.nextLong(1, 10_000_000_000L)}"
    private var ack = 0
    private var socket: WebSocket? = null
    private var beat: ScheduledFuture<*>? = null
    @Volatile private var closed = false

    private fun nextAck() = synchronized(this) { ++ack }

    fun connect() {
        val base = url.replace(Regex("^wss://"), "https://").replace(Regex("^ws://"), "http://").trimEnd('/')
        val req = Request.Builder()
            .url("$base/sign_in?peer_id=$peerName&version=2")
            .header("Sec-WebSocket-Protocol", "x-nv-sessionid.$sessionId")
            .header("Origin", "https://play.geforcenow.com")
            .header("User-Agent", UA)
            .build()
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = guard {
                send(
                    JSONObject().put("ackid", nextAck()).put(
                        "peer_info",
                        JSONObject()
                            .put("browser", "Chrome")
                            .put("browserVersion", "131")
                            .put("connected", true)
                            .put("id", SELF)
                            .put("name", peerName)
                            .put("peerRole", 0)
                            .put("resolution", "1920x1080")
                            .put("version", 2),
                    ),
                )
                beat = timer.scheduleWithFixedDelay({ guard { send(JSONObject().put("hb", 1)) } }, 5, 5, TimeUnit.SECONDS)
                events.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) = guard { handle(text) }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = guard {
                webSocket.close(1000, null)
                finish(reason.ifBlank { "closed ($code)" })
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = guard {
                android.util.Log.w("Signaling", "failed (${response?.code})", t)
                finish(t.message ?: "connection failed")
            }
        })
    }

    /** OkHttp delivers these on its own threads, where an escaping exception ends the app. */
    private inline fun guard(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            android.util.Log.e("Signaling", "callback failed", t)
        }
    }

    private fun handle(text: String) {
        val j = runCatching { JSONObject(text) }.getOrNull() ?: return
        if (j.has("ackid")) {
            val fromSelf = j.optJSONObject("peer_info")?.optInt("id", -1) == SELF
            if (!fromSelf) send(JSONObject().put("ack", j.optInt("ackid")))
        }
        if (j.optInt("hb", 0) != 0) send(JSONObject().put("hb", 1))
        val msg = j.optJSONObject("peer_msg")?.optString("msg")?.takeIf { it.isNotBlank() } ?: return
        val p = runCatching { JSONObject(msg) }.getOrNull() ?: return
        when {
            p.optString("type") == "offer" && p.has("sdp") -> events.onOffer(p.getString("sdp"))
            p.has("candidate") -> {
                val c = p.optString("candidate")
                if (c.isNotBlank()) {
                    val mid = if (p.has("sdpMid") && !p.isNull("sdpMid")) p.optString("sdpMid") else null
                    events.onRemoteCandidate(c, mid, p.optInt("sdpMLineIndex", 0))
                }
            }
        }
    }

    private fun peer(msg: JSONObject) {
        send(JSONObject().put("peer_msg", JSONObject().put("from", SELF).put("to", 1).put("msg", msg.toString())).put("ackid", nextAck()))
    }

    fun sendAnswer(sdp: String, nvstSdp: String) = peer(JSONObject().put("type", "answer").put("sdp", sdp).put("nvstSdp", nvstSdp))

    fun sendCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) =
        peer(JSONObject().put("candidate", candidate).put("sdpMid", sdpMid ?: JSONObject.NULL).put("sdpMLineIndex", sdpMLineIndex))

    fun requestKeyframe(reason: String) =
        peer(JSONObject().put("type", "request_keyframe").put("reason", reason).put("backlogFrames", 0).put("attempt", 1))

    private fun send(j: JSONObject) {
        if (!closed) socket?.send(j.toString())
    }

    private fun finish(reason: String) {
        if (closed) return
        closed = true
        beat?.cancel(false)
        events.onClosed(reason)
    }

    fun close() {
        if (closed) return
        closed = true
        beat?.cancel(false)
        socket?.close(1000, "bye")
    }

    companion object {
        private const val SELF = 2
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0.0.0 Safari/537.36"
        private val timer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "signaling-hb").apply { isDaemon = true } }

        /** The shared client with standard certificate checks, kept open for as long as the stream runs. */
        private val client: OkHttpClient by lazy {
            Nvidia.http.newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()
        }
    }
}
