package com.dayo.wavremote

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private val R_REGION = Req("net", "wifi_region")
private val R_W2 = Req("net", "wifi_ap")
private val R_W5 = Req("net", "wifi_ap_5g")
private val R_AGENTS = Req("net", "wifi_mesh_agent_info")

private val AUTH_OPTIONS = listOf(
    0 to "Open (no password)",
    1 to "WPA2-Personal",
    3 to "WPA/WPA2-Personal (older devices)",
    2 to "WPA3/WPA2-Personal",
)

// ================================================================ Wi-Fi radios & schedule

@Composable
fun WifiAdvancedPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "Wi-Fi radios & schedule", listOf(R_REGION, R_W2, R_W5), optional = listOf(R_AGENTS)) { get ->
        val country = get(R_REGION).optString("country")
        val hasSatellites = (vm.data(R_AGENTS)?.optJSONArray("agent_list")?.length() ?: 0) > 0
        var b2 by remember(get(R_W2)) { mutableStateOf(BandEdit.from(get(R_W2), is5 = false)) }
        var b5 by remember(get(R_W5)) { mutableStateOf(BandEdit.from(get(R_W5), is5 = true)) }
        var confirm by remember { mutableStateOf(false) }

        BandSection("2.4 GHz", b2, { b2 = it }, country, hasSatellites)
        BandSection("5 GHz", b5, { b5 = it }, country, hasSatellites)

        val errors = listOf(b2, b5).mapNotNull { b ->
            when {
                b.auth == 4 -> null
                b.auth != 0 -> wifiPassErr(b.password)
                else -> null
            } ?: b.rules.firstNotNullOfOrNull { it.error }
        }
        if (errors.isNotEmpty()) Note("Fix the highlighted fields first.", error = true)
        Note("Saving restarts Wi-Fi: every device drops off for about 20 seconds.")
        SaveButton(vm, enabled = errors.isEmpty()) { confirm = true }

        if (confirm) ConfirmDialog(
            title = "Apply Wi-Fi changes?",
            text = "Wi-Fi restarts and every device disconnects for about 20 seconds.",
            onDismiss = { confirm = false },
        ) {
            confirm = false
            vm.applySettings("Saved. Wi-Fi is restarting.", listOf(R_W2, R_W5), tolerateDisconnect = true, reloadDelayMs = 20000) {
                it.saveBands(country, b2, b5)
            }
        }
    }
}

@Composable
private fun BandSection(title: String, b: BandEdit, onChange: (BandEdit) -> Unit, country: String, hasSatellites: Boolean) {
    Section(title) {
        SwitchRow("Turn on", b.enabled, { onChange(b.copy(enabled = it)) },
            sub = if (hasSatellites) "Can't be turned off while mesh satellites are connected" else null,
            enabled = !hasSatellites || !b.enabled)
        SwitchRow("Hide network name", b.hidden, { onChange(b.copy(hidden = it)) },
            sub = "Devices must type the name to join")
        if (b.auth == 4) {
            Note("Uses WPA/WPA2-Enterprise (RADIUS) — change that in web settings.")
        } else {
            Dropdown("Security", AUTH_OPTIONS, b.auth, { onChange(b.copy(auth = it)) })
            if (b.auth != 0) Field("Password", b.password, { onChange(b.copy(password = it)) },
                error = wifiPassErr(b.password), password = true)
            else Note("Anyone nearby can join an open network.", error = true)
        }

        if (!b.is5) {
            Dropdown("Channel", listOf("0" to "Auto") + WifiRegions.channels2.map { it to it }, b.channel,
                { onChange(b.copy(channel = it)) })
            Dropdown("Channel width", listOf("0" to "20 MHz", "2" to "40 MHz", "1" to "20/40 MHz (auto)"),
                b.bandwidth, { onChange(b.copy(bandwidth = it)) })
        } else {
            SwitchRow("DFS channels", b.dfs == "1", { on ->
                // Same coupling as the web UI: DFS on ⇒ 160 MHz allowed.
                val bw = if (on) "1" else "0"
                val dfs = if (on) "1" else "0"
                val ch = if (b.channel in WifiRegions.channels5(country, dfs, bw)) b.channel else "0"
                onChange(b.copy(dfs = dfs, bandwidth = bw, channel = ch))
            }, sub = "More channels and 160 MHz, but can pause briefly for radar checks")
            val channels = WifiRegions.channels5(country, b.dfs, b.bandwidth)
            Dropdown("Channel", listOf("0" to "Auto") + channels.map { it to it }, b.channel,
                { onChange(b.copy(channel = it)) })
            Dropdown("Channel width", listOf("0" to "80 MHz", "1" to "20/80/160 MHz"), b.bandwidth, { bw ->
                val ch = if (b.channel in WifiRegions.channels5(country, b.dfs, bw)) b.channel else "0"
                onChange(b.copy(bandwidth = bw, channel = ch))
            })
        }
        SwitchRow("TWT (battery saving)", b.twt, { onChange(b.copy(twt = it)) }, sub = "Wi-Fi 6 target wake time")
        SwitchRow("OFDMA", b.ofdma, { onChange(b.copy(ofdma = it)) }, sub = "Wi-Fi 6 multi-user efficiency")
        HorizontalDivider()
        Text("Off-times", style = MaterialTheme.typography.titleSmall)
        ScheduleEditor(b.rules, { onChange(b.copy(rules = it)) }, what = "This band is off during these times")
    }
}

// ================================================================ Guest network

private val R_GUEST = Req("net", "wifi_ap_guest")

@Composable
fun GuestWifiPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "Guest network", listOf(R_GUEST)) { get ->
        val d = get(R_GUEST)
        val wasOn = d.s("guest_enable") == "1"
        var enabled by remember(d) { mutableStateOf(wasOn) }
        var ssid by remember(d) { mutableStateOf(d.optString("SSID")) }
        var mode by remember(d) { mutableStateOf(d.s("guest_mode").ifEmpty { "0" }) }
        var pwd by remember(d) { mutableStateOf(d.optString("passwd")) }
        var isolation by remember(d) { mutableStateOf(d.s("ap_isolation").ifEmpty { "1" }) }
        var rules by remember(d) { mutableStateOf(ScheduleRule.parseAll(d.optJSONArray("rules"), masterOn = wasOn)) }
        val meshAp = d.s("ap_isolation") == "2"
        val canEditMode = mode == "0" || mode == "1"

        Section {
            SwitchRow("Guest network", enabled, { enabled = it }, sub = "A separate Wi-Fi that can't see your devices")
        }
        if (enabled) {
            Section("Network") {
                Field("Network name", ssid, { ssid = it }, error = ssidErr(ssid))
                if (canEditMode) {
                    Dropdown("Security", listOf("0" to "Password", "1" to "Open (no password)"), mode, { mode = it })
                    if (mode == "0") Field("Password", pwd, { pwd = it }, error = wifiPassErr(pwd), password = true)
                } else {
                    Note(if (mode == "2") "Uses one-touch access — change the mode in web settings."
                    else "Uses WPA/WPA2-Enterprise — change it in web settings.")
                }
                if (!meshAp) SwitchRow("Keep guests apart", isolation == "1", { isolation = if (it) "1" else "0" },
                    sub = "Guests can't see each other's devices")
            }
            Section("Blocked times") {
                ScheduleEditor(rules, { rules = it }, what = "Guest Wi-Fi is blocked during these times")
            }
        }
        val err = if (!enabled) null else ssidErr(ssid) ?: (if (mode == "0") wifiPassErr(pwd) else null)
            ?: rules.firstNotNullOfOrNull { it.error }
        SaveButton(vm, enabled = err == null) {
            val enc: (String) -> String = { WavlinkApi.enc(it) }
            val data = "&need_restart=1&enable=${if (enabled) 1 else 0}&guestMode=$mode" +
                "&ssid=${enc(ssid)}&password=${enc(pwd)}&radiusip=${d.s("radiusip")}&radiusport=${d.s("radiusport")}" +
                "&radiuspwd=${enc(d.s("radiuspwd"))}&apIsolation=$isolation" +
                "&touchDuration=${d.s("touch_duration_time").ifEmpty { "0" }}" +
                "&touchExpiration=${d.s("touch_expiration_time").ifEmpty { "0" }}" +
                "&rules=${ScheduleRule.toJson(if (enabled) rules else rules.map { it.copy(enabled = false) })}"
            vm.applySettings("Guest network saved.", listOf(R_GUEST), tolerateDisconnect = true, reloadDelayMs = 15000) {
                it.set("net", "wifi_ap_guest", data)
            }
        }
    }
}

// ================================================================ Kids' Wi-Fi

private val R_KIDS = Req("net", "wifi_lt", "&wifi_type=_child")

@Composable
fun KidsWifiPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "Kids' Wi-Fi", listOf(R_KIDS)) { get ->
        val d = get(R_KIDS)
        val wasOn = d.s("enable") == "1"
        val rawSsid = d.optString("SSID")
        var enabled by remember(d) { mutableStateOf(wasOn) }
        var ssid by remember(d) {
            mutableStateOf(if ((rawSsid.isEmpty() || rawSsid == "WAVLINK" || rawSsid == "MeshGo") && !wasOn) "Parental-Wi-Fi" else rawSsid)
        }
        var auth by remember(d) { mutableStateOf(d.s("security").let { if (it == "2") "2" else "1" }) }
        var pwd by remember(d) { mutableStateOf(d.optString("passwd")) }
        var rules by remember(d) { mutableStateOf(ScheduleRule.parseAll(d.optJSONArray("rules"), masterOn = wasOn)) }

        Section {
            SwitchRow("Kids' Wi-Fi", enabled, { enabled = it },
                sub = "A separate network for your children's devices, with internet blocked at set times")
        }
        if (enabled) {
            Section("Network") {
                Field("Network name", ssid, { ssid = it }, error = ssidErr(ssid))
                Dropdown("Security", listOf("1" to "WPA2-Personal", "2" to "WPA3/WPA2-Personal"), auth, { auth = it })
                Field("Password", pwd, { pwd = it }, error = wifiPassErr(pwd), password = true)
            }
            Section("Internet blocked during") {
                ScheduleEditor(rules, { rules = it }, what = "No internet on this network during these times")
                if (rules.none { it.enabled }) Note("Turn on at least one schedule.", error = true)
            }
        }
        val err = if (!enabled) null else ssidErr(ssid) ?: wifiPassErr(pwd)
            ?: rules.firstNotNullOfOrNull { it.error } ?: (if (rules.none { it.enabled }) "rule" else null)
        SaveButton(vm, enabled = err == null) {
            val data = "&wifi_type=_child&enable=${if (enabled) 1 else 0}&ssid=${WavlinkApi.enc(ssid)}" +
                "&password=${WavlinkApi.enc(pwd)}&authmode=$auth" +
                "&rules=${ScheduleRule.toJson(if (enabled) rules else rules.map { it.copy(enabled = false) })}"
            vm.applySettings("Kids' Wi-Fi saved.", listOf(R_KIDS), tolerateDisconnect = true, reloadDelayMs = 15000) {
                it.set("net", "wifi_lt", data)
            }
        }
    }
}

// ================================================================ Mesh

private val R_MESH_ADV = Req("net", "mesh_advanced")

@Composable
fun MeshPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "Mesh", listOf(R_AGENTS, R_MESH_ADV)) { get ->
        val agents = get(R_AGENTS).optJSONArray("agent_list")?.objects() ?: emptyList()
        val adv = get(R_MESH_ADV)
        var renaming by remember { mutableStateOf<Pair<String, String>?>(null) }   // pair_key to name
        var removing by remember { mutableStateOf<Pair<String, String>?>(null) }

        Section("Satellites") {
            if (agents.isEmpty()) Note("No satellites connected.")
            agents.forEach { a ->
                val key = a.optString("pair_key").ifEmpty { a.optString("pairKey") }
                val name = a.optString("name").ifEmpty { "Satellite" }
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(name) },
                    supportingContent = {
                        Text(listOfNotNull(a.str("ip_addr"), a.str("rssi")?.let { "Signal $it dBm" },
                            a.str("BHLType")?.let { if (it == "1") "Wired backhaul" else "Wireless backhaul" })
                            .joinToString(" · "))
                    },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { renaming = key to name }) { Text("Rename") }
                            TextButton(onClick = { removing = key to name }) { Text("Remove") }
                        }
                    },
                )
            }
            OutlinedButton(onClick = {
                vm.applySettings("Optimising the mesh layout…", listOf(R_AGENTS), reloadDelayMs = 5000) {
                    it.set("net", "mesh_topo_optimization")
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Optimise mesh") }
        }

        var bs by remember(adv) { mutableStateOf(adv.s("bs_enable") == "1") }
        var bs2 by remember(adv) { mutableStateOf(adv.s("bs_threshold_2")) }
        var bs5 by remember(adv) { mutableStateOf(adv.s("bs_threshold_5")) }
        var roam by remember(adv) { mutableStateOf(adv.s("roaming_enable") == "1") }
        var roamT by remember(adv) { mutableStateOf(adv.s("roaming_threshold")) }
        var topoT by remember(adv) { mutableStateOf(adv.s("threshold_topo_update")) }
        Section("Advanced") {
            SwitchRow("Band steering", bs, { bs = it }, sub = "Move capable devices to the faster 5 GHz band")
            if (bs) {
                Field("2.4 GHz threshold (dBm)", bs2, { bs2 = it }, hint = "e.g. -60")
                Field("5 GHz threshold (dBm)", bs5, { bs5 = it }, hint = "e.g. -75")
            }
            SwitchRow("Fast roaming", roam, { roam = it }, sub = "Hand devices to the nearest node")
            if (roam) Field("Roaming threshold (dBm)", roamT, { roamT = it })
            Field("Topology update threshold", topoT, { topoT = it })
            SaveButton(vm) {
                vm.applySettings("Mesh settings saved.", listOf(R_MESH_ADV)) {
                    it.set("net", "mesh_advanced", "&bs_enable=${if (bs) 1 else 0}&bs_threshold_2=$bs2" +
                        "&bs_threshold_5=$bs5&roaming_enable=${if (roam) 1 else 0}&roaming_threshold=$roamT" +
                        "&topo_update_threshold=$topoT")
                }
            }
        }

        renaming?.let { (key, old) ->
            var name by remember(key) { mutableStateOf(old) }
            AlertDialog(
                onDismissRequest = { renaming = null },
                title = { Text("Rename satellite") },
                text = {
                    OutlinedTextField(value = name, onValueChange = { if (it.length <= 31) name = it },
                        singleLine = true, label = { Text("Name") })
                },
                confirmButton = {
                    TextButton(enabled = name.isNotBlank(), onClick = {
                        renaming = null
                        vm.applySettings("Renamed.", listOf(R_AGENTS)) {
                            it.set("net", "wifi_mesh_changeExtenderName", "&mac=$key&newName=${WavlinkApi.enc(name)}")
                        }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
            )
        }
        removing?.let { (key, name) ->
            ConfirmDialog("Remove $name?", "It will leave the mesh. To add it back, pair it again.",
                onDismiss = { removing = null }, confirmLabel = "Remove") {
                removing = null
                vm.applySettings("$name removed.", listOf(R_AGENTS), reloadDelayMs = 3000) {
                    it.set("net", "scan_select_extender", "&macAction=0&pair_key_list=$key")
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    confirmLabel: String = "Apply",
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
