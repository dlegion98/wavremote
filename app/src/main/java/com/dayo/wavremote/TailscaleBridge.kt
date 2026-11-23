package com.dayo.wavremote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Proxy

/**
 * Tailscale running inside the app (userspace, no VPN), from the Go bridge in tailscale-bridge/.
 * Away from home, the app reaches the router through a local HTTP proxy that dials over the tailnet
 * to the home subnet router.
 */
object TailscaleBridge {
    data class Status(
        val state: String = "Stopped",
        val authUrl: String? = null,
        val ip: String? = null,
        val tailnet: String? = null,
        val error: String? = null,
    ) {
        val running: Boolean get() = state == "Running"
    }

    private const val PREFS = "wavremote"
    private const val KEY_ENABLED = "tsEnabled"
    @Volatile private var interfacesSet = false
    @Volatile private var started = false
    @Volatile private var startError: String? = null

    fun enabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
        if (on) start(ctx) else stop()
    }

    /** Blocking (a second or two): call off the main thread. */
    fun startIfEnabled(ctx: Context) {
        if (enabled(ctx)) start(ctx)
    }

    @Synchronized
    private fun start(ctx: Context) {
        if (started) return
        // If built-in Tailscale took the whole app down last time, switch it off and keep the details.
        if (CrashReporter.tailscaleCrashedLastTime(ctx)) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
            startError = "It closed the app last time, so it was switched off. Switch it on again to retry."
            return
        }
        CrashReporter.tailscaleStarting(ctx)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
            { CrashReporter.tailscaleSurvived(ctx) }, CrashReporter.GRACE_MS)
        try {
            if (!interfacesSet) {
                // Android 11+ won't let Go list interfaces itself, so hand them over from Java.
                tsbridge.Tsbridge.setInterfaceSource(object : tsbridge.InterfaceSource {
                    override fun interfacesJSON(): String = interfacesJson()
                })
                interfacesSet = true
            }
            val dir = File(ctx.filesDir, "tailscale").apply { mkdirs() }
            tsbridge.Tsbridge.start(dir.absolutePath, "wavremote-android")
            started = true
            startError = null
        } catch (e: Throwable) {
            startError = e.message ?: e.toString()
            CrashReporter.tailscaleSurvived(ctx)
        }
    }

    @Synchronized
    fun stop() {
        if (!started) return
        try { tsbridge.Tsbridge.stop() } catch (e: Throwable) { }
        started = false
    }

    fun status(): Status {
        if (!started) return Status(error = startError)
        return try {
            val o = JSONObject(tsbridge.Tsbridge.statusJSON())
            Status(
                state = o.optString("state", "Stopped"),
                authUrl = o.optString("authURL").ifEmpty { null },
                ip = o.optString("ip").ifEmpty { null },
                tailnet = o.optString("tailnet").ifEmpty { null },
                error = o.optString("error").ifEmpty { null },
            )
        } catch (e: Throwable) {
            Status(state = "Starting")
        }
    }

    fun login() {
        try { tsbridge.Tsbridge.login() } catch (e: Throwable) { }
    }

    fun logout() {
        try { tsbridge.Tsbridge.logout() } catch (e: Throwable) { }
    }

    /** Blocking. Waits up to [ms] for Tailscale to connect, when it's switched on. */
    fun waitUntilRunning(ctx: Context, ms: Long): Boolean {
        if (!enabled(ctx)) return false
        start(ctx)
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            val s = status()
            if (s.running) return true
            if (s.state == "NeedsLogin" || s.state == "NeedsMachineAuth" || !started) return false
            Thread.sleep(700)
        }
        return status().running
    }

    /** Blocking: true when [host]'s web page answers through the tailnet. */
    fun reaches(host: String): Boolean = try {
        started && tsbridge.Tsbridge.probe(host, 5000L)
    } catch (e: Throwable) {
        false
    }

    /** The local proxy that carries requests over the tailnet, or null if it can't start. */
    fun proxy(): Proxy? = try {
        val port = tsbridge.Tsbridge.proxyPort().toInt()
        if (port > 0) Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port)) else null
    } catch (e: Throwable) {
        null
    }

    fun stateText(s: Status, enabled: Boolean): String = when (s.state) {
        "Running" -> buildString {
            append("Connected")
            s.tailnet?.let { append(" to $it") }
            s.ip?.let { append(" as $it") }
        }
        "NeedsLogin" -> "Sign in to your Tailscale account to connect."
        "NeedsMachineAuth" -> "Waiting for this device to be approved in the Tailscale admin console."
        "Starting", "NoState" -> "Starting…"
        else -> if (enabled) (s.error?.let { "Couldn't start: $it" } ?: "Starting…") else "Off"
    }

    private fun interfacesJson(): String {
        val arr = JSONArray()
        try {
            val all = NetworkInterface.getNetworkInterfaces() ?: return "[]"
            for (ni in all.toList()) {
                val addrs = JSONArray()
                for (ia in ni.interfaceAddresses) {
                    val a = ia.address ?: continue
                    val host = a.hostAddress?.substringBefore('%') ?: continue
                    addrs.put("$host/${ia.networkPrefixLength}")
                }
                arr.put(JSONObject()
                    .put("name", ni.name)
                    .put("index", ni.index)
                    .put("mtu", runCatching { ni.mtu }.getOrDefault(1500))
                    .put("up", runCatching { ni.isUp }.getOrDefault(false))
                    .put("loopback", runCatching { ni.isLoopback }.getOrDefault(false))
                    .put("p2p", runCatching { ni.isPointToPoint }.getOrDefault(false))
                    .put("multicast", runCatching { ni.supportsMulticast() }.getOrDefault(false))
                    .put("broadcast", ni.interfaceAddresses.any { it.broadcast != null })
                    .put("addrs", addrs))
            }
        } catch (e: Exception) {
            // Return what we have.
        }
        return arr.toString()
    }
}
