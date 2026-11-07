package eu.anifantakis.myapplication

import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log

object LaunchTrace {
    private const val TAG = "LaunchDemo"
    private val seen = mutableSetOf<String>()
    private val keep = listOf(
        "com.android.internal.os.", "java.lang.reflect.Method", "android.os.Looper", "android.os.Handler",
        "android.app.", "android.view.Choreographer", "android.view.ViewRootImpl",
        "androidx.compose.ui.platform.AbstractComposeView", "eu.anifantakis.",
    )

    /**
     * API 33+: the moment the system asked for this process — close to the start of the launch,
     * so our "+ms" roughly lines up with ActivityTaskManager's "Displayed +…ms".
     * Older devices: the moment the process started binding (later, so the numbers come out smaller).
     */
    private fun startUptime(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Process.getStartRequestedUptimeMillis()
        else Process.getStartUptimeMillis()

    fun mark(step: String, showPath: Boolean = false) {
        val t = SystemClock.uptimeMillis() - startUptime()
        val head = "▶ $step   [pid ${Process.myPid()} · ${Thread.currentThread().name} · +${t}ms]"
        Log.i(TAG, if (showPath) "$head\n${callPath()}" else head)
    }

    fun markOnce(step: String, showPath: Boolean = false) {
        if (seen.add(step)) mark(step, showPath)
    }

    private fun callPath(): String = Throwable().stackTrace
        .filter { f -> keep.any { f.className.startsWith(it) } && !f.className.startsWith(LaunchTrace::class.java.name) }
        .map { "${it.className.substringAfterLast('.')}.${it.methodName}" }
        .distinct()
        .reversed()
        .joinToString("\n") { "      ↳ $it" }
}

/**
 * "ContextImpl@3f2a1b" — class name plus identity, so two different Context "engines"
 * (the Application's and the Activity's) look different in the log.
 */
fun Context.describe(): String =
    "${javaClass.simpleName}@${Integer.toHexString(System.identityHashCode(this))}"