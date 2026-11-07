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
    data class Problem(val whenMs: Long, val summary: String, val detail: String)

    private const val FILE = "last-problem.txt"
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
            // Only an ANR trace is text; a native crash's is a binary tombstone.
            val trace = if (e.reason != ApplicationExitInfo.REASON_ANR) null else runCatching {
                e.traceInputStream?.use { it.readBytes().take(6000).toByteArray().toString(Charsets.UTF_8) }
            }.getOrNull()
            Problem(e.timestamp, listOfNotNull(kind, e.description?.take(120)).joinToString(": "), trace ?: e.description.orEmpty())
        }
    }

    fun format(ms: Long): String = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(ms))
}

/** The most recent quality the session service turned down, and why, for the About page. */
object Diagnostics {
    @Volatile var lastRefusal: String? = null
}
