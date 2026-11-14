package com.dayo.wavremote

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val name: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

/**
 * Self-updates from the GitHub repo's latest release (published by .github/workflows/build.yml).
 * Release tags are "build-<versionCode>", and each release carries one signed APK.
 */
object Updater {
    val repo: String get() = BuildConfig.UPDATE_REPO
    val enabled: Boolean get() = repo.contains("/") && !BuildConfig.APPLICATION_ID.endsWith(".dev")

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10000; readTimeout = 30000
        setRequestProperty("User-Agent", "WavRemote/${BuildConfig.VERSION_NAME}")
    }

    /** Returns the latest release if it's newer than this build, otherwise null. */
    fun check(): UpdateInfo? {
        if (!enabled) return null
        val c = open("https://api.github.com/repos/$repo/releases/latest").apply {
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (c.responseCode == 404) return null   // no releases yet
            if (c.responseCode !in 200..299) throw IllegalStateException("GitHub returned ${c.responseCode}")
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val code = j.optString("tag_name").substringAfter("build-").toIntOrNull() ?: return null
            if (code <= BuildConfig.VERSION_CODE) return null
            val assets = j.optJSONArray("assets") ?: return null
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") } ?: return null
            return UpdateInfo(
                versionCode = code,
                name = j.optString("name").ifBlank { "Version $code" },
                notes = j.optString("body").trim(),
                apkUrl = apk.getString("browser_download_url"),
                sizeBytes = apk.optLong("size"),
            )
        } finally {
            c.disconnect()
        }
    }

    /** Android asks once per app before it may install updates. */
    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Downloads the APK to the app's cache, reporting progress 0..1. */
    fun download(ctx: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File {
        val out = File(ctx.cacheDir, "update.apk")
        val c = open(info.apkUrl).apply { instanceFollowRedirects = true }
        try {
            val total = c.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            var done = 0L
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        return out
    }

    /** Hands the APK to Android's installer. Android shows a confirmation unless it allows a silent self-update. */
    fun install(ctx: Context, apk: File) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { session ->
            session.openWrite("wavremote.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                ctx, id, Intent(ctx, UpdateReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }
}

/** Receives installer status: shows Android's confirm screen when needed, or a failure message. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit   // the app restarts on the new version
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown error"
                Toast.makeText(ctx, "Update didn't install: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
