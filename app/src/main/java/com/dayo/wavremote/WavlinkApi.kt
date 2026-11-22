package com.dayo.wavremote

import android.net.Network
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class RouterException(val code: Int, message: String) : Exception(message)

/**
 * Client for the Wavlink web UI's JSON API (mapped from the router's own login.js, common.js,
 * index.js, terminal.js, wireless.js and setting.js).
 *
 *  - Every call goes to http://<router>/protocol.csp with "fname=…&opt=…&function=get|set&…".
 *  - "get" calls are HTTP GET with the fields in the query string; "set" calls are POSTed as the body.
 *  - After login, every call carries ?token=<token>.
 *  - Login sends usrid = md5("admin" + password).
 */
class WavlinkApi(val host: String, private val network: Network?) {
    @Volatile var token: String? = null
        private set
    @Volatile var loginHash: String? = null
        private set

    private val endpoint get() = "http://$host/protocol.csp"

    // ---------------------------------------------------------------- transport

    /**
     * Requests are made to look exactly like the router's own web page (jQuery in a mobile browser).
     * The router's tiny web server answers HTTP 500 to requests it doesn't expect — e.g. a chunked
     * POST body or Android's default "Dalvik/…" user agent.
     */
    private fun open(url: String): HttpURLConnection {
        val u = URL(url)
        val c = (network?.openConnection(u) ?: u.openConnection()) as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 20000
        c.useCaches = false
        c.instanceFollowRedirects = false
        c.setRequestProperty("User-Agent", BROWSER_UA)
        c.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
        c.setRequestProperty("Accept-Language", "en-GB,en;q=0.9")
        c.setRequestProperty("Accept-Encoding", "identity")
        c.setRequestProperty("X-Requested-With", "XMLHttpRequest")
        c.setRequestProperty("Origin", "http://$host")
        c.setRequestProperty("Referer", "http://$host/login.html")
        c.setRequestProperty("Connection", "close")
        return c
    }

    private fun send(data: String): JSONObject {
        val t = token
        val isGet = data.contains("&function=get")
        val bodyBytes = data.toByteArray(Charsets.UTF_8)
        val conn = if (isGet) {
            open(if (t != null) "$endpoint?token=$t&$data" else "$endpoint?$data")
        } else {
            // The router answers HTTP 500 if protocol.csp has no query string at all, and Android drops
            // an empty trailing "?", so always send a non-empty one before login.
            open(if (t != null) "$endpoint?token=$t" else "$endpoint?_=${System.currentTimeMillis()}").apply {
                requestMethod = "POST"
                doOutput = true
                setFixedLengthStreamingMode(bodyBytes.size)   // send Content-Length, never chunked
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            }
        }
        try {
            if (!isGet) conn.outputStream.use { it.write(bodyBytes) }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val trimmed = body.trim()
            // Some firmware sends a JSON error with a 500 status — use it if it's there.
            if (trimmed.startsWith("{")) {
                runCatching { JSONObject(trimmed) }.getOrNull()?.let { return it }
            }
            if (code >= 400) {
                val what = data.substringAfter("opt=").substringBefore("&")
                val snippet = trimmed.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").take(120)
                throw RouterException(code, "Router returned HTTP $code for \"$what\"" +
                    if (snippet.isNotEmpty()) ": $snippet" else "")
            }
            return JSONObject().put("error", 0).put("raw", trimmed)
        } finally {
            conn.disconnect()
        }
    }

    /** Sends a call; logs in again once if the session has expired. Throws on a router error. */
    fun call(data: String): JSONObject {
        var r = send(data)
        var err = r.optInt("error", 0)
        val hash = loginHash
        if (err == SESSION_EXPIRED && hash != null) {
            login(hash)
            r = send(data)
            err = r.optInt("error", 0)
        }
        if (err != 0) throw RouterException(err, errorText(err))
        return r
    }

    // ---------------------------------------------------------------- session

    /** Works without logging in. Tells us the model and whether first-time setup is done. */
    fun guide(): JSONObject = send("fname=net&opt=guide&function=get")

    fun login(hash: String) {
        token = null
        val r = send("fname=system&opt=login&function=set&usrid=$hash")
        if (r.optInt("error", -1) != 0) {
            val freq = r.optInt("frequency", 0)
            val msg = if (freq >= 5) {
                "Too many wrong attempts. The router has locked login for ${r.optInt("time", 60)} seconds."
            } else {
                "Wrong password (${(5 - freq).coerceAtLeast(0)} tries left before a lock-out)."
            }
            throw RouterException(r.optInt("error", -1), msg)
        }
        token = r.optString("token").takeIf { it.isNotEmpty() }
        loginHash = hash
    }

    fun logout() {
        token = null
        loginHash = null
    }

    // ---------------------------------------------------------------- reads

    fun overview(): Overview {
        val g = runCatching { guide() }.getOrNull()
        val wan = call("fname=net&opt=wan_info&function=get")
        val w2 = runCatching { call("fname=net&opt=wifi_ap&function=get") }.getOrNull()
        val w5 = runCatching { call("fname=net&opt=wifi_ap_5g&function=get") }.getOrNull()
        val base = runCatching { call("fname=sys&opt=baseinfo&function=get") }.getOrNull()
        val main = runCatching { call("fname=system&opt=main&function=get") }.getOrNull()
        val meshCount = runCatching {
            call("fname=net&opt=wifi_mesh_agent_info&function=get").optJSONArray("agent_list")?.length()
        }.getOrNull()

        val online = main?.optJSONArray("terminals")?.objects()?.count { isOnline(it) }

        return Overview(
            model = g?.str("Model") ?: base?.str("Model"),
            operationMode = g?.optInt("OperationMode", 0) ?: 0,
            internetUp = wan.truthy("connected"),
            wanLink = wan.truthy("link"),
            wanIp = wan.str("ip"),
            gateway = wan.str("gateway"),
            wanType = when (wan.optInt("mode", 1)) {
                2 -> "PPPoE"; 3 -> "Static IP"; 4 -> "PPPoE dual access"
                5 -> "PPTP dual access"; 6 -> "L2TP dual access"; else -> "DHCP"
            },
            ssid2 = w2?.str("SSID2g"),
            ssid5 = w5?.str("SSID5g"),
            uptimeSec = firstLong(listOf(main, base), "ontime", "uptime", "run_time"),
            firmware = firstString(listOf(base, main), "sdkversion", "fw_version", "firmware",
                "FirmwareVersion", "soft_version", "version"),
            updateAvailable = base?.optInt("ota_info", 0) == 1,
            meshNodes = meshCount,
            onlineDevices = online,
        )
    }

    fun devices(): DeviceList {
        val leases = runCatching {
            call("fname=net&opt=dhcp_leases&function=get").optJSONArray("dhcp_list")
        }.getOrNull()
        val hostIf = runCatching {
            call("fname=system&opt=host_if&function=get&if=FFFFFFFT&flage=0")
        }.getOrNull()
        val main = call("fname=system&opt=main&function=get")

        val leaseNames = leases?.objects()?.associate {
            it.optString("mac").lowercase() to it.optString("name")
        } ?: emptyMap()
        val limits = hostIf?.optJSONArray("terminals")?.objects()?.associate {
            it.optString("mac").lowercase() to (it.optInt("ls", 0) to it.optInt("ls_up", 0))
        } ?: emptyMap()

        val all = (main.optJSONArray("terminals")?.objects() ?: emptyList()).map { t ->
            val mac = t.optString("mac")
            var name = t.optString("name")
            if (name.isEmpty()) name = leaseNames[mac.lowercase()] ?: ""
            if (name.isBlank() || name == "*") name = "Unknown device"
            val (limDown, limUp) = limits[mac.lowercase()] ?: (0 to 0)
            Device(
                mac = mac,
                name = name,
                ip = t.optString("ip"),
                online = isOnline(t),
                band = t.optInt("band", -1),
                rssi = t.optInt("rssi", 0),
                downKBs = t.optLong("speed", 0),
                upKBs = t.optLong("up_speed", 0),
                onlineSec = t.optLong("ontime", 0),
                blocked = t.truthy("net_index"),
                limitDownKB = limDown,
                limitUpKB = limUp,
            )
        }
        return DeviceList(
            online = all.filter { it.online }.sortedBy { it.name.lowercase() },
            offline = all.filter { !it.online }.sortedBy { it.name.lowercase() },
            limitSlots = hostIf?.optInt("limit_sum", 15) ?: 15,
            limitsUsed = hostIf?.optInt("exist_limit_num", 0) ?: 0,
        )
    }

    fun wifi(): WifiConfig {
        val region = runCatching { call("fname=net&opt=wifi_region&function=get") }.getOrNull()
        val w2 = call("fname=net&opt=wifi_ap&function=get")
        val w5 = call("fname=net&opt=wifi_ap_5g&function=get")
        return WifiConfig(
            country = region?.optString("country").orEmpty(),
            band2 = BandConfig.from(w2, "SSID2g"),
            band5 = BandConfig.from(w5, "SSID5g"),
        )
    }

    fun meshNodes(): List<MeshNode> {
        val list = call("fname=net&opt=wifi_mesh_agent_info&function=get").optJSONArray("agent_list")
        return list?.objects()?.mapIndexed { i, o ->
            MeshNode(
                name = o.str("name") ?: o.str("dev_name") ?: "Node ${i + 1}",
                ip = o.str("ip_addr") ?: o.str("ip"),
                mac = o.str("mac") ?: o.str("pairKey") ?: o.str("pair_key"),
                rssi = if (o.has("rssi")) o.optInt("rssi") else null,
            )
        } ?: emptyList()
    }

    // ---------------------------------------------------------------- actions

    fun rename(mac: String, name: String) {
        call("fname=net&opt=host_nick&function=set&mac=$mac&nick=${enc(name)}")
    }

    /** "Block Internet" in the web UI: index=1 blocks, 0 allows. */
    fun setBlocked(mac: String, blocked: Boolean) {
        call("fname=net&opt=host_black&function=set&mac=$mac&index=${if (blocked) 1 else 0}")
    }

    /** Speed limits in KB/s, 0 = unlimited. */
    fun setLimit(mac: String, downKB: Int, upKB: Int) {
        call("fname=net&opt=host_ls&function=set&mac=$mac&speed=$downKB&up_speed=$upKB")
    }

    fun forgetOffline(mac: String) {
        call("fname=net&opt=host_del_history&function=set&mac=$mac")
    }

    /** action=0 is reboot. (Other values reset the router — never sent by this app.) */
    fun reboot() {
        try {
            call("fname=system&opt=reset_reboot&function=set&action=0")
        } catch (_: IOException) {
            // The router may drop the connection as it goes down — that's fine.
        }
    }

    /**
     * Changes the Wi-Fi names/passwords, keeping every other setting exactly as the router reports it.
     * Mirrors wireless.js: 2.4 GHz first without restart, then 5 GHz three seconds later with restart.
     */
    fun saveWifi(cfg: WifiConfig, ssid2: String, pass2: String, ssid5: String, pass5: String) {
        saveBands(
            cfg.country,
            BandEdit.from(cfg.band2.raw, is5 = false).copy(ssid = ssid2, password = pass2),
            BandEdit.from(cfg.band5.raw, is5 = true).copy(ssid = ssid5, password = pass5),
        )
    }

    fun saveBands(country: String, b2: BandEdit, b5: BandEdit) {
        call(b2.toSetData(country, needRestart = false))
        Thread.sleep(3000)
        try {
            call(b5.toSetData(country, needRestart = true))
        } catch (_: IOException) {
            // Wi-Fi restarts as this is applied, so the reply often never arrives.
        }
    }

    // ---------------------------------------------------------------- generic access (settings pages)

    fun get(fname: String, opt: String, extra: String = ""): JSONObject =
        call("fname=$fname&opt=$opt&function=get$extra")

    fun set(fname: String, opt: String, params: String = ""): JSONObject =
        call("fname=$fname&opt=$opt&function=set$params")

    /** Plain-text CGI outputs (system log, ping results). They read the session from the cookie. */
    fun cgiText(path: String): String {
        val conn = open("http://$host$path")
        token?.let { conn.setRequestProperty("Cookie", "token=$it; lstatus=true") }
        try {
            val code = conn.responseCode
            val body = (if (code >= 400) conn.errorStream else conn.inputStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code >= 400) throw RouterException(code, "The router couldn't provide this (HTTP $code).")
            return body
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val SESSION_EXPIRED = 10007
        const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

        fun hashPassword(password: String): String {
            val d = MessageDigest.getInstance("MD5").digest(("admin$password").toByteArray(Charsets.UTF_8))
            return d.joinToString("") { "%02x".format(it) }
        }

        fun errorText(code: Int): String = when (code) {
            SESSION_EXPIRED -> "Your session expired. Please log in again."
            else -> "The router returned error $code."
        }

        /** JavaScript's encodeURIComponent(), then %20 → space, exactly as the web UI does. */
        fun enc(s: String): String {
            val safe = "-_.!~*'()"
            val sb = StringBuilder()
            for (b in s.toByteArray(Charsets.UTF_8)) {
                val c = b.toInt() and 0xff
                val ch = c.toChar()
                if (c < 128 && (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || safe.indexOf(ch) >= 0)) {
                    sb.append(ch)
                } else {
                    sb.append('%').append(String.format("%02X", c))
                }
            }
            return sb.toString().replace("%20", " ")
        }

        internal fun isOnline(t: JSONObject): Boolean {
            val f = t.optString("flag")
            return f.length > 1 && f[1] == 'T'
        }
    }
}

// ---------------------------------------------------------------- JSON helpers

internal fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

/** Non-empty string value, or null. */
internal fun JSONObject.str(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

/** Value as the web UI would send it back: booleans as 1/0, missing as "". */
internal fun JSONObject.s(key: String): String = when (val v = opt(key)) {
    null, JSONObject.NULL -> ""
    is Boolean -> if (v) "1" else "0"
    else -> v.toString()
}

internal fun JSONObject.truthy(key: String): Boolean = when (val v = opt(key)) {
    is Boolean -> v
    is Number -> v.toInt() != 0
    is String -> v == "1" || v.equals("true", ignoreCase = true)
    else -> false
}

private fun firstLong(objs: List<JSONObject?>, vararg keys: String): Long? {
    for (o in objs) for (k in keys) {
        if (o != null && o.has(k)) o.optString(k).toLongOrNull()?.let { return it }
    }
    return null
}

private fun firstString(objs: List<JSONObject?>, vararg keys: String): String? {
    for (o in objs) for (k in keys) o?.str(k)?.let { return it }
    return null
}
