package com.dayo.wavremote

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Home-screen widgets: network status, quick controls and a device counter.
 *
 * They show the last snapshot of the router. The snapshot is refreshed every ~15 minutes in the
 * background (Android's minimum), when the refresh button is tapped, and every few seconds while
 * the app itself is open.
 */

/** What the widgets display. Toggle values: -1 unknown, 0 off, 1 on. */
data class WidgetSnap(
    val at: Long = 0,
    val internet: Boolean = false,
    val devices: Int = 0,
    val wifi5: Int = 0,
    val wifi24: Int = 0,
    val wired: Int = 0,
    val downKBs: Long = 0,
    val upKBs: Long = 0,
    val nodes: Int = 0,
    val ssid: String = "",
    val guest: Int = -1,
    val kids: Int = -1,
    val lights: Int = -1,
    val note: String? = null,
    val busy: Boolean = false,
) {
    fun toJson(): String = JSONObject()
        .put("at", at).put("internet", internet).put("devices", devices)
        .put("wifi5", wifi5).put("wifi24", wifi24).put("wired", wired)
        .put("down", downKBs).put("up", upKBs).put("nodes", nodes).put("ssid", ssid)
        .put("guest", guest).put("kids", kids).put("lights", lights)
        .put("note", note ?: "").put("busy", busy)
        .toString()

    companion object {
        fun fromJson(text: String?): WidgetSnap {
            if (text.isNullOrEmpty()) return WidgetSnap()
            return try {
                val o = JSONObject(text)
                WidgetSnap(
                    at = o.optLong("at"),
                    internet = o.optBoolean("internet"),
                    devices = o.optInt("devices"),
                    wifi5 = o.optInt("wifi5"),
                    wifi24 = o.optInt("wifi24"),
                    wired = o.optInt("wired"),
                    downKBs = o.optLong("down"),
                    upKBs = o.optLong("up"),
                    nodes = o.optInt("nodes"),
                    ssid = o.optString("ssid"),
                    guest = o.optInt("guest", -1),
                    kids = o.optInt("kids", -1),
                    lights = o.optInt("lights", -1),
                    note = o.optString("note").ifEmpty { null },
                    busy = o.optBoolean("busy"),
                )
            } catch (e: Exception) {
                WidgetSnap()
            }
        }
    }
}

object Widgets {
    private const val PREFS = "wavremote"
    private const val KEY = "widgetSnap"
    private const val WORK_PERIODIC = "widget-refresh"
    private const val WORK_NOW = "widget-now"
    const val ACTION_REFRESH = "com.dayo.wavremote.widget.REFRESH"
    const val ACTION_GUEST = "com.dayo.wavremote.widget.GUEST"
    const val ACTION_KIDS = "com.dayo.wavremote.widget.KIDS"
    const val ACTION_LIGHTS = "com.dayo.wavremote.widget.LIGHTS"

    private const val TEXT = 0xFFF4F6FA.toInt()
    private const val MUTED = 0xFFB6BFCC.toInt()
    private const val GREEN = 0xFF2FBF71.toInt()
    private const val RED = 0xFFF2555A.toInt()
    private const val AMBER = 0xFFF2A93B.toInt()

    private val providers = listOf(StatusWidget::class.java, ControlsWidget::class.java, DevicesWidget::class.java)

    fun load(ctx: Context): WidgetSnap =
        WidgetSnap.fromJson(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    private fun save(ctx: Context, snap: WidgetSnap) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, snap.toJson()).apply()
    }

    private fun ids(ctx: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, cls))

    fun anyPlaced(ctx: Context): Boolean = providers.any { ids(ctx, it).isNotEmpty() }

    // ---------------------------------------------------------------- scheduling

    fun schedule(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetWorker>(15, TimeUnit.MINUTES).build(),
        )
    }

    fun unscheduleIfUnused(ctx: Context) {
        if (!anyPlaced(ctx)) WorkManager.getInstance(ctx).cancelUniqueWork(WORK_PERIODIC)
    }

    /** Refresh now; [toggle] is "guest", "kids" or "lights" to flip that switch first. */
    fun runNow(ctx: Context, toggle: String?) {
        save(ctx, load(ctx).copy(busy = true))
        updateAll(ctx)
        val work = OneTimeWorkRequestBuilder<WidgetWorker>()
            .setInputData(workDataOf("toggle" to (toggle ?: "")))
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, work)
    }

    // ---------------------------------------------------------------- data

    /** Called by the app while it is open, so the widgets follow what the app shows. */
    fun pushFromApp(ctx: Context, overview: Overview?, list: DeviceList) {
        if (!anyPlaced(ctx)) return
        val old = load(ctx)
        save(ctx, withDevices(old, list).copy(
            at = System.currentTimeMillis(),
            internet = overview?.internetUp ?: old.internet,
            nodes = overview?.meshNodes ?: old.nodes,
            ssid = overview?.ssid5 ?: overview?.ssid2 ?: old.ssid,
            note = null,
            busy = false,
        ))
        updateAll(ctx)
    }

    private fun withDevices(snap: WidgetSnap, list: DeviceList): WidgetSnap = snap.copy(
        devices = list.online.size,
        wifi5 = list.online.count { it.band == 1 },
        wifi24 = list.online.count { it.band == 0 },
        wired = list.online.count { it.band == 2 },
        downKBs = list.online.sumOf { it.downKBs },
        upKBs = list.online.sumOf { it.upKBs },
    )

    /** Signs in to the router, optionally flips a switch, reads the latest state. Runs off the main thread. */
    fun refresh(ctx: Context, toggle: String?) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = load(ctx)
        fun fail(note: String) {
            save(ctx, old.copy(note = note, busy = false))
            updateAll(ctx)
        }

        val host = prefs.getString("lastHost", null)
        val hash = if (host != null) prefs.getString("hash:$host", null) else null
        if (host == null || hash == null) {
            fail("Open WavRemote and sign in")
            return
        }
        val route = RouterLink.resolve(ctx, host)
        if (route == null) {
            fail(if (RouterLink.vpnActive(ctx)) "VPN doesn't reach the router" else "Away: turn on your VPN")
            return
        }

        val api = WavlinkApi(route.host, route.network, route.proxy)
        try {
            api.login(hash)
        } catch (e: RouterException) {
            fail("Sign in again in WavRemote")
            return
        } catch (e: Exception) {
            fail("Can't reach the router")
            return
        }

        var guest: Int? = null
        var kids: Int? = null
        var lights: Int? = null
        var note: String? = null
        try {
            when (toggle) {
                "lights" -> {
                    val on = api.set("system", "led", "&act=dump").s("led") == "1"
                    api.set("system", "led", "&act=${if (on) "off" else "on"}")
                    lights = if (on) 0 else 1
                }
                "guest" -> {
                    val d = api.get("net", "wifi_ap_guest")
                    val on = d.s("guest_enable") == "1"
                    val body = Quick.guestData(d, !on)
                    if (body == null) {
                        note = "Set up guest Wi-Fi in the app first"
                    } else {
                        // The router restarts Wi-Fi here, so a dropped connection is expected.
                        try { api.set("net", "wifi_ap_guest", body) } catch (e: java.io.IOException) { }
                        guest = if (on) 0 else 1
                    }
                }
                "kids" -> {
                    val d = api.get("net", "wifi_lt", "&wifi_type=_child")
                    val on = d.s("enable") == "1"
                    val body = Quick.kidsData(d, !on)
                    if (body == null) {
                        note = "Set up kids' Wi-Fi in the app first"
                    } else {
                        try { api.set("net", "wifi_lt", body) } catch (e: java.io.IOException) { }
                        kids = if (on) 0 else 1
                    }
                }
            }
        } catch (e: Exception) {
            note = "That switch didn't change. Try again."
        }

        val overview = runCatching { api.overview() }.getOrNull()
        val list = runCatching { api.devices() }.getOrNull()
        if (guest == null) guest = runCatching {
            if (api.get("net", "wifi_ap_guest").s("guest_enable") == "1") 1 else 0
        }.getOrNull()
        if (kids == null) kids = runCatching {
            if (api.get("net", "wifi_lt", "&wifi_type=_child").s("enable") == "1") 1 else 0
        }.getOrNull()
        if (lights == null) lights = runCatching {
            if (api.set("system", "led", "&act=dump").s("led") == "1") 1 else 0
        }.getOrNull()

        var snap = old
        if (list != null) snap = withDevices(snap, list)
        val gotData = overview != null || list != null
        snap = snap.copy(
            at = if (gotData) System.currentTimeMillis() else old.at,
            internet = overview?.internetUp ?: old.internet,
            nodes = overview?.meshNodes ?: old.nodes,
            ssid = overview?.ssid5 ?: overview?.ssid2 ?: old.ssid,
            guest = guest ?: old.guest,
            kids = kids ?: old.kids,
            lights = lights ?: old.lights,
            note = note ?: if (gotData || toggle == "guest" || toggle == "kids") null else "Can't reach the router",
            busy = false,
        )
        save(ctx, snap)
        updateAll(ctx)
    }

    // ---------------------------------------------------------------- drawing

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 100, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(ctx: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, WidgetActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun timeText(snap: WidgetSnap): String = when {
        snap.busy -> "Updating…"
        snap.at == 0L -> "Not loaded yet"
        else -> "Updated " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(snap.at))
    }

    private fun statusViews(ctx: Context, s: WidgetSnap): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_status)
        val loaded = s.at != 0L
        val (title, color) = when {
            !loaded -> "Waiting for the router" to AMBER
            s.note != null -> s.note to AMBER
            s.internet -> "Internet online" to GREEN
            else -> "Internet offline" to RED
        }
        v.setTextViewText(R.id.w_title, title)
        v.setTextColor(R.id.w_dot, color)
        v.setTextViewText(R.id.w_time, timeText(s))
        v.setTextViewText(R.id.w_devices, if (loaded) s.devices.toString() else "–")
        v.setTextViewText(R.id.w_down, if (loaded) formatSpeed(s.downKBs) else "–")
        v.setTextViewText(R.id.w_up, if (loaded) formatSpeed(s.upKBs) else "–")
        val points = if (s.nodes == 1) "1 mesh point" else "${s.nodes} mesh points"
        v.setTextViewText(R.id.w_footer, if (!loaded) "Tap refresh at home or with your VPN on"
            else if (s.ssid.isEmpty()) points else "${s.ssid}, $points")
        v.setOnClickPendingIntent(R.id.w_root, openApp(ctx))
        v.setOnClickPendingIntent(R.id.w_refresh, action(ctx, ACTION_REFRESH, 1))
        return v
    }

    private fun setToggle(v: RemoteViews, box: Int, state: Int, value: Int, busy: Boolean) {
        v.setInt(box, "setBackgroundResource", if (value == 1) R.drawable.widget_btn_on else R.drawable.widget_btn_off)
        v.setTextViewText(state, if (busy) "…" else when (value) { 1 -> "On"; 0 -> "Off"; else -> "–" })
        v.setTextColor(state, if (value == 1) TEXT else MUTED)
    }

    private fun controlsViews(ctx: Context, s: WidgetSnap): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_controls)
        setToggle(v, R.id.w_guest, R.id.w_guest_state, s.guest, s.busy)
        setToggle(v, R.id.w_kids, R.id.w_kids_state, s.kids, s.busy)
        setToggle(v, R.id.w_lights, R.id.w_lights_state, s.lights, s.busy)
        v.setOnClickPendingIntent(R.id.w_guest, action(ctx, ACTION_GUEST, 2))
        v.setOnClickPendingIntent(R.id.w_kids, action(ctx, ACTION_KIDS, 3))
        v.setOnClickPendingIntent(R.id.w_lights, action(ctx, ACTION_LIGHTS, 4))
        return v
    }

    private fun devicesViews(ctx: Context, s: WidgetSnap): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_devices)
        val loaded = s.at != 0L
        v.setTextViewText(R.id.w_count, if (loaded) s.devices.toString() else "–")
        v.setTextViewText(R.id.w_split, if (loaded) "${s.wifi5} on 5 GHz, ${s.wifi24} on 2.4 GHz" else "Tap to refresh")
        v.setTextViewText(R.id.w_wired, if (loaded) "${s.wired} wired" else "")
        v.setTextViewText(R.id.w_time, timeText(s))
        v.setOnClickPendingIntent(R.id.w_root, action(ctx, ACTION_REFRESH, 1))
        return v
    }

    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val snap = load(ctx)
        try {
            ids(ctx, StatusWidget::class.java).forEach { mgr.updateAppWidget(it, statusViews(ctx, snap)) }
            ids(ctx, ControlsWidget::class.java).forEach { mgr.updateAppWidget(it, controlsViews(ctx, snap)) }
            ids(ctx, DevicesWidget::class.java).forEach { mgr.updateAppWidget(it, devicesViews(ctx, snap)) }
        } catch (e: Exception) {
            // A launcher that rejects an update must not crash the app.
        }
    }
}

/** Shared behaviour: draw on update, keep the background refresh scheduled while any widget exists. */
open class BaseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.updateAll(context)
        Widgets.schedule(context)
        Widgets.runNow(context, null)
    }

    override fun onDisabled(context: Context) {
        Widgets.unscheduleIfUnused(context)
    }
}

class StatusWidget : BaseWidget()
class ControlsWidget : BaseWidget()
class DevicesWidget : BaseWidget()

/** Taps on widget buttons. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val toggle = when (intent.action) {
            Widgets.ACTION_GUEST -> "guest"
            Widgets.ACTION_KIDS -> "kids"
            Widgets.ACTION_LIGHTS -> "lights"
            Widgets.ACTION_REFRESH -> null
            else -> return
        }
        Widgets.runNow(context, toggle)
    }
}

class WidgetWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val toggle = inputData.getString("toggle")?.ifEmpty { null }
        try {
            withContext(Dispatchers.IO) { Widgets.refresh(applicationContext, toggle) }
        } catch (e: Exception) {
            // Leave the last snapshot in place.
        }
        return Result.success()
    }
}
