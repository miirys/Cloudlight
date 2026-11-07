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

    fun install(context: Context) {
        val app = context.applicationContext
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
        runCatching {
            val f = File(context.filesDir, STREAMING)
            if (path == null) f.delete() else f.writeText(path)
        }
    }

    /** Which video path a stream was using when the app last went down, if one was. */
    fun takeStreamingMark(context: Context): String? = runCatching {
        val f = File(context.filesDir, STREAMING)
        if (!f.exists()) null else f.readText().trim().also { f.delete() }
    }.getOrNull()

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
