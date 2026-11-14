package com.dayo.wavremote

import org.json.JSONObject

/** Reads and one-tap toggles used by the Home screen's quick controls. */
object Quick {
    val GUEST = Req("net", "wifi_ap_guest")
    val LED = Req("system", "led", "&act=dump", viaSet = true)
    val KIDS = Req("net", "wifi_lt", "&wifi_type=_child")
    val all = listOf(GUEST, LED, KIDS)

    private fun enc(s: String) = WavlinkApi.enc(s)

    /** Same request the Guest page sends, with only "enable" changed. Null if the guest network needs setting up first. */
    fun guestData(d: JSONObject, on: Boolean): String? {
        val mode = d.s("guest_mode").ifEmpty { "0" }
        val pwd = d.optString("passwd")
        if (on && (d.optString("SSID").isBlank() || (mode == "0" && wifiPassErr(pwd) != null))) return null
        val rules = ScheduleRule.parseAll(d.optJSONArray("rules"), masterOn = on)
        return "&need_restart=1&enable=${if (on) 1 else 0}&guestMode=$mode" +
            "&ssid=${enc(d.optString("SSID"))}&password=${enc(pwd)}&radiusip=${d.s("radiusip")}&radiusport=${d.s("radiusport")}" +
            "&radiuspwd=${enc(d.s("radiuspwd"))}&apIsolation=${d.s("ap_isolation").ifEmpty { "1" }}" +
            "&touchDuration=${d.s("touch_duration_time").ifEmpty { "0" }}" +
            "&touchExpiration=${d.s("touch_expiration_time").ifEmpty { "0" }}" +
            "&rules=${ScheduleRule.toJson(rules)}"
    }

    /** Kids' Wi-Fi can only be switched on once it has a password and at least one blocked time. */
    fun kidsData(d: JSONObject, on: Boolean): String? {
        val rules = ScheduleRule.parseAll(d.optJSONArray("rules"), masterOn = on)
        val pwd = d.optString("passwd")
        if (on && (wifiPassErr(pwd) != null || rules.none { it.enabled } || d.optString("SSID").isBlank())) return null
        val auth = d.s("security").let { if (it == "2") "2" else "1" }
        return "&wifi_type=_child&enable=${if (on) 1 else 0}&ssid=${enc(d.optString("SSID"))}" +
            "&password=${enc(pwd)}&authmode=$auth&rules=${ScheduleRule.toJson(rules)}"
    }
}
