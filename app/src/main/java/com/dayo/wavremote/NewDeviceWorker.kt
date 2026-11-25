package com.dayo.wavremote

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Every ~15 minutes, while the phone is on the home Wi-Fi, checks the router's device list and
 * notifies about devices it has never seen before.
 */
class NewDeviceWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return Result.success()
        val host = prefs.getString("lastHost", null) ?: return Result.success()
        val hash = prefs.getString("hash:$host", null) ?: return Result.success()
        // Home Wi-Fi or a VPN to home; neither means there's nothing to check right now.
        val route = RouterLink.resolve(ctx, host) ?: return Result.success()

        val api = WavlinkApi(route.host, route.network, route.proxy)
        val list = try {
            withContext(Dispatchers.IO) {
                api.login(hash)
                api.devices()
            }
        } catch (e: Exception) {
            return Result.success()   // router unreachable or busy: try next time
        }

        // Network problems (internet down, a mesh point offline…): tell once per problem.
        try {
            val problems = withContext(Dispatchers.IO) {
                val o = runCatching { api.overview() }.getOrNull()
                val mesh = runCatching { api.meshNodes() }.getOrNull()
                Health.issues(ctx, o, list, mesh, null, null, null).filter { it.severity == Severity.PROBLEM }
            }
            val told = prefs.getStringSet(KEY_TOLD, emptySet()) ?: emptySet()
            problems.filter { it.id !in told }.take(3).forEach { notifyIssue(ctx, it) }
            prefs.edit().putStringSet(KEY_TOLD, problems.map { it.id }.toSet()).apply()
        } catch (_: Exception) {
        }

        val known = prefs.getStringSet(KEY_KNOWN, emptySet()) ?: emptySet()
        val all = (list.online + list.offline).map { it.mac.uppercase() }.toSet()
        val fresh = list.online.filter { it.mac.uppercase() !in known }
        prefs.edit().putStringSet(KEY_KNOWN, known + all).apply()
        if (known.isNotEmpty()) fresh.take(5).forEach { notify(ctx, it) }
        return Result.success()
    }

    companion object {
        const val PREFS = "wavremote"
        const val KEY_ENABLED = "alerts"
        const val KEY_KNOWN = "knownMacs"
        private const val KEY_TOLD = "toldIssues"
        private const val CHANNEL = "new_devices"
        private const val CHANNEL_ISSUES = "network_problems"
        private const val WORK = "new-device-check"

        fun enable(ctx: Context, currentDevices: DeviceList?) {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val seed = currentDevices?.let { (it.online + it.offline).map { d -> d.mac.uppercase() }.toSet() } ?: emptySet()
            prefs.edit().putBoolean(KEY_ENABLED, true)
                .putStringSet(KEY_KNOWN, (prefs.getStringSet(KEY_KNOWN, emptySet()) ?: emptySet()) + seed).apply()
            ensureChannel(ctx)
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<NewDeviceWorker>(15, TimeUnit.MINUTES).build(),
            )
        }

        fun disable(ctx: Context) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
            WorkManager.getInstance(ctx).cancelUniqueWork(WORK)
        }

        fun isEnabled(ctx: Context) =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

        fun knownCount(ctx: Context) =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_KNOWN, emptySet())?.size ?: 0

        private fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "New devices", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When a device joins your network for the first time"
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ISSUES, "Network problems", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "When the internet goes down or a mesh point goes offline"
                },
            )
        }

        fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        private fun notifyIssue(ctx: Context, issue: Issue) {
            if (!canNotify(ctx)) return
            ensureChannel(ctx)
            val open = PendingIntent.getActivity(
                ctx, 1, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(ctx, CHANNEL_ISSUES)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(issue.title)
                .setContentText(issue.detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(issue.detail))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(ctx).notify(issue.id.hashCode(), n)
            } catch (_: SecurityException) {
            }
        }

        private fun notify(ctx: Context, d: Device) {
            if (!canNotify(ctx)) return
            ensureChannel(ctx)
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("New device on your Wi-Fi")
                .setContentText("${d.name} joined (${d.connection}, ${d.ip}).")
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "${d.name} joined your network for the first time.\n${d.connection}, IP ${d.ip}, MAC ${d.mac.uppercase()}." +
                        "\nIf you don't recognise it, open WavRemote to block it."))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(ctx).notify(d.mac.hashCode(), n)
            } catch (_: SecurityException) {
            }
        }
    }
}
