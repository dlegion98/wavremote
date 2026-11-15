package com.dayo.wavremote

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL
import kotlin.random.Random

// ================================================================ Wi-Fi QR codes

object WifiQr {
    private fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
        .replace(":", "\\:").replace("\"", "\\\"")

    /** The standard "WIFI:" payload that phone cameras understand. */
    fun payload(ssid: String, password: String, open: Boolean, hidden: Boolean = false): String =
        if (open) "WIFI:T:nopass;S:${esc(ssid)};${if (hidden) "H:true;" else ""};"
        else "WIFI:T:WPA;S:${esc(ssid)};P:${esc(password)};${if (hidden) "H:true;" else ""};"

    fun bitmap(text: String, size: Int = 720): ImageBitmap {
        val m = QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.CHARACTER_SET to "UTF-8"),
        )
        val px = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            px[y * size + x] = if (m.get(x, y)) 0xFF13201C.toInt() else 0xFFFFFFFF.toInt()
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

// ================================================================ this phone's Wi-Fi link

data class PhoneLink(
    val rssi: Int,
    val linkMbps: Int,
    val freqMhz: Int,
    val txMbps: Int?,
    val rxMbps: Int?,
) {
    val band: String get() = when {
        freqMhz >= 5925 -> "6 GHz"
        freqMhz >= 4900 -> "5 GHz"
        freqMhz > 0 -> "2.4 GHz"
        else -> "Wi-Fi"
    }
    /** 0–100 quality from RSSI (−90 dBm = 0, −50 dBm = 100). */
    val quality: Int get() = ((rssi + 90) * 100 / 40).coerceIn(0, 100)
    val verdict: String get() = when {
        rssi >= -55 -> "Excellent"
        rssi >= -65 -> "Good"
        rssi >= -72 -> "Fair"
        rssi >= -80 -> "Weak"
        else -> "Very weak"
    }
}

object PhoneWifi {
    fun link(ctx: Context): PhoneLink? {
        val info: WifiInfo? = if (Build.VERSION.SDK_INT >= 29) {
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val net = WifiNetwork.current(ctx)?.network ?: return null
            cm?.getNetworkCapabilities(net)?.transportInfo as? WifiInfo
        } else {
            @Suppress("DEPRECATION")
            ctx.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo
        }
        info ?: return null
        if (info.rssi <= -127) return null
        return PhoneLink(
            rssi = info.rssi,
            linkMbps = info.linkSpeed,
            freqMhz = info.frequency,
            txMbps = if (Build.VERSION.SDK_INT >= 29) info.txLinkSpeedMbps.takeIf { it > 0 } else null,
            rxMbps = if (Build.VERSION.SDK_INT >= 29) info.rxLinkSpeedMbps.takeIf { it > 0 } else null,
        )
    }

    /** Directed broadcast for the Wi-Fi subnet, e.g. 192.168.20.255. */
    fun broadcast(ctx: Context): InetAddress? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        val net = WifiNetwork.current(ctx)?.network ?: return null
        val la = cm.getLinkProperties(net)?.linkAddresses?.firstOrNull { it.address is Inet4Address } ?: return null
        val ip = la.address.address
        val prefix = la.prefixLength
        val ipInt = ip.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xff) }
        val mask = if (prefix == 0) 0 else -1 shl (32 - prefix)
        val bc = ipInt or mask.inv()
        return InetAddress.getByAddress(byteArrayOf((bc ushr 24).toByte(), (bc ushr 16).toByte(), (bc ushr 8).toByte(), bc.toByte()))
    }
}

// ================================================================ Wake-on-LAN

object WakeOnLan {
    fun send(mac: String, network: Network?, subnetBroadcast: InetAddress?) {
        val m = mac.split(":", "-").map { it.toInt(16).toByte() }
        require(m.size == 6) { "Not a MAC address" }
        val packet = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) packet[i] = 0xFF.toByte()
        for (r in 0 until 16) for (i in 0 until 6) packet[6 + r * 6 + i] = m[i]
        DatagramSocket().use { s ->
            network?.bindSocket(s)
            s.broadcast = true
            val targets = listOfNotNull(subnetBroadcast, InetAddress.getByName("255.255.255.255")).distinct()
            repeat(3) {
                for (t in targets) for (port in listOf(9, 7)) s.send(DatagramPacket(packet, packet.size, t, port))
                Thread.sleep(100)
            }
        }
    }
}

// ================================================================ MAC helpers

/** Phones and laptops use "private" random MACs; the locally-administered bit gives them away. */
fun isPrivateMac(mac: String): Boolean {
    val first = mac.take(2).toIntOrNull(16) ?: return false
    return first and 0x02 != 0
}

// ================================================================ device list export

fun shareDeviceList(
    ctx: Context,
    list: DeviceList,
    favourites: Set<String> = emptySet(),
    seen: Map<String, Seen> = emptyMap(),
) {
    val rows = (list.online + list.offline).joinToString("\n") { d ->
        val k = DeviceBook.key(d.mac)
        val last = seen[k]?.last
        listOf(d.name, if (d.online) "online" else "offline", d.connection, d.ip, d.mac.uppercase(),
            if (d.blocked) "blocked" else "", if (d.hasLimit) "limit ${d.limitDownKB}/${d.limitUpKB} KB/s" else "",
            if (k in favourites) "favourite" else "",
            when { d.online -> "now"; last != null -> formatDay(last); else -> "" })
            .joinToString(",") { "\"" + it.replace("\"", "'") + "\"" }
    }
    val csv = "Name,Status,Connection,IP,MAC,Blocked,Limit,Favourite,Last online\n$rows"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, "Devices on my network")
        putExtra(Intent.EXTRA_TEXT, csv)
    }
    ctx.startActivity(Intent.createChooser(send, "Share device list").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun copyText(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
}

fun shareText(ctx: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// ================================================================ speed test (Cloudflare)

/**
 * Measures this phone's connection through the router: latency, download and upload,
 * using Cloudflare's public speed-test endpoints.
 */
object SpeedTest {
    private const val BASE = "https://speed.cloudflare.com"

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000; readTimeout = 15000; useCaches = false
            setRequestProperty("User-Agent", "WavRemote")
        }

    /** Median and jitter of [n] tiny requests, in ms. */
    fun latency(n: Int = 10): Pair<Int, Int> {
        val samples = mutableListOf<Long>()
        repeat(n) {
            val c = open("$BASE/__down?bytes=0")
            val t0 = System.nanoTime()
            try {
                c.inputStream.use { it.readBytes() }
                samples += (System.nanoTime() - t0) / 1_000_000
            } finally { c.disconnect() }
        }
        val sorted = samples.sorted()
        val median = sorted[sorted.size / 2].toInt()
        val jitter = samples.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average().toInt()
        return median to jitter
    }

    /** Downloads for ~[seconds], calling [onMbps] as it goes. Returns the final Mbps. */
    fun download(seconds: Double = 8.0, onMbps: (Double) -> Unit): Double {
        val start = System.nanoTime()
        var bytes = 0L
        val buf = ByteArray(64 * 1024)
        var lastReport = start
        fun elapsed() = (System.nanoTime() - start) / 1e9
        while (elapsed() < seconds) {
            val c = open("$BASE/__down?bytes=50000000")
            try {
                c.inputStream.use { s: InputStream ->
                    while (elapsed() < seconds) {
                        val n = s.read(buf)
                        if (n <= 0) break
                        bytes += n
                        val now = System.nanoTime()
                        if (now - lastReport > 250_000_000) {
                            lastReport = now
                            onMbps(bytes * 8 / 1e6 / elapsed())
                        }
                    }
                }
            } finally { c.disconnect() }
        }
        return bytes * 8 / 1e6 / elapsed()
    }

    /** Uploads random data for ~[seconds]. */
    fun upload(seconds: Double = 6.0, onMbps: (Double) -> Unit): Double {
        val chunk = Random.nextBytes(2_000_000)
        val start = System.nanoTime()
        var bytes = 0L
        fun elapsed() = (System.nanoTime() - start) / 1e9
        while (elapsed() < seconds) {
            val c = open("$BASE/__up").apply {
                requestMethod = "POST"; doOutput = true
                setFixedLengthStreamingMode(chunk.size)
                setRequestProperty("Content-Type", "application/octet-stream")
            }
            try {
                c.outputStream.use { o ->
                    var off = 0
                    while (off < chunk.size) {
                        val n = minOf(64 * 1024, chunk.size - off)
                        o.write(chunk, off, n)
                        off += n
                        bytes += n
                    }
                }
                c.responseCode
            } finally { c.disconnect() }
            onMbps(bytes * 8 / 1e6 / elapsed())
        }
        return bytes * 8 / 1e6 / elapsed()
    }
}
