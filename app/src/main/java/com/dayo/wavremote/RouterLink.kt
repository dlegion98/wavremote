package com.dayo.wavremote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Thrown when neither home Wi-Fi nor a VPN reaches the router. */
class RouterUnreachable(message: String) : IOException(message)

/**
 * Finds a way to the router.
 *
 * At home the app talks to the router over Wi-Fi (even when mobile data is on). Away from home it
 * uses the phone's normal connection, so a VPN that reaches the home network (Tailscale with a
 * subnet route, ZeroTier, WireGuard…) carries the traffic.
 */
object RouterLink {
    data class Route(val host: String, val network: Network?, val away: Boolean, val proxy: java.net.Proxy? = null)

    private const val PREFS = "wavremote"
    private const val KEY_AWAY_HOST = "awayHost"

    fun awayHost(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_AWAY_HOST, "") ?: ""

    fun setAwayHost(ctx: Context, host: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_AWAY_HOST, host.trim()).apply()
    }

    fun vpnActive(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        return cm.allNetworks.any { n ->
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    /** Blocking: call off the main thread. Returns null when the router can't be reached at all. */
    fun resolve(ctx: Context, homeHost: String): Route? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val wifi = WifiNetwork.current(ctx)
        if (wifi != null && reachable(wifi.network, homeHost)) {
            cm?.bindProcessToNetwork(wifi.network)
            return Route(homeHost, wifi.network, away = false)
        }
        // Not home: don't pin traffic to this Wi-Fi, or a VPN would be bypassed.
        cm?.bindProcessToNetwork(null)
        val candidates = listOf(awayHost(ctx), homeHost).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        for (h in candidates) {
            if (reachable(null, h)) return Route(h, null, away = true)
        }
        // Last: built-in Tailscale, through its local proxy.
        if (TailscaleBridge.waitUntilRunning(ctx, 10_000) && TailscaleBridge.reaches(homeHost)) {
            TailscaleBridge.proxy()?.let { return Route(homeHost, null, away = true, proxy = it) }
        }
        return null
    }

    fun unreachableText(ctx: Context): String = when {
        TailscaleBridge.enabled(ctx) && TailscaleBridge.status().state == "NeedsLogin" ->
            "Sign in to built-in Tailscale to reach your router from here."
        TailscaleBridge.enabled(ctx) && TailscaleBridge.status().running ->
            "Tailscale is connected, but it doesn't reach your router. Check your home server shares 192.168.20.0/24 and the route is approved."
        vpnActive(ctx) -> "Your VPN is on, but it doesn't reach your router. Check it routes to your home network (More, Settings, Away from home)."
        WifiNetwork.current(ctx) != null -> "Can't reach your router. At home, join its Wi-Fi. Away, switch on built-in Tailscale."
        else -> "You're away from home. Switch on built-in Tailscale to reach your router."
    }

    /** True when something answers at [host]'s router page within a couple of seconds. */
    fun reachable(network: Network?, host: String): Boolean = try {
        val u = URL("http://$host/protocol.csp?fname=net&opt=guide&function=get")
        val c = (network?.openConnection(u) ?: u.openConnection()) as HttpURLConnection
        c.connectTimeout = 2500
        c.readTimeout = 3000
        c.useCaches = false
        c.setRequestProperty("User-Agent", WavlinkApi.BROWSER_UA)
        c.setRequestProperty("Connection", "close")
        try {
            c.responseCode > 0
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        false
    }
}
