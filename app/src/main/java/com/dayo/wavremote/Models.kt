package com.dayo.wavremote

import org.json.JSONObject

data class Overview(
    val model: String?,
    val operationMode: Int,
    val internetUp: Boolean,
    val wanLink: Boolean,
    val wanIp: String?,
    val gateway: String?,
    val wanType: String,
    val ssid2: String?,
    val ssid5: String?,
    val uptimeSec: Long?,
    val firmware: String?,
    val updateAvailable: Boolean,
    val meshNodes: Int?,
    val onlineDevices: Int?,
) {
    val modeLabel: String
        get() = when (operationMode) {
            1 -> "Access point"
            2 -> "Repeater (bridge)"
            3 -> "Repeater (WISP)"
            else -> "Router"
        }
}

data class Device(
    val mac: String,
    val name: String,
    val ip: String,
    val online: Boolean,
    val band: Int,
    val rssi: Int,
    val downKBs: Long,
    val upKBs: Long,
    val onlineSec: Long,
    val blocked: Boolean,
    val limitDownKB: Int,
    val limitUpKB: Int,
) {
    val connection: String
        get() = when (band) {
            0 -> "2.4 GHz Wi-Fi"
            1 -> "5 GHz Wi-Fi"
            2 -> "Wired"
            else -> "Wi-Fi"
        }
    val isWired get() = band == 2
    val hasLimit get() = limitDownKB != 0 || limitUpKB != 0
}

data class DeviceList(
    val online: List<Device>,
    val offline: List<Device>,
    val limitSlots: Int,
    val limitsUsed: Int,
)

data class BandConfig(
    val raw: JSONObject,
    val ssid: String,
    val password: String,
    val security: Int,
    val enabled: Boolean,
    val channel: String,
    val autoChannel: String?,
    val mac: String?,
) {
    val isOpen get() = security == 0
    val isRadius get() = security == 4

    companion object {
        fun from(o: JSONObject, ssidKey: String): BandConfig {
            val security = o.s("security").toIntOrNull() ?: 0
            return BandConfig(
                raw = o,
                ssid = o.optString(ssidKey),
                password = if (security == 0) "" else o.optString("passwd"),
                security = security,
                enabled = o.s("enable") != "0",
                channel = o.s("channel"),
                autoChannel = o.str("auto_channel"),
                mac = o.str("mac"),
            )
        }
    }
}

data class WifiConfig(
    val country: String,
    val band2: BandConfig,
    val band5: BandConfig,
) {
    val bandsMatch get() = band2.ssid == band5.ssid && band2.password == band5.password &&
        band2.security == band5.security
}

data class MeshNode(
    val name: String,
    val ip: String?,
    val mac: String?,
    val rssi: Int?,
)

fun formatDuration(sec: Long): String {
    if (sec <= 0) return "—"
    val d = sec / 86400
    val h = (sec % 86400) / 3600
    val m = (sec % 3600) / 60
    return buildList {
        if (d > 0) add("${d}d")
        if (h > 0) add("${h}h")
        if (m > 0 || isEmpty()) add("${m}m")
    }.joinToString(" ")
}

fun formatSpeed(kbs: Long): String =
    if (kbs > 1024) String.format("%.1f MB/s", kbs / 1024.0) else "$kbs KB/s"

data class TrafficSample(val at: Long, val downKBs: Long, val upKBs: Long)

/** 0–100 Wi-Fi experience across online devices (wired counts as perfect). */
fun experienceScore(list: DeviceList): Int? {
    val on = list.online
    if (on.isEmpty()) return null
    return on.map { if (it.isWired) 100 else (it.rssi * 1.15).toInt().coerceIn(0, 100) }.average().toInt()
}

fun experienceLabel(score: Int) = when {
    score >= 80 -> "Excellent"
    score >= 60 -> "Good"
    score >= 40 -> "Fair"
    else -> "Poor"
}

/** Mesh backhaul RSSI (dBm) thresholds used by the router's own mesh page. */
fun meshLinkLabel(rssi: Int?) = when {
    rssi == null -> "Connected"
    rssi >= -55 -> "Strong link"
    rssi >= -73 -> "Good link"
    else -> "Weak link"
}
