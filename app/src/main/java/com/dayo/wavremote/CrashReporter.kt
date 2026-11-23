package com.dayo.wavremote

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Saves the details of a crash so the next launch can show them (and they can be shared),
 * and keeps a crashing built-in Tailscale from crashing the app again and again.
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"
    private const val PREFS = "wavremote"
    private const val KEY_TS_STARTING = "tsStartingAt"
    private const val TS_GRACE_MS = 20_000L

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                save(app, "Thread: ${thread.name}\n$sw")
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun save(ctx: Context, details: String) {
        val header = "WavRemote ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), " +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n\n"
        File(ctx.filesDir, FILE).writeText(header + details.take(20_000))
    }

    /** The crash from last time, if any. Reading it clears it. */
    fun takeLastCrash(ctx: Context): String? {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return null
        val text = runCatching { f.readText() }.getOrNull()
        f.delete()
        return text?.takeIf { it.isNotBlank() }
    }

    // ---- built-in Tailscale crash guard

    /** Called just before Tailscale starts. Cleared by [tailscaleSurvived] once it has run a while. */
    fun tailscaleStarting(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_TS_STARTING, System.currentTimeMillis()).commit()
    }

    fun tailscaleSurvived(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_TS_STARTING).commit()
    }

    /**
     * True when the app died while Tailscale was starting last time. Tailscale's own crash
     * report (written by Go) is folded into the saved crash.
     */
    fun tailscaleCrashedLastTime(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_TS_STARTING)) {
            // A Tailscale crash later on (after it had started fine): keep the details, leave it on.
            val late = File(File(ctx.filesDir, "tailscale"), "crash.txt")
            val text = runCatching { late.readText() }.getOrNull().orEmpty()
            if (text.isNotBlank()) save(ctx, "Built-in Tailscale stopped the app.\n\n$text")
            late.delete()
            return false
        }
        prefs.edit().remove(KEY_TS_STARTING).commit()
        val goCrash = File(File(ctx.filesDir, "tailscale"), "crash.txt")
        val details = runCatching { goCrash.readText() }.getOrNull().orEmpty()
        if (details.isNotBlank() || !File(ctx.filesDir, FILE).exists()) {
            save(ctx, "Built-in Tailscale stopped the app while starting.\n\n" +
                details.ifBlank { "(no details from Tailscale)" })
        }
        goCrash.delete()
        return true
    }

    const val GRACE_MS = TS_GRACE_MS
}
