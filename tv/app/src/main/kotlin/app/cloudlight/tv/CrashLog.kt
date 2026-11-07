package app.cloudlight.tv

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the app alive when a background thread fails, and remembers why the app last closed
 * unexpectedly so the next launch can say so.
 */
object CrashLog {
    data class Problem(val whenMs: Long, val summary: String, val detail: String, val native: Boolean = false)

    private const val FILE = "last-problem.txt"
    private const val STREAMING = "streaming"
    private const val SEEN = "last-problem-seen"

    /** Called on the main thread when a background thread failed and was stopped. */
    @Volatile var onBackgroundFailure: ((Throwable) -> Unit)? = null

    @Volatile private var appContext: Context? = null
    @Volatile private var streamPath: String? = null
    @Volatile private var lastStep: String? = null

    fun install(context: Context) {
        val app = context.applicationContext
        appContext = app
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, error, thread.name) }
            if (thread === Looper.getMainLooper().thread) {
                previous?.uncaughtException(thread, error)
            } else {
                // A worker thread failed. Losing that thread is survivable; losing the app is not.
                Log.e("CrashLog", "background thread ${thread.name} failed", error)
                Handler(Looper.getMainLooper()).post { runCatching { onBackgroundFailure?.invoke(error) } }
            }
        }
    }

    private fun write(context: Context, error: Throwable, thread: String) {
        val text = buildString {
            append(System.currentTimeMillis()).append('\n')
            append(error.javaClass.simpleName).append(": ").append(error.message.orEmpty().take(160)).append('\n')
            append("thread ").append(thread).append('\n')
            append(Log.getStackTraceString(error).take(6000))
        }
        File(context.filesDir, FILE).writeText(text)
    }

    /** The most recent unexpected exit not yet shown, from our own record or from Android's. */
    fun take(context: Context): Problem? {
        val prefs = context.getSharedPreferences("crash", Context.MODE_PRIVATE)
        val seen = prefs.getLong(SEEN, 0)
        val candidates = ArrayList<Problem>()
        runCatching {
            val f = File(context.filesDir, FILE)
            if (f.exists()) {
                val lines = f.readLines()
                val at = lines.firstOrNull()?.toLongOrNull() ?: 0
                candidates += Problem(at, lines.getOrNull(1).orEmpty(), lines.drop(2).joinToString("\n"))
            }
        }
        if (Build.VERSION.SDK_INT >= 30) runCatching { candidates += exits(context) }
        val latest = candidates.filter { it.whenMs > seen }.maxByOrNull { it.whenMs } ?: return null
        prefs.edit().putLong(SEEN, latest.whenMs).apply()
        return latest
    }

    /** The newest problem whether or not it was shown, for the About page. */
    fun latest(context: Context): Problem? {
        val f = File(context.filesDir, FILE)
        val own = runCatching {
            if (!f.exists()) null else f.readLines().let { Problem(it[0].toLong(), it.getOrNull(1).orEmpty(), it.drop(2).joinToString("\n")) }
        }.getOrNull()
        val android = if (Build.VERSION.SDK_INT >= 30) runCatching { exits(context) }.getOrDefault(emptyList()) else emptyList()
        return (listOfNotNull(own) + android).maxByOrNull { it.whenMs }
    }

    @android.annotation.TargetApi(30)
    private fun exits(context: Context): List<Problem> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.getHistoricalProcessExitReasons(context.packageName, 0, 5).mapNotNull { e ->
            val kind = when (e.reason) {
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
                ApplicationExitInfo.REASON_ANR -> "Stopped responding"
                ApplicationExitInfo.REASON_LOW_MEMORY -> "Closed for low memory"
                ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "Failed to start"
                else -> return@mapNotNull null
            }
            val bytes = runCatching { e.traceInputStream?.use { it.readBytes() } }.getOrNull()
            if (e.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
                val (summary, detail) = nativeSummary(bytes, e.description)
                return@mapNotNull Problem(e.timestamp, summary, detail, native = true)
            }
            // An ANR trace is plain text.
            val trace = bytes?.take(6000)?.toByteArray()?.toString(Charsets.UTF_8)
            Problem(e.timestamp, listOfNotNull(kind, e.description?.take(120)).joinToString(": "), trace ?: e.description.orEmpty())
        }
    }

    /** Notes that a stream is starting on [path], so a crash can be traced back to it. */
    fun markStreaming(context: Context, path: String?) {
        streamPath = path
        lastStep = null
        runCatching {
            val f = File(context.filesDir, STREAMING)
            if (path == null) f.delete() else f.writeText("$path|start")
        }
    }

    /**
     * Records the stream setup step about to run. If the app goes down, the next launch knows
     * which part was at fault and can avoid it.
     */
    fun step(name: String) {
        val path = streamPath ?: return
        if (lastStep == name) return
        lastStep = name
        val ctx = appContext ?: return
        runCatching { File(ctx.filesDir, STREAMING).writeText("$path|$name") }
    }

    /** The video path and setup step a stream was on when the app last went down, if any. */
    fun takeStreamingMark(context: Context): Pair<String, String>? = runCatching {
        val f = File(context.filesDir, STREAMING)
        if (!f.exists()) null else f.readText().trim().also { f.delete() }.split('|').let { it[0] to it.getOrElse(1) { "start" } }
    }.getOrNull()

    /** Keeps the explained crash note for the About page. */
    fun saveNote(context: Context, text: String) {
        runCatching { File(context.filesDir, "last-problem-note.txt").writeText(text) }
    }

    fun note(context: Context): String? = runCatching {
        File(context.filesDir, "last-problem-note.txt").takeIf { it.exists() }?.readText()
    }.getOrNull()

    private const val LOG = "last-crash-log.txt"

    /**
     * Copies this app's recent system log lines that explain a crash: fatal messages, failed
     * checks, the crashing signal and its backtrace, and our own stream warnings. Android keeps
     * an app's log for a while after it goes down, and lets the app read its own lines.
     */
    fun captureLog(context: Context) {
        val lines = ArrayList<String>()
        for (buffers in listOf(listOf("main", "system"), listOf("crash"))) runCatching {
            val cmd = listOf("logcat", "-d", "-v", "brief", "-t", "3000") + buffers.flatMap { listOf("-b", it) }
            val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
            p.inputStream.bufferedReader().useLines { seq -> seq.filter(::telling).forEach { lines += it.take(300) } }
            p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            p.destroy()
        }
        if (lines.isEmpty()) return
        runCatching { File(context.filesDir, LOG).writeText(lines.distinct().takeLast(160).joinToString("\n")) }
    }

    private val tellingTags = listOf(
        "/DEBUG", "/libc", "/rtc", "/webrtc", "/Rtc", "/DirectDecoder", "/Decoders", "/CrashLog",
        "/AndroidRuntime", "/MediaCodec", "/ACodec", "/CCodec", "/OMXClient", "/SurfaceView", "/BufferQueue",
    )

    private fun telling(l: String): Boolean =
        l.startsWith("F/") || "Fatal" in l || "Check failed" in l || "Abort message" in l ||
            ((l.startsWith("E/") || l.startsWith("W/")) && tellingTags.any { l.startsWith(it, 1) }) ||
            (l.startsWith("I/DEBUG") || l.startsWith("I/crash_dump") || l.startsWith("F/DEBUG"))

    /** The saved log lines, or null. */
    fun crashLog(context: Context): List<String>? = runCatching {
        File(context.filesDir, LOG).takeIf { it.exists() }?.readLines()
    }.getOrNull()

    /**
     * The few lines that name the cause: the failed check or abort message, the signal, and
     * the top frames of the crashing thread.
     */
    fun keyLines(log: List<String>): List<String> {
        fun body(l: String) = l.substringAfter("): ", l).trim()
        val cause = log.filter { "Check failed" in it || "Abort message" in it || "Fatal error in" in it }.map(::body)
        val signal = log.filter { "Fatal signal" in it || "signal " in body(it) && "code " in body(it) }.map(::body)
        val frames = log.map(::body).filter { Regex("""^#0[0-5] pc """).containsMatchIn(it) }
            .map { f -> f.substringAfter(" pc ").substringAfter(' ').trim().substringBefore(" (BuildId").let { it.substringAfterLast('/').ifEmpty { it } } }
        val ours = log.filter { it.startsWith("E/") || it.startsWith("W/") }.map(::body).takeLast(3)
        return (cause.take(2) + signal.take(1) + frames.take(6) + ours).distinct().map { it.take(160) }.ifEmpty { log.takeLast(4).map(::body) }
    }

    /** Plain words for a setup step, for the crash note. */
    fun stepName(step: String) = when (step) {
        "start", "factory" -> "preparing the stream"
        "peer", "signaling", "offer", "answer" -> "connecting to the rig"
        "connected" -> "waiting for the first picture"
        "audio" -> "starting the sound"
        "decoder-create", "decoder-init", "decoding" -> "starting the video decoder"
        "video" -> "showing the video"
        "live" -> "playing"
        else -> step
    }

    /**
     * Android stores a native crash as a binary tombstone. Its readable strings are enough to
     * say which library failed and in which function.
     */
    private fun tombstoneStrings(bytes: ByteArray): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        fun flush() {
            if (sb.length >= 5) out += sb.toString()
            sb.setLength(0)
        }
        for (b in bytes) {
            val c = b.toInt() and 0xff
            if (c in 0x20..0x7e) sb.append(c.toChar()) else flush()
        }
        flush()
        return out
    }

    private fun nativeSummary(bytes: ByteArray?, fallback: String?): Pair<String, String> {
        if (bytes == null || bytes.isEmpty()) return "Native crash${fallback?.let { ": $it" }.orEmpty()}" to fallback.orEmpty()
        val strings = tombstoneStrings(bytes)
        val libs = strings.filter { it.endsWith(".so") || ".so " in it || ".so!" in it }
        val culprit = libs.map { it.substringAfterLast('/').substringBefore(' ') }
            .firstOrNull { it !in setOf("libc.so", "libart.so", "libbase.so", "liblog.so", "libutils.so") }
        val abort = strings.firstOrNull { it.startsWith("Abort message") || "Check failed" in it || "FATAL" in it }
        val summary = buildString {
            append("Native crash")
            if (culprit != null) append(" in ").append(culprit)
            if (abort != null) append(": ").append(abort.take(100))
        }
        val detail = (listOfNotNull(abort) + libs.take(12) + strings.filter { it.startsWith("_Z") || "::" in it }.take(12))
            .distinct().joinToString("\n")
        return summary to detail
    }

    fun format(ms: Long): String = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(ms))
}

/** The most recent quality the session service turned down, and why, for the About page. */
object Diagnostics {
    @Volatile var lastRefusal: String? = null
}
