package com.dayo.wavremote

import org.json.JSONArray
import org.json.JSONObject

/**
 * One of the router's 3 schedule slots (Wi-Fi off / guest / kids Wi-Fi blocking periods).
 * days[0] = Sunday … days[6] = Saturday, matching the web UI's "week" string.
 */
data class ScheduleRule(
    val enabled: Boolean = false,
    val days: List<Boolean> = List(7) { false },
    val sh: Int = 0, val sm: Int = 0,
    val eh: Int = 0, val em: Int = 0,
) {
    val startMin get() = sh * 60 + sm
    val endMin get() = eh * 60 + em

    /** Same checks as the web UI. */
    val error: String?
        get() = when {
            !enabled -> null
            startMin == endMin -> "Start and end time can't be the same"
            startMin > endMin -> "Start time must be before end time"
            days.none { it } -> "Pick at least one day"
            else -> null
        }

    fun toJson(): String = if (!enabled) DISABLED else {
        val week = days.joinToString("") { if (it) "1" else "0" }
        "{\"enable\":1,\"week\":\"$week\",\"sh\":\"$sh\",\"sm\":\"$sm\",\"eh\":\"$eh\",\"em\":\"$em\"}"
    }

    companion object {
        private const val DISABLED = "{\"enable\":0,\"week\":\"0000000\",\"sh\":0,\"sm\":0,\"eh\":0,\"em\":0}"

        /** Reads the router's rules array. [masterOn] mirrors the web UI, which shows rules as off when the feature is off. */
        fun parseAll(arr: JSONArray?, masterOn: Boolean = true): List<ScheduleRule> = (0 until 3).map { i ->
            val o = arr?.optJSONObject(i) ?: return@map ScheduleRule()
            val days = when (val w = o.opt("week")) {
                is JSONArray -> (0 until 7).map { w.opt(it)?.toString() == "1" }
                is String -> (0 until 7).map { w.getOrNull(it) == '1' }
                else -> List(7) { false }
            }
            ScheduleRule(
                enabled = o.optInt("enable", 0) == 1 && masterOn,
                days = days,
                sh = o.optString("sh").toIntOrNull() ?: 0,
                sm = o.optString("sm").toIntOrNull() ?: 0,
                eh = o.optString("eh").toIntOrNull() ?: 0,
                em = o.optString("em").toIntOrNull() ?: 0,
            )
        }

        fun toJson(rules: List<ScheduleRule>): String =
            "[" + (0 until 3).joinToString(",") { (rules.getOrNull(it) ?: ScheduleRule()).toJson() } + "]"
    }
}

/** Everything the router needs to save one Wi-Fi band (wifi_ap / wifi_ap_5g set). */
data class BandEdit(
    val is5: Boolean,
    val ssid: String,
    val password: String,
    val auth: Int,
    val radiusIp: String,
    val radiusPort: String,
    val radiusPwd: String,
    val channel: String,
    val enabled: Boolean,
    val hidden: Boolean,
    val bandwidth: String,
    val dfs: String,
    val twt: Boolean,
    val ofdma: Boolean,
    val rules: List<ScheduleRule>,
) {
    fun toSetData(country: String, needRestart: Boolean): String {
        val pwd = if (auth == 0) "Admin12345" else password   // what the web UI sends for open networks
        val enc: (String) -> String = { WavlinkApi.enc(it) }
        val common = "&ssid=${enc(ssid)}&password=${enc(pwd)}&authmode=$auth" +
            "&radiusip=$radiusIp&radiusport=$radiusPort&radiuspwd=${enc(radiusPwd)}&channel=$channel"
        val restart = if (needRestart) 1 else 0
        val en = if (enabled) 1 else 0
        val r = ScheduleRule.toJson(rules)
        return if (!is5) {
            "fname=net&opt=wifi_ap&function=set$common&need_restart=$restart&enable=$en" +
                "&hidden2g=${b(hidden)}&bandwidth=$bandwidth&twt2g=${b(twt)}&ofdma2g=${b(ofdma)}&rules=$r"
        } else {
            "fname=net&opt=wifi_ap_5g&function=set$common&dfs=$dfs&need_restart=$restart&enable=$en" +
                "&hidden5g=${b(hidden)}&bandwidth=$bandwidth&skiplist=${WifiRegions.skipList5(country, bandwidth, dfs)}" +
                "&twt5g=${b(twt)}&ofdma5g=${b(ofdma)}&rules=$r"
        }
    }

    private fun b(v: Boolean) = if (v) "1" else "0"

    companion object {
        fun from(r: JSONObject, is5: Boolean): BandEdit {
            val auth = r.s("security").toIntOrNull() ?: 0
            return BandEdit(
                is5 = is5,
                ssid = r.optString(if (is5) "SSID5g" else "SSID2g"),
                password = if (auth == 0) "" else r.optString("passwd"),
                auth = auth,
                radiusIp = r.s("radiusip"),
                radiusPort = r.s("radiusport"),
                radiusPwd = r.s("radiuspwd"),
                channel = r.s("channel").ifEmpty { "0" },
                enabled = r.s("enable") != "0",
                hidden = r.s(if (is5) "hidden5g" else "hidden2g") == "1",
                bandwidth = r.s("bandwidth").ifEmpty { "0" },
                dfs = r.s("dfs").ifEmpty { "0" },
                twt = r.s(if (is5) "twt5g" else "twt2g") == "1",
                ofdma = r.s(if (is5) "ofdma5g" else "ofdma2g") == "1",
                rules = ScheduleRule.parseAll(r.optJSONArray("rules")),
            )
        }
    }
}
