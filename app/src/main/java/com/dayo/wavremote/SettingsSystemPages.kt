package com.dayo.wavremote

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun b(v: Boolean) = if (v) 1 else 0

// ================================================================ Time zone

private val R_TZ = Req("system", "timezone")

/** Router values (POSIX-style, sign inverted) → labels, in the web UI's order. */
private val TIME_ZONES = listOf(
    "UTC+12:00" to "(UTC-12:00) International Date Line West", "UTC+11:00" to "(UTC-11:00) Samoa",
    "UTC+10:00" to "(UTC-10:00) Hawaii", "UTC+09:30" to "(UTC-09:30) Marquesas Islands",
    "UTC+09:00" to "(UTC-09:00) Alaska", "UTC+08:00" to "(UTC-08:00) Pacific Time (US & Canada)",
    "UTC+07:00" to "(UTC-07:00) Mountain Time (US & Canada)", "UTC+06:00" to "(UTC-06:00) Mexico City",
    "UTC+05:00" to "(UTC-05:00) Bogota, Lima, Quito", "UTC+04:30" to "(UTC-04:30) Caracas",
    "UTC+04:00" to "(UTC-04:00) Atlantic Time (Canada)", "UTC+03:30" to "(UTC-03:30) Newfoundland",
    "UTC+03:00" to "(UTC-03:00) Brasilia", "UTC+02:00" to "(UTC-02:00) Mid Atlantic",
    "UTC+01:00" to "(UTC-01:00) Cabo Verde", "UTC+00:00" to "(UTC+00:00) London, Dublin, Lisbon",
    "UTC-01:00" to "(UTC+01:00) Amsterdam, Berlin, Rome, Stockholm", "UTC-02:00" to "(UTC+02:00) Helsinki, Kyiv, Riga, Sofia",
    "UTC-03:00" to "(UTC+03:00) Moscow, St. Petersburg", "UTC-03:30" to "(UTC+03:30) Tehran",
    "UTC-04:00" to "(UTC+04:00) Abu Dhabi, Muscat", "UTC-04:30" to "(UTC+04:30) Kabul",
    "UTC-05:00" to "(UTC+05:00) Islamabad, Karachi", "UTC-05:30" to "(UTC+05:30) Mumbai, New Delhi",
    "UTC-05:45" to "(UTC+05:45) Kathmandu", "UTC-06:00" to "(UTC+06:00) Astana",
    "UTC-06:30" to "(UTC+06:30) Yangon", "UTC-07:00" to "(UTC+07:00) Bangkok, Hanoi, Jakarta",
    "UTC-08:00" to "(UTC+08:00) Beijing, Hong Kong", "UTC-08:45" to "(UTC+08:45) Eucla",
    "UTC-09:00" to "(UTC+09:00) Tokyo, Osaka", "UTC-09:30" to "(UTC+09:30) Adelaide",
    "UTC-10:00" to "(UTC+10:00) Brisbane", "UTC-10:30" to "(UTC+10:30) Lord Howe Island",
    "UTC-11:00" to "(UTC+11:00) Solomon Islands", "UTC-11:30" to "(UTC+11:30) Norfolk",
    "UTC-12:00" to "(UTC+12:00) Auckland, Wellington", "UTC-12:45" to "(UTC+12:45) Chatham Islands",
    "UTC-13:00" to "(UTC+13:00) Nuku'alofa", "UTC-14:00" to "(UTC+14:00) Kiritimati",
)

/** Port of common.js UTC2DST: flag=1 converts a base zone to its DST value, flag=0 back. */
private fun utc2dst(tz: String, toDst: Boolean): String {
    if (tz.length < 9) return tz
    var sign = tz[3]
    var h = tz.substring(4, 6).toIntOrNull() ?: return tz
    val m = tz.substring(7)
    h = if (toDst) (if (sign == '+') h - 1 else h + 1) else (if (sign == '+') h + 1 else h - 1)
    val hs = if (h <= 0) {
        sign = if (sign == '+') '-' else '+'
        "0" + (-h)
    } else if (h < 10) "0$h" else "$h"
    return "UTC$sign$hs:$m"
}

@Composable
fun TimeZonePage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "Time zone", listOf(R_TZ)) { get ->
        val d = get(R_TZ)
        val dstOn = d.s("enable") == "1"
        var dst by remember(d) { mutableStateOf(dstOn) }
        var tz by remember(d) { mutableStateOf(d.optString("tz").let { if (dstOn) utc2dst(it, false) else it }) }
        Section {
            InfoRow("Router time", d.optString("date"))
            Dropdown("Time zone", TIME_ZONES, tz, { tz = it })
            SwitchRow("Daylight saving time", dst, { dst = it }, sub = "Turn on for UK summer time (BST)")
        }
        SaveButton(vm) {
            val out = if (dst) utc2dst(tz, true) else tz
            vm.applySettings("Time zone saved.", listOf(R_TZ)) {
                it.set("system", "timezone", "&tz=$out&enable=${b(dst)}")
            }
        }
    }
}

// ================================================================ LED

private val R_LED = Req("system", "led", "&act=dump", viaSet = true)

@Composable
fun LedPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "Lights", listOf(R_LED)) { get ->
        val d = get(R_LED)
        var on by remember(d) { mutableStateOf(d.s("led") == "1") }
        Section {
            SwitchRow("Status lights", on, { on = it }, sub = "Turn off to keep a bedroom dark")
            SaveButton(vm) {
                vm.applySettings("Lights turned ${if (on) "on" else "off"}.", listOf(R_LED)) {
                    it.set("system", "led", "&act=${if (on) "on" else "off"}")
                }
            }
        }
    }
}

// ================================================================ Scheduled restart

private val R_REBOOT = Req("net", "timing_reboot")
private val MON_FIRST = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

@Composable
fun AutoRebootPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "Scheduled restart", listOf(R_REBOOT), optional = listOf(R_TZ)) { get ->
        val d = get(R_REBOOT)
        // week like "1,0,3,0,0,0,7": position i (Mon=1 … Sun=7) holds i when selected.
        val wk = d.optString("week").replace(",", "")
        var on by remember(d) { mutableStateOf(d.s("enable") == "1") }
        var days by remember(d) { mutableStateOf((1..7).map { i -> wk.getOrNull(i - 1) == ('0' + i) }) }
        var hour by remember(d) { mutableStateOf(d.s("hour").toIntOrNull() ?: 3) }
        var min by remember(d) { mutableStateOf(d.s("min").toIntOrNull() ?: 0) }
        Section {
            SwitchRow("Restart automatically", on, { on = it }, sub = "Keeps the network fresh — pick a time nobody's online")
            vm.data(R_TZ)?.optString("date")?.let { InfoRow("Router time", it) }
            if (on) {
                DaysPicker(days, { days = it }, labels = MON_FIRST)
                TimeRow("At", hour, min) { h, m -> hour = h; min = m }
                if (days.none { it }) Note("Pick at least one day.", error = true)
            }
        }
        SaveButton(vm, enabled = !on || days.any { it }) {
            val week = if (on) (1..7).joinToString(",") { i -> if (days[i - 1]) "$i" else "0" } else "0,0,0,0,0,0,0"
            vm.applySettings("Scheduled restart saved.", listOf(R_REBOOT)) {
                it.set("net", "timing_reboot", "&enable=${b(on)}&week=$week&hour=${if (on) hour else 0}&min=${if (on) min else 0}")
            }
        }
    }
}

// ================================================================ Admin password

@Composable
fun AdminPasswordPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "Admin password", emptyList()) {
        var old by remember { mutableStateOf("") }
        var new by remember { mutableStateOf("") }
        var again by remember { mutableStateOf("") }
        val newErr = when {
            new.isEmpty() -> null
            new.length < 6 || new.length > 30 -> "6–30 characters"
            !plainAscii(new) -> "Plain letters, numbers and symbols only"
            new == old -> "Must be different from the current password"
            else -> null
        }
        val againErr = if (again.isNotEmpty() && again != new) "Doesn't match" else null
        Section {
            Field("Current password", old, { old = it }, password = true)
            Field("New password", new, { new = it }, password = true, error = newErr)
            Field("Confirm new password", again, { again = it }, password = true, error = againErr)
            Note("The app signs back in with the new password automatically.")
        }
        SaveButton(vm, enabled = old.isNotEmpty() && new.isNotEmpty() && newErr == null && again == new, label = "Change password") {
            vm.changeAdminPassword(old, new) { old = ""; new = ""; again = "" }
        }
    }
}

// ================================================================ Firmware

private val R_MESH_ONLINE = Req("net", "mesh_online_list")
private val R_OTA = Req("sys", "ota_check")

@Composable
fun FirmwarePage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "Firmware update", listOf(R_MESH_ONLINE)) { get ->
        val nodes = get(R_MESH_ONLINE).optJSONArray("mesh_online_list")?.objects() ?: emptyList()
        val ota = vm.data(R_OTA)?.optJSONArray("mesh_ota_check_ret")?.objects() ?: emptyList()
        val updates = ota.associateBy { it.optString("pair_key").lowercase() }
        var confirm by remember { mutableStateOf(false) }

        Section("Installed") {
            nodes.forEachIndexed { i, n ->
                val u = updates[n.optString("pair_key").lowercase()]
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(if (i == 0) "Main router" else n.optString("name").ifEmpty { "Satellite $i" }) },
                    supportingContent = {
                        Text(n.optString("version") + (u?.let { "\nUpdate available: ${it.optString("new_version")}" } ?: ""))
                    },
                )
            }
        }
        OutlinedButton(onClick = {
            val macs = nodes.drop(1).joinToString(",") { it.optString("pair_key") }
            vm.runAndRead("fname=sys&opt=ota_check&function=set&router_check=1&mac=$macs", 8000, R_OTA)
        }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.busy) "Checking…" else "Check for updates")
        }
        if (vm.data(R_OTA) != null && updates.isEmpty()) Note("Everything is up to date.")
        if (updates.isNotEmpty()) {
            ota.forEach { u -> u.str("version_info")?.let { Section("What's new in ${u.optString("new_version")}") { Note(it) } } }
            SaveButton(vm, label = "Install updates") { confirm = true }
        }
        if (confirm) ConfirmDialog("Install firmware updates?",
            "The network goes down for several minutes while each node updates and restarts. Don't unplug anything.",
            onDismiss = { confirm = false }, confirmLabel = "Install") {
            confirm = false
            val routerKey = nodes.firstOrNull()?.optString("pair_key")?.lowercase()
            val routerUp = if (routerKey != null && updates.containsKey(routerKey)) 1 else 0
            val macs = nodes.drop(1).filter { updates.containsKey(it.optString("pair_key").lowercase()) }
                .joinToString(",") { it.optString("pair_key") }
            vm.applySettings("Updating — this takes several minutes. Reconnect when the lights settle.", tolerateDisconnect = true) {
                it.set("sys", "ota_upgrade", "&router_up=$routerUp&mac=$macs")
            }
        }
    }
}

// ================================================================ Network check

private val R_DIAG = Req("sys", "netdiagnostics")

@Composable
fun DiagnosticsPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "Network check", emptyList()) {
        val d = vm.data(R_DIAG)
        Section {
            Note("Checks the internet cable, connection, DNS, Wi-Fi interference and router load (about 20 seconds).")
            SaveButton(vm, label = if (vm.busy) "Checking…" else "Run check") {
                vm.runAndRead("fname=sys&opt=netdiagnostics&function=set", 18000, R_DIAG)
            }
        }
        if (d != null) Section("Results") {
            if (d.s("wan_link") == "0") {
                Note("No cable in the internet port.", error = true)
            } else {
                InfoRow("Internet cable", when {
                    d.s("wan_speed").toIntOrNull()?.let { it >= 0 } == true ->
                        "${d.s("wan_speed")} Mbps " + if (d.s("wan_duplex") == "2") "full duplex" else "half duplex"
                    else -> "Connected"
                })
                InfoRow("Internet address", if (d.s("wan_ip") == "0") "Not received" else "OK")
                InfoRow("Reach gateway", if (d.s("ping_gateway") == "0") "Failed" else "OK")
                InfoRow("DNS", if (d.s("dns_analysis") == "0") "Failed" else "OK")
                InfoRow("Reach internet", if (d.s("ping_ip") == "0") "Failed" else "OK")
            }
            InfoRow("2.4 GHz Wi-Fi", wifiState(d.s("wifi_2g_num")))
            InfoRow("5 GHz Wi-Fi", wifiState(d.s("wifi_5g_num")))
            d.s("cputime").toIntOrNull()?.let { InfoRow("CPU in use", "${(100 - it).coerceAtLeast(1)}%") }
            val total = d.s("totalram").toDoubleOrNull(); val free = d.s("freemem").toDoubleOrNull()
            if (total != null && free != null && total > 0) InfoRow("Memory in use", "${((total - free) / total * 100).toInt()}%")
        }
    }
}

private fun wifiState(v: String): String {
    val n = v.toIntOrNull() ?: return "—"
    return when {
        n == -3 -> "Off"
        n == -2 -> "Unknown"
        n == -1 -> "Problem detected"
        n <= 10 -> "OK"
        else -> "Busy channel ($n networks nearby)"
    }
}

// ================================================================ Ping / traceroute

@Composable
fun PingPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "Ping & traceroute", emptyList()) {
        var target by remember { mutableStateOf("google.com") }
        var action by remember { mutableStateOf("ping") }
        val valid = Regex("^([a-zA-Z0-9\\-]+(\\.[a-zA-Z0-9\\-]+)*\\.[a-zA-Z]{2,63})$").matches(target) || isIp(target)
        Section {
            Dropdown("Test", listOf("ping" to "Ping", "traceroute" to "Traceroute"), action, { action = it })
            Field("Website or IP", target, { target = it.trim() }, error = if (valid) null else "Enter a domain or IP")
            SaveButton(vm, enabled = valid && !vm.textBusy, label = if (vm.textBusy) "Running…" else "Run from the router") {
                val a = action; val t = target
                vm.runText({ it.set("net", "ping_or_trace", "&action=$a&ip_domainname=$t") },
                    if (a == "ping") 5000 else 30000, "/cgi-bin/export_pingortrace.cgi")
            }
        }
        OutputBox(vm)
    }
}

// ================================================================ System log

@Composable
fun SystemLogPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "System log", emptyList()) {
        LaunchedEffect(Unit) { vm.runText(null, 0, "/cgi-bin/export_systemlog.cgi") }
        OutlinedButton(onClick = { vm.runText(null, 0, "/cgi-bin/export_systemlog.cgi") }, modifier = Modifier.fillMaxWidth()) {
            Text("Reload log")
        }
        OutputBox(vm)
    }
}

@Composable
private fun OutputBox(vm: AppViewModel) {
    if (vm.textBusy) CircularProgressIndicator(Modifier.size(28.dp))
    vm.textOutput?.let { out ->
        Section("Output") {
            Text(
                out,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}

// ================================================================ Factory reset

@Composable
fun FactoryResetPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "Factory reset", emptyList()) {
        var typed by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf(false) }
        Section {
            Note("Erases every setting: Wi-Fi names and passwords, admin password, reservations, port forwards — " +
                "everything. The router restarts with the defaults on its label, and satellites may need re-pairing.", error = true)
            Field("Type RESET to confirm", typed, { typed = it })
        }
        SaveButton(vm, enabled = typed == "RESET", label = "Erase everything") { confirm = true }
        if (confirm) ConfirmDialog("Factory reset the router?", "This can't be undone.",
            onDismiss = { confirm = false }, confirmLabel = "Erase") {
            confirm = false
            vm.factoryReset()
        }
    }
}
