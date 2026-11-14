package com.dayo.wavremote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address

/** The phone's Wi-Fi network, its address and its real gateway (read from the routing table). */
data class WifiInfo(
    val network: Network,
    val phoneIp: String?,
    val gateway: String?,
)

object WifiNetwork {
    fun current(context: Context): WifiInfo? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        val wifi = cm.allNetworks.firstOrNull { n ->
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: return null

        val props = cm.getLinkProperties(wifi)
        val ip = props?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address }
            ?.hostAddress
        val gateway = props?.routes
            ?.filter { it.isDefaultRoute }
            ?.mapNotNull { it.gateway }
            ?.firstOrNull { it is Inet4Address }
            ?.hostAddress
        return WifiInfo(wifi, ip, gateway)
    }

    /**
     * Sends all app traffic (including the WebView) over Wi-Fi, so the router is reachable
     * even when mobile data is switched on.
     */
    fun bindAppToWifi(context: Context, info: WifiInfo?) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        cm.bindProcessToNetwork(info?.network)
    }
}
