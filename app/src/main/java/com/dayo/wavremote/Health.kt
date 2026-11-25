package com.dayo.wavremote

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

enum class Severity { PROBLEM, WARNING, TIP }

/** What the button on an issue does. */
sealed interface Fix {
    val label: String
    data class Open(val page: SettingsPage, override val label: String) : Fix
    data class RestartNode(val node: MeshNode, override val label: String = "Restart ${node.name}") : Fix
    data object RestartRouter : Fix { override val label = "Restart the router" }
    data object RunCheck : Fix { override val label = "Find the cause" }
    data object ShowDevices : Fix { override val label = "See devices" }
}

data class Issue(
    val id: String,
    val severity: Severity,
    val title: String,
    val detail: String,
    val fix: Fix? = null,
)

/** Turns what the app knows about the network into plain-language problems and what to do about them. */
object Health {
    private const val PREFS = "wavremote"
    private const val KEY_KNOWN_NODES = "knownMeshNodes"

    fun issues(
        ctx: Context,
        o: Overview?,
        devices: DeviceList?,
        mesh: List<MeshNode>?,
        wifi: WifiConfig?,
        loginHash: String?,
        diag: JSONObject?,
    ): List<Issue> {
        val out = mutableListOf<Issue>()

        // ---- internet
        if (o != null && !o.internetUp) {
            out += when {
                !o.wanLink -> Issue("wan-cable", Severity.PROBLEM, "Internet cable unplugged",
                    "Nothing is connected to the router's internet port, or your modem or fibre box is off. " +
                        "Check the cable between the modem and the router's internet (WAN) port, and that the modem has power.")
                diag != null && diag.optString("wan_ip") == "0" -> Issue("wan-ip", Severity.PROBLEM, "Your provider isn't giving the router an address",
                    "The cable is fine, but your internet provider didn't hand the router an internet address. " +
                        "Restart your modem or fibre box, wait 2 minutes, then restart the router. " +
                        (if (o.wanType.contains("PPPoE")) "As you use PPPoE, also check the username and password in Internet settings." else ""),
                    Fix.Open(SettingsPage.INTERNET, "Internet settings"))
                diag != null && diag.optString("ping_gateway") == "0" -> Issue("wan-gateway", Severity.PROBLEM, "Can't reach your provider",
                    "The router has an address but your provider's equipment isn't answering. Restart your modem or fibre box. " +
                        "If that doesn't help, it's probably an outage: check your provider's service status.")
                diag != null && diag.optString("ping_ip") == "0" -> Issue("wan-outage", Severity.PROBLEM, "Your provider's network is down",
                    "The router reaches your provider, but the internet beyond it doesn't answer. This is usually an outage on their side. " +
                        "Check your provider's service status page, or call them.")
                else -> Issue("wan-down", Severity.PROBLEM, "No internet from your provider",
                    "The router is connected to your modem but isn't getting internet. Restart your modem or fibre box, wait 2 minutes, " +
                        "then restart the router. Tap Find the cause to check each step.",
                    if (diag == null) Fix.RunCheck else Fix.RestartRouter)
            }
        }
        if (o?.internetUp == true && diag != null && diag.optString("dns_analysis") == "0") {
            out += Issue("dns", Severity.PROBLEM, "Websites won't load: name lookup (DNS) is failing",
                "The internet connection works, but looking up website names fails. Switch to a public DNS server such as 1.1.1.1 " +
                    "in Internet settings, or turn off AdGuard if you use it.", Fix.Open(SettingsPage.INTERNET, "Internet settings"))
        }
        if (diag != null) {
            diag.optString("cputime").toIntOrNull()?.let { idle ->
                if (100 - idle >= 90) out += Issue("cpu", Severity.WARNING, "The router is overloaded",
                    "Its processor is almost fully busy, which slows everything down. Restarting it usually clears this.", Fix.RestartRouter)
            }
            val total = diag.optString("totalram").toDoubleOrNull()
            val free = diag.optString("freemem").toDoubleOrNull()
            if (total != null && free != null && total > 0 && (total - free) / total >= 0.92) {
                out += Issue("mem", Severity.WARNING, "The router is almost out of memory",
                    "It can become slow or drop connections. Restart it to free memory.", Fix.RestartRouter)
            }
        }

        // ---- mesh points
        if (mesh != null) {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val known = prefs.getStringSet(KEY_KNOWN_NODES, emptySet())!!.toMutableSet()
            val present = mesh.map { nodeKey(it) }.toSet()
            for (k in known - present) {
                val name = k.substringAfter('|')
                out += Issue("node-off-$k", Severity.PROBLEM, "$name is offline",
                    "This mesh point isn't connected to your network. Check it has power, then unplug it for 10 seconds and plug it back in. " +
                        "If it keeps dropping, move it closer to the main router.", Fix.Open(SettingsPage.MESH, "Mesh settings"))
            }
            if (present.isNotEmpty() && !known.containsAll(present)) {
                known += present
                prefs.edit().putStringSet(KEY_KNOWN_NODES, known).apply()
            }
            for (n in mesh) {
                val r = n.rssi ?: continue
                if (r < -73) out += Issue("node-weak-${nodeKey(n)}", Severity.WARNING, "${n.name} has a weak link to the router",
                    "It's too far from the main router or behind thick walls, so devices near it get slower Wi-Fi. " +
                        "Move it a little closer, or restart it.", if (n.ip != null) Fix.RestartNode(n) else null)
            }
        }

        // ---- security
        if (loginHash != null && loginHash == WavlinkApi.hashPassword("admin")) {
            out += Issue("admin-default", Severity.WARNING, "Your router still uses the default password",
                "Anyone on your Wi-Fi could sign in to the router with \"admin\". Change it to something only you know.",
                Fix.Open(SettingsPage.ADMIN_PASSWORD, "Change password"))
        }
        if (wifi != null) {
            val open = listOf(wifi.band2, wifi.band5).filter { it.enabled && it.security == 0 }
            if (open.isNotEmpty()) out += Issue("wifi-open", Severity.WARNING, "Your Wi-Fi has no password",
                "Anyone nearby can join ${open.joinToString(" and ") { "\"${it.ssid}\"" }} and use your internet.",
                Fix.Open(SettingsPage.WIFI_ADVANCED, "Wi-Fi settings"))
        }
        if (o?.updateAvailable == true) {
            out += Issue("firmware", Severity.WARNING, "A firmware update is available",
                "It brings fixes and security patches for the router.", Fix.Open(SettingsPage.FIRMWARE, "Update firmware"))
        }

        // ---- tips
        val weak = devices?.online?.filter { !it.isWired && it.rssi in 1..25 }.orEmpty()
        if (weak.isNotEmpty()) {
            val names = weak.take(3).joinToString(", ") { it.name } + if (weak.size > 3) " and ${weak.size - 3} more" else ""
            out += Issue("weak-devices", Severity.TIP,
                if (weak.size == 1) "${weak[0].name} has a weak signal" else "${weak.size} devices have a weak signal",
                "$names ${if (weak.size == 1) "is" else "are"} far from the nearest mesh point, so ${if (weak.size == 1) "it" else "they"} may be slow or drop out. " +
                    "Moving a mesh point closer helps.", Fix.ShowDevices)
        }
        val days = (o?.uptimeSec ?: 0L) / 86400
        if (days >= 30) out += Issue("uptime", Severity.TIP, "The router hasn't restarted in $days days",
            "A restart every few weeks keeps it running smoothly. You can schedule one automatically.",
            Fix.Open(SettingsPage.AUTO_REBOOT, "Auto restart"))

        return out.sortedBy { it.severity.ordinal }
    }

    private fun nodeKey(n: MeshNode) = "${n.mac ?: n.ip ?: n.name}|${n.name}"
}

/** "Network health" panel: what's wrong, in plain words, with a fix button. */
@Composable
fun HealthCard(vm: AppViewModel) {
    val wav = Wav.colors
    val issues = vm.issues
    // No internet: run the router's own check straight away to pin down why.
    androidx.compose.runtime.LaunchedEffect(vm.overview?.internetUp) {
        if (vm.overview?.internetUp == false && vm.diag == null) vm.runHealthCheck()
    }
    GroupLabel("Network health")
    Group {
        if (issues.isEmpty()) {
            GroupRow(
                if (vm.diagRunning) "Checking your network…" else "Everything looks good",
                if (vm.diagRunning) "This takes about 20 seconds." else "No problems found with your internet, mesh points or Wi-Fi.",
                icon = Icons.Rounded.CheckCircle, iconTint = wav.signal,
            )
        }
        issues.forEachIndexed { i, issue ->
            if (i > 0) GroupDivider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        when (issue.severity) {
                            Severity.PROBLEM -> Icons.Rounded.ErrorOutline
                            Severity.WARNING -> Icons.Rounded.WarningAmber
                            Severity.TIP -> Icons.Rounded.Lightbulb
                        },
                        contentDescription = null,
                        tint = when (issue.severity) {
                            Severity.PROBLEM -> wav.fault
                            Severity.WARNING -> wav.activity
                            Severity.TIP -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(issue.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(issue.detail, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                issue.fix?.let { fix ->
                    OutlinedButton(
                        onClick = { vm.applyFix(fix) },
                        enabled = !vm.busy && !(fix is Fix.RunCheck && vm.diagRunning),
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) { Text(if (fix is Fix.RunCheck && vm.diagRunning) "Checking…" else fix.label) }
                }
            }
        }
        GroupDivider()
        GroupRow(
            if (vm.diagRunning) "Running a full check…" else "Run a full check",
            "Tests the cable, your provider, DNS, Wi-Fi and router load",
            icon = Icons.Rounded.CheckCircle, iconTint = MaterialTheme.colorScheme.secondary,
            onClick = if (vm.diagRunning) null else ({ vm.runHealthCheck() }),
        )
    }
}
