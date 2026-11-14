package com.dayo.wavremote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private fun b(v: Boolean) = if (v) 1 else 0

// ================================================================ Internet (WAN)

private val R_WAN = Req("net", "wan_conf")
private val R_BASE = Req("sys", "baseinfo")

@Composable
fun InternetPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "Internet connection", listOf(R_WAN), optional = listOf(R_BASE)) { get ->
        val w = get(R_WAN)
        val advPppoe = vm.data(R_BASE)?.s("pppoe_adv_support") == "1"
        if (w.s("device_mode").ifEmpty { "1" } != "1") {
            Section { Note("The router isn't in router mode, so its internet settings are managed by your main router.") }
        } else {
            InternetForm(vm, w, advPppoe)
        }
    }
}

@Composable
private fun InternetForm(vm: AppViewModel, w: JSONObject, advPppoe: Boolean) {
        val origMode = w.s("mode").ifEmpty { "1" }
        var mode by remember(w) { mutableStateOf(origMode) }
        var user by remember(w) { mutableStateOf(w.optString("user")) }
        var pass by remember(w) { mutableStateOf(w.optString("passwd")) }
        var service by remember(w) { mutableStateOf(w.optString("server")) }
        var ip by remember(w) { mutableStateOf(w.optString("ip")) }
        var mask by remember(w) { mutableStateOf(w.optString("mask")) }
        var gw by remember(w) { mutableStateOf(w.optString("gw")) }
        var dnsManual by remember(w) { mutableStateOf(w.s("dns_manual") == "1") }
        var dns1 by remember(w) { mutableStateOf(w.optString("dns1").ifEmpty { "" }) }
        var dns2 by remember(w) { mutableStateOf(w.optString("dns2").ifEmpty { "" }) }
        var mtuManual by remember(w) { mutableStateOf(w.s("mtu_manual") == "1") }
        var mtu by remember(w) { mutableStateOf(w.s("mtu").ifEmpty { "1500" }) }
        var clone by remember(w) { mutableStateOf(w.s("mac_clone") == "1") }
        var mac by remember(w) { mutableStateOf(w.optString("mac").uppercase()) }
        var confirm by remember { mutableStateOf(false) }

        val editable = mode in listOf("1", "2", "3")
        Section("Connection type") {
            Dropdown("Type", listOf("1" to "Automatic (DHCP)", "2" to "PPPoE (username & password)", "3" to "Static IP") +
                (if (!editable || origMode !in listOf("1", "2", "3")) listOf(origMode to "Other (set in web settings)") else emptyList()),
                mode, { mode = it })
            when (mode) {
                "2" -> {
                    Field("Username", user, { user = it }, error = if (user.isBlank()) "Required" else null)
                    Field("Password", pass, { pass = it }, password = true, error = if (pass.isBlank()) "Required" else null)
                    Field("Service name (optional)", service, { service = it })
                }
                "3" -> {
                    Field("IP address", ip, { ip = it }, error = ipErr(ip))
                    Field("Subnet mask", mask, { mask = it }, error = ipErr(mask))
                    Field("Gateway", gw, { gw = it }, error = ipErr(gw))
                    Field("DNS 1", dns1, { dns1 = it }, error = ipErr(dns1))
                    Field("DNS 2 (optional)", dns2, { dns2 = it }, error = ipErr(dns2, required = false))
                }
                "1" -> Note("Your broadband provider assigns everything automatically.")
                else -> Note("This connection type can only be changed in web settings.")
            }
        }
        if (editable) Section("Advanced") {
            if (mode != "3") {
                SwitchRow("Use my own DNS servers", dnsManual, { dnsManual = it })
                if (dnsManual) {
                    Field("DNS 1", dns1, { dns1 = it }, error = if (dns1 == "0.0.0.0") "Required" else ipErr(dns1))
                    Field("DNS 2 (optional)", dns2, { dns2 = it }, error = ipErr(dns2, required = false))
                }
            }
            SwitchRow("Custom MTU", mtuManual, { mtuManual = it })
            if (mtuManual) Field("MTU", mtu, { mtu = it }, number = true,
                error = mtu.toIntOrNull()?.let { if (it < 576 || it > 1500) "576–1500" else null } ?: "Enter a number")
            SwitchRow("Clone MAC address", clone, { clone = it }, sub = "Some providers only accept a registered MAC")
            if (clone) Field("MAC address", mac, { mac = it.uppercase() }, error = if (isMac(mac)) null else "Format AA:BB:CC:DD:EE:FF")
        }
        val err = when {
            !editable -> "x"
            mode == "2" && (user.isBlank() || pass.isBlank()) -> "x"
            mode == "3" && listOf(ipErr(ip), ipErr(mask), ipErr(gw), ipErr(dns1), ipErr(dns2, false)).any { it != null } -> "x"
            mode != "3" && dnsManual && (ipErr(dns1) != null || dns1 == "0.0.0.0" || ipErr(dns2, false) != null) -> "x"
            mtuManual && (mtu.toIntOrNull() ?: 0) !in 576..1500 -> "x"
            clone && !isMac(mac) -> "x"
            else -> null
        }
        Note("Saving reconnects the internet — expect a short drop.")
        SaveButton(vm, enabled = err == null) { confirm = true }
        if (confirm) ConfirmDialog("Change internet settings?",
            "If these details are wrong, the internet will stop working until you fix them here.",
            onDismiss = { confirm = false }) {
            confirm = false
            val enc: (String) -> String = { WavlinkApi.enc(it) }
            val sb = StringBuilder("&device_mode=1&mode=$mode")
            if (mode == "2") {
                sb.append("&username=${enc(user)}&password=${enc(pass)}&server=${enc(service)}")
                if (advPppoe) sb.append("&server_name=${enc(w.optString("server_name"))}&ac_name=${enc(w.optString("ac_name"))}" +
                    "&host_uniq=${w.optString("host_uniq")}&lcp_echo_interval=${w.s("lcp_echo_interval")}" +
                    "&rules=${w.optJSONArray("rules")?.toString() ?: w.opt("rules")?.toString() ?: "[]"}")
            } else if (mode == "3") {
                sb.append("&ip=$ip&mask=$mask&gw=$gw")
            }
            val useDns = mode == "3" || dnsManual
            sb.append("&apdhcp_val=${w.s("apsmartdhcp")}&dns_manual=${b(if (mode == "3") dnsManual else dnsManual)}" +
                "&dns1=${if (useDns) dns1 else ""}&dns2=${if (useDns) dns2 else ""}" +
                "&mtu_manual=${b(mtuManual)}&mtu=${if (mtuManual) mtu else "1500"}" +
                "&cloneMAC=${b(clone)}&cloneMACAddr=${if (clone) mac else "00:00:00:00:00:00"}")
            vm.applySettings("Internet settings saved. Reconnecting…", listOf(R_WAN), tolerateDisconnect = true, reloadDelayMs = 15000) {
                it.set("net", "wan_conf", sb.toString())
            }
        }
}

// ================================================================ LAN & DHCP

private val R_DHCPD = Req("net", "dhcpd")
private val LEASES = listOf("1" to "1 hour", "6" to "6 hours", "12" to "12 hours", "24" to "1 day", "72" to "3 days")

@Composable
fun LanPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "LAN & DHCP", listOf(R_DHCPD)) { get ->
        val d = get(R_DHCPD)
        var ip by remember(d) { mutableStateOf(d.optString("ip")) }
        var mask by remember(d) { mutableStateOf(d.optString("mask")) }
        var dhcp by remember(d) { mutableStateOf(d.s("enable") == "1") }
        var start by remember(d) { mutableStateOf(d.optString("start")) }
        var end by remember(d) { mutableStateOf(d.optString("end")) }
        var lease by remember(d) { mutableStateOf(d.s("leaseTime").ifEmpty { "24" }) }
        var confirm by remember { mutableStateOf(false) }

        val first = ip.split(".").firstOrNull()?.toIntOrNull() ?: 0
        val last = ip.split(".").getOrNull(3)?.toIntOrNull() ?: 0
        val ipError = ipErr(ip) ?: when {
            first !in 1..223 -> "Not a usable address"
            last == 0 || last == 255 -> "Can't end in .0 or .255"
            else -> null
        }
        val startErr = ipErr(start) ?: when {
            !sameSubnet(start, ip, mask) -> "Must be in the router's subnet"
            start == ip -> "Can't be the router's own address"
            else -> null
        }
        val endErr = ipErr(end) ?: when {
            !sameSubnet(end, ip, mask) -> "Must be in the router's subnet"
            end == ip -> "Can't be the router's own address"
            isIp(start) && ipToLong(start) > ipToLong(end) -> "Must be after the start address"
            else -> null
        }
        Section("Router address") {
            Field("Router IP", ip, { ip = it }, error = ipError)
            Field("Subnet mask", mask, { mask = it }, error = ipErr(mask))
            Note("Changing the router's address disconnects every device until it reconnects.")
        }
        Section("DHCP server") {
            SwitchRow("Hand out addresses automatically", dhcp, { dhcp = it }, sub = "Leave on unless another device does this")
            Field("First address", start, { start = it }, error = startErr, enabled = dhcp)
            Field("Last address", end, { end = it }, error = endErr, enabled = dhcp)
            Dropdown("Lease time", LEASES, lease, { lease = it }, enabled = dhcp)
        }
        val ok = ipError == null && ipErr(mask) == null && startErr == null && endErr == null
        SaveButton(vm, enabled = ok) { confirm = true }
        if (confirm) ConfirmDialog("Apply LAN settings?",
            if (ip != d.optString("ip")) "The router will move to $ip. Your devices will reconnect, and this app will follow it there."
            else "Devices may briefly reconnect.",
            onDismiss = { confirm = false }) {
            confirm = false
            val moving = ip != d.optString("ip")
            val newIp = ip
            vm.applySettings("LAN settings saved.", if (moving) emptyList() else listOf(R_DHCPD), tolerateDisconnect = true,
                then = { if (moving) vm.followRouterTo(newIp) }) {
                it.set("net", "dhcpd", "&enable=${b(dhcp)}&ip=$ip&mask=$mask&start=$start&end=$end&leaseTime=$lease")
            }
        }
    }
}

// ================================================================ Address reservation

private val R_BIND = Req("net", "ipmac_bind")
private val R_ARP = Req("net", "ipmac_arplist")

@Composable
fun ReservationsPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "Address reservation", listOf(R_BIND, R_DHCPD), optional = listOf(R_ARP)) { get ->
        val hosts = get(R_BIND).optJSONArray("hosts")?.objects() ?: emptyList()
        val lan = get(R_DHCPD)
        val arp = vm.data(R_ARP)?.optJSONArray("arp_list")?.objects() ?: emptyList()
        val names = vm.devices?.let { (it.online + it.offline).associate { d -> d.mac.uppercase() to d.name } } ?: emptyMap()
        var adding by remember { mutableStateOf(false) }

        Section("Reserved (${hosts.size}/10)") {
            if (hosts.isEmpty()) Note("No reservations yet.")
            hosts.forEachIndexed { i, h ->
                val mac = h.optString("mac").uppercase()
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(names[mac] ?: mac) },
                    supportingContent = { Text("${h.optString("ip")} · $mac") },
                    trailingContent = {
                        TextButton(onClick = {
                            vm.applySettings("Reservation removed.", listOf(R_BIND)) {
                                it.set("net", "ipmac_bind", "&act=del&index=$i&ip=${h.optString("ip")}&mac=$mac")
                            }
                        }) { Text("Remove") }
                    },
                )
            }
            if (hosts.size < 10) OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("Add reservation") }
        }

        if (adding) {
            var ip by remember { mutableStateOf("") }
            var mac by remember { mutableStateOf("") }
            val lanIp = lan.optString("ip"); val mask = lan.optString("mask")
            val err = ipErr(ip) ?: (if (!sameSubnet(ip, lanIp, mask)) "Must be in ${lanIp.substringBeforeLast('.')}.x" else null)
                ?: (if (hosts.any { it.optString("ip") == ip }) "Already reserved" else null)
            val macErr = if (!isMac(mac)) "Format AA:BB:CC:DD:EE:FF"
                else if (hosts.any { it.optString("mac").equals(mac, true) }) "Already reserved" else null
            AlertDialog(
                onDismissRequest = { adding = false },
                title = { Text("Add reservation") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (arp.isNotEmpty()) Dropdown("Pick a device", listOf("" to "Choose…") + arp.map {
                            val m = it.optString("mac").uppercase()
                            "${it.optString("ip")}|$m" to "${names[m] ?: m} (${it.optString("ip")})"
                        }, "", { v -> if (v.isNotEmpty()) { ip = v.substringBefore('|'); mac = v.substringAfter('|') } })
                        Field("IP address", ip, { ip = it }, error = err)
                        Field("MAC address", mac, { mac = it.uppercase() }, error = macErr)
                    }
                },
                confirmButton = {
                    TextButton(enabled = err == null && macErr == null, onClick = {
                        adding = false
                        vm.applySettings("Reservation added.", listOf(R_BIND)) {
                            it.set("net", "ipmac_bind", "&act=add&index=${hosts.size}&ip=$ip&mac=$mac")
                        }
                    }) { Text("Add") }
                },
                dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
            )
        }
    }
}

// ================================================================ IPv6

private val R_IPV6 = Req("net", "ipv6")

@Composable
fun Ipv6Page(vm: AppViewModel) {
    SettingsPageScaffold(vm, "IPv6", listOf(R_IPV6)) { get ->
        val d = get(R_IPV6)
        var enabled by remember(d) { mutableStateOf(d.s("enable") == "1") }
        var wanM by remember(d) { mutableStateOf(d.s("wanObtainMethod").ifEmpty { "0" }) }
        var addr by remember(d) { mutableStateOf(d.optString("ipv6Addr")) }
        var plen by remember(d) { mutableStateOf(d.s("prefixLength")) }
        var gw by remember(d) { mutableStateOf(d.optString("ipv6Gateway")) }
        var dns1 by remember(d) { mutableStateOf(d.optString("fstDNS")) }
        var dns2 by remember(d) { mutableStateOf(d.optString("secDNS")) }
        var lanM by remember(d) { mutableStateOf(d.s("lanObtainMethod").ifEmpty { "0" }) }
        val lanPrefix = d.optString("ipv6AddrPrefix")

        Section {
            SwitchRow("IPv6", enabled, { enabled = it }, sub = "Only turn on if your provider supports IPv6")
        }
        if (enabled) {
            Section("Internet") {
                Dropdown("Connection", listOf("0" to "DHCPv6", "1" to "PPPoEv6", "2" to "Static IPv6"), wanM, { wanM = it })
                if (wanM == "2") {
                    Field("IPv6 address", addr, { addr = it })
                    Field("Prefix length", plen, { plen = it }, number = true,
                        error = plen.toIntOrNull()?.let { if (it in 1..128) null else "1–128" } ?: "Required")
                    Field("Gateway", gw, { gw = it })
                    Field("DNS 1", dns1, { dns1 = it })
                    Field("DNS 2", dns2, { dns2 = it })
                }
            }
            Section("Home network") {
                Dropdown("Address assignment", listOf("0" to "Automatic", "1" to "SLAAC"), lanM, { lanM = it })
                if (lanPrefix.isNotEmpty()) Note("LAN prefix: $lanPrefix")
            }
        }
        val bad = enabled && wanM == "2" && (addr.isBlank() || gw.isBlank() || dns1.isBlank() ||
            (plen.toIntOrNull() ?: 0) !in 1..128)
        SaveButton(vm, enabled = !bad) {
            val pre = lanPrefix.substringBefore('/')
            val len = lanPrefix.substringAfter('/', "64")
            vm.applySettings("IPv6 settings saved.", listOf(R_IPV6), tolerateDisconnect = true, reloadDelayMs = 8000) {
                it.set("net", "ipv6", "&enable=${b(enabled)}&wanObtainMethod=$wanM&ipv6Addr=$addr&prefixLength=$plen" +
                    "&ipv6Gateway=$gw&fstDNS=$dns1&secDNS=$dns2&lanObtainMethod=$lanM" +
                    "&lanIPv6AddrPrefix=$pre&lanPrefixLength=$len")
            }
        }
    }
}

// ================================================================ Secure DNS

private val R_DNS = Req("net", "dns_server")
private val DOH = listOf(
    Triple("0", "Cloudflare", "https://cloudflare-dns.com/dns-query"),
    Triple("1", "CleanBrowsing (family)", "https://doh.cleanbrowsing.org/doh/family-filter/"),
    Triple("2", "AdGuard", "https://dns.adguard.com/dns-query"),
    Triple("3", "OpenDNS", "https://doh.opendns.com/dns-query"),
)
private val DOT = listOf(
    Triple("0", "Cloudflare", "1dot1dot1dot1.cloudflare-dns.com" to "1.0.0.1"),
    Triple("1", "CleanBrowsing", "dns.cleanbrowsing.org" to "185.228.169.9"),
    Triple("2", "AdGuard (family)", "dns-family.adguard.com" to "94.140.15.16"),
)

@Composable
fun SecureDnsPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "Secure DNS", listOf(R_DNS)) { get ->
        val d = get(R_DNS)
        var enabled by remember(d) { mutableStateOf(d.s("enable") == "1") }
        var proto by remember(d) { mutableStateOf(d.s("protocol").ifEmpty { "0" }) }
        var sid by remember(d) { mutableStateOf(d.s("server_id").ifEmpty { "0" }) }
        var url by remember(d) { mutableStateOf(d.optString("server_url")) }
        var sip by remember(d) { mutableStateOf(d.optString("server_ip")) }
        val customId = if (proto == "0") "4" else "3"

        Section {
            SwitchRow("Encrypted DNS", enabled, { enabled = it }, sub = "Stops your provider seeing which sites you look up")
            if (enabled) Note("Status: " + if (d.truthy("status")) "connected" else "not connected")
        }
        if (enabled) Section("Provider") {
            Dropdown("Protocol", listOf("0" to "DNS over HTTPS", "1" to "DNS over TLS"), proto, { proto = it; sid = "0" })
            if (proto == "0") {
                Dropdown("Server", DOH.map { it.first to it.second } + ("4" to "Custom"), sid, { sid = it })
                if (sid == "4") Field("DoH URL", url, { url = it }, hint = "https://…/dns-query")
            } else {
                Dropdown("Server", DOT.map { it.first to it.second } + ("3" to "Custom"), sid, { sid = it })
                if (sid == "3") {
                    Field("Server IP", sip, { sip = it }, error = ipErr(sip))
                    Field("Server name", url, { url = it })
                }
            }
        }
        val bad = enabled && sid == customId && (url.isBlank() || (proto == "1" && !isIp(sip)))
        SaveButton(vm, enabled = !bad) {
            val (outUrl, outIp) = when {
                !enabled -> "" to ""
                proto == "0" && sid != "4" -> (DOH.firstOrNull { it.first == sid }?.third ?: "") to ""
                proto == "0" -> url.trim() to ""
                sid != "3" -> DOT.firstOrNull { it.first == sid }?.third ?: ("" to "")
                else -> url.trim() to sip.trim()
            }
            vm.applySettings("DNS settings saved.", listOf(R_DNS), reloadDelayMs = 3000) {
                it.set("net", "dns_server", "&enable=${b(enabled)}&protocol=$proto&server_id=${if (enabled) sid else "0"}" +
                    "&server_ip=$outIp&server_url=$outUrl")
            }
        }
    }
}

// ================================================================ QoS

private val R_SQM = Req("net", "sqmqos")

@Composable
fun QosPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "Smart QoS (SQM)", listOf(R_SQM)) { get ->
        val d = get(R_SQM)
        var enabled by remember(d) { mutableStateOf(d.s("enable") == "1") }
        var up by remember(d) { mutableStateOf(d.s("up")) }
        var down by remember(d) { mutableStateOf(d.s("down")) }
        fun err(s: String) = s.toIntOrNull()?.let { if (it in 1..65535) null else "1–65535" } ?: "Enter a number"
        Section {
            SwitchRow("Smart QoS", enabled, { enabled = it },
                sub = "Keeps games and calls smooth when the line is busy. Set speeds a little below your real speed test.")
            if (enabled) {
                Field("Download (Mbps)", down, { down = it }, number = true, error = err(down))
                Field("Upload (Mbps)", up, { up = it }, number = true, error = err(up))
            }
        }
        SaveButton(vm, enabled = !enabled || (err(up) == null && err(down) == null)) {
            vm.applySettings("QoS saved.", listOf(R_SQM)) {
                it.set("net", "sqmqos", "&enable=${b(enabled)}&up=$up&down=$down")
            }
        }
    }
}

// ================================================================ DDNS

private val R_DDNS = Req("net", "ddns")

@Composable
fun DdnsPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "Dynamic DNS", listOf(R_DDNS)) { get ->
        val d = get(R_DDNS)
        var enabled by remember(d) { mutableStateOf(d.s("enable") == "1") }
        var server by remember(d) { mutableStateOf(d.optString("server").ifEmpty { "no-ip.com" }) }
        var user by remember(d) { mutableStateOf(d.optString("userid")) }
        var pwd by remember(d) { mutableStateOf(d.optString("pwd")) }
        var domain by remember(d) { mutableStateOf(d.optString("domain")) }
        val status = when (d.optString("status")) {
            "ok" -> "Online"; "wait" -> "Connecting"; "no" -> "Not connected"
            "error_addr" -> "Domain name error"; "error_info" -> "Wrong username or password"; else -> "Offline"
        }
        Section {
            SwitchRow("Dynamic DNS", enabled, { enabled = it })
            if (enabled) Note("Status: $status")
        }
        if (enabled) Section("Account") {
            Dropdown("Provider", listOf("no-ip.com" to "No-IP", "oray.com" to "Oray"), server, { server = it })
            Field("Username", user, { user = it }, error = if (user.isBlank()) "Required" else if (!plainAscii(user)) "Plain characters only" else null)
            Field("Password", pwd, { pwd = it }, password = true, error = if (pwd.isBlank()) "Required" else if (!plainAscii(pwd)) "Plain characters only" else null)
            Field("Domain", domain, { domain = it }, error = if (domain.isBlank()) "Required" else null, hint = "myhome.ddns.net")
        }
        val bad = enabled && (user.isBlank() || pwd.isBlank() || domain.isBlank() || !plainAscii(user) || !plainAscii(pwd))
        SaveButton(vm, enabled = !bad) {
            vm.applySettings("Dynamic DNS saved.", listOf(R_DDNS), reloadDelayMs = 8000) {
                it.set("net", "ddns", "&enable=${b(enabled)}&server=$server&userid=${WavlinkApi.enc(user)}" +
                    "&pwd=${WavlinkApi.enc(pwd)}&domain=${WavlinkApi.enc(domain)}")
            }
        }
    }
}

// ================================================================ Port forwarding

private val R_PF = Req("net", "port_forward")

@Composable
fun PortForwardPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "Port forwarding", listOf(R_PF, R_DHCPD)) { get ->
        val rules = get(R_PF).optJSONArray("rules")?.objects() ?: emptyList()
        val lan = get(R_DHCPD)
        var editing by remember { mutableStateOf<Int?>(null) }   // index; == size for a new rule

        Section("Rules (${rules.size}/20)") {
            if (rules.isEmpty()) Note("No ports are forwarded.")
            rules.forEachIndexed { i, r ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("${r.optString("out_port")} → ${r.optString("ip")}:${r.optString("in_port")}") },
                    supportingContent = { Text(r.optString("proto").uppercase().replace(" ", " & ")) },
                    trailingContent = {
                        androidx.compose.foundation.layout.Row {
                            TextButton(onClick = { editing = i }) { Text("Edit") }
                            TextButton(onClick = {
                                vm.applySettings("Rule deleted.", listOf(R_PF)) {
                                    it.set("net", "port_forward", "&ip=${r.optString("ip")}&out_port=${r.optString("out_port")}" +
                                        "&in_port=${r.optString("in_port")}&proto=${r.optString("proto").uppercase()}&name=forward_$i&act=del")
                                }
                            }) { Text("Delete") }
                        }
                    },
                )
            }
            if (rules.size < 20) OutlinedButton(onClick = { editing = rules.size }, modifier = Modifier.fillMaxWidth()) {
                Text("Add rule")
            }
        }

        editing?.let { idx ->
            val existing: JSONObject? = rules.getOrNull(idx)
            var ip by remember(idx) { mutableStateOf(existing?.optString("ip") ?: "") }
            var outP by remember(idx) { mutableStateOf(existing?.optString("out_port") ?: "") }
            var inP by remember(idx) { mutableStateOf(existing?.optString("in_port") ?: "") }
            var proto by remember(idx) { mutableStateOf(existing?.optString("proto")?.lowercase() ?: "tcp") }
            val lanIp = lan.optString("ip"); val mask = lan.optString("mask")
            val ipE = ipErr(ip) ?: if (!sameSubnet(ip, lanIp, mask)) "Must be a device on your network" else null
            val outE = portErr(outP) ?: if (rules.withIndex().any { (j, r) -> j != idx && r.optString("out_port") == outP }) "Already used" else null
            val inE = portErr(inP)
            val devices = vm.devices?.online.orEmpty()
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text(if (existing == null) "Add rule" else "Edit rule") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (devices.isNotEmpty()) Dropdown("Device", listOf("" to "Choose…") + devices.map { it.ip to "${it.name} (${it.ip})" },
                            "", { if (it.isNotEmpty()) ip = it })
                        Field("Device IP", ip, { ip = it }, error = ipE)
                        Field("External port", outP, { outP = it }, number = true, error = outE)
                        Field("Internal port", inP, { inP = it }, number = true, error = inE)
                        Dropdown("Protocol", listOf("tcp" to "TCP", "udp" to "UDP", "tcp udp" to "TCP & UDP"), proto, { proto = it })
                    }
                },
                confirmButton = {
                    TextButton(enabled = ipE == null && outE == null && inE == null, onClick = {
                        editing = null
                        vm.applySettings("Rule saved.", listOf(R_PF)) {
                            it.set("net", "port_forward", "&ip=$ip&out_port=$outP&in_port=$inP&proto=$proto&name=forward_$idx&act=add")
                        }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
            )
        }
    }
}

// ================================================================ DMZ / UPnP / Firewall / Remote / AdGuard

private val R_DMZ = Req("net", "dmz")

@Composable
fun DmzPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "DMZ", listOf(R_DMZ, R_DHCPD)) { get ->
        val d = get(R_DMZ); val lan = get(R_DHCPD)
        var enabled by remember(d) { mutableStateOf(d.s("enable") == "1") }
        var ip by remember(d) { mutableStateOf(if (enabled) d.optString("ip") else "") }
        val start = lan.optString("start"); val end = lan.optString("end")
        val ipE = ipErr(ip) ?: if (!sameSubnet(ip, lan.optString("ip"), lan.optString("mask")) ||
            ipToLong(ip) < ipToLong(start) || ipToLong(ip) > ipToLong(end)) "Must be between $start and $end" else null
        Section {
            SwitchRow("DMZ host", enabled, { enabled = it },
                sub = "Sends all unsolicited internet traffic to one device. Only use it for a game console or similar.")
            if (enabled) {
                val devices = vm.devices?.online.orEmpty()
                if (devices.isNotEmpty()) Dropdown("Device", listOf("" to "Choose…") + devices.map { it.ip to "${it.name} (${it.ip})" },
                    "", { if (it.isNotEmpty()) ip = it })
                Field("Device IP", ip, { ip = it }, error = ipE)
            }
        }
        SaveButton(vm, enabled = !enabled || ipE == null) {
            vm.applySettings("DMZ saved.", listOf(R_DMZ)) { it.set("net", "dmz", "&enable=${b(enabled)}&ip=$ip") }
        }
    }
}

private val R_UPNP = Req("net", "upnp")

@Composable
fun UpnpPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "UPnP", listOf(R_UPNP)) { get ->
        val d = get(R_UPNP)
        var enabled by remember(d) { mutableStateOf(d.s("enable") == "1") }
        Section {
            SwitchRow("UPnP", enabled, { enabled = it }, sub = "Lets consoles and apps open the ports they need")
            SaveButton(vm) { vm.applySettings("UPnP saved.", listOf(R_UPNP)) { it.set("net", "upnp", "&enable=${b(enabled)}") } }
        }
        val routes = d.optJSONArray("route")?.objects() ?: emptyList()
        Section("Ports opened by apps (${routes.size})") {
            if (routes.isEmpty()) Note("None right now.")
            routes.forEach { r ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(r.optString("desc").ifEmpty { "Unnamed" }) },
                    supportingContent = {
                        Text("${r.optString("proto")} ${r.optString("external_port")} → ${r.optString("internal_ip")}:${r.optString("internal_port")}")
                    },
                )
            }
        }
    }
}

private val R_SEC = Req("net", "security_manage")
private val R_ALG = Req("net", "alg")
private val R_HWNAT = Req("net", "hwnat")
private val ALG_KEYS = listOf(
    "pptp_enable" to "PPTP passthrough", "l2tp_enable" to "L2TP passthrough", "ipsec_enable" to "IPsec passthrough",
    "ftp_enable" to "FTP", "tftp_enable" to "TFTP", "rtsp_enable" to "RTSP (streaming)",
    "h323_enable" to "H.323 (video calls)", "sip_enable" to "SIP (VoIP)",
)

@Composable
fun FirewallPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "Firewall, ALG & NAT", listOf(R_SEC, R_ALG), optional = listOf(R_HWNAT, R_IPV6)) { get ->
        val sec = get(R_SEC); val alg = get(R_ALG)
        var ping by remember(sec) { mutableStateOf(sec.s("prePing") == "1") }
        var scan by remember(sec) { mutableStateOf(sec.s("prePortScan") == "1") }
        var ddos by remember(sec) { mutableStateOf(sec.s("preDDoS") == "1") }
        Section("Firewall") {
            SwitchRow("Ignore pings from the internet", ping, { ping = it })
            SwitchRow("Block port scans", scan, { scan = it })
            SwitchRow("DDoS protection", ddos, { ddos = it })
            SaveButton(vm) {
                vm.applySettings("Firewall saved.", listOf(R_SEC)) {
                    it.set("net", "security_manage", "&prePing=${b(ping)}&prePortScan=${b(scan)}&preDDoS=${b(ddos)}")
                }
            }
        }
        var algs by remember(alg) { mutableStateOf(ALG_KEYS.associate { (k, _) -> k to (alg.s(k) == "1") }) }
        Section("Application gateways (ALG)") {
            ALG_KEYS.forEach { (k, label) ->
                SwitchRow(label, algs[k] == true, { v -> algs = algs + (k to v) })
            }
            SaveButton(vm) {
                vm.applySettings("ALG saved.", listOf(R_ALG)) {
                    it.set("net", "alg", ALG_KEYS.joinToString("") { (k, _) -> "&$k=${b(algs[k] == true)}" })
                }
            }
        }
        vm.data(R_HWNAT)?.let { hw ->
            var on by remember(hw) { mutableStateOf(hw.s("enable") == "1") }
            val ipv6On = vm.data(R_IPV6)?.s("enable") == "1"
            Section("Hardware NAT") {
                SwitchRow("Hardware acceleration", on, { on = it }, sub = "Faster routing on fast connections")
                if (on && ipv6On) Note("IPv6 is on — the router may not accelerate IPv6 traffic.")
                SaveButton(vm) {
                    vm.applySettings("Hardware NAT saved.", listOf(R_HWNAT), tolerateDisconnect = true, reloadDelayMs = 5000) {
                        it.set("net", "hwnat", "&enable=${b(on)}")
                    }
                }
            }
        }
    }
}

private val R_REMOTE = Req("net", "remote_manage")

@Composable
fun RemoteAccessPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "Remote management", listOf(R_REMOTE)) { get ->
        val d = get(R_REMOTE)
        var enabled by remember(d) { mutableStateOf(d.s("enable") == "1") }
        var port by remember(d) { mutableStateOf(if (enabled) d.s("port") else "8080") }
        val pE = port.toIntOrNull()?.let { if (it in 80..65535) null else "80–65535" } ?: "Enter a port"
        Section {
            SwitchRow("Allow access from the internet", enabled, { enabled = it },
                sub = "Anyone who finds your IP can try the admin password. Use a strong one.")
            if (enabled) Field("Port", port, { port = it }, number = true, error = pE)
        }
        SaveButton(vm, enabled = !enabled || pE == null) {
            vm.applySettings("Remote management saved.", listOf(R_REMOTE)) {
                it.set("net", "remote_manage", "&enable=${b(enabled)}&port=${if (enabled) port else "80"}")
            }
        }
    }
}

private val R_AGH = Req("system", "agh")
private val R_LANINFO = Req("net", "lan_info")

@Composable
fun AdGuardPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "AdGuard Home", listOf(R_AGH), optional = listOf(R_LANINFO)) { get ->
        val d = get(R_AGH)
        var on by remember(d) { mutableStateOf(d.s("enable") == "1") }
        val lanIp = vm.data(R_LANINFO)?.optString("ip").orEmpty()
        Section {
            SwitchRow("AdGuard Home", on, { on = it }, sub = "Blocks ads and trackers for every device")
            if (d.s("enable") == "1" && lanIp.isNotEmpty()) Note("Its dashboard is at http://$lanIp:${d.s("web_port")}")
            SaveButton(vm) {
                vm.applySettings("AdGuard Home ${if (on) "turned on" else "turned off"}.", listOf(R_AGH), reloadDelayMs = 5000) {
                    it.set("net", "agh", "&enable=${b(on)}")
                }
            }
        }
    }
}

// ================================================================ VPN

private val R_VPNC = Req("net", "vpn_client")

@Composable
fun VpnClientPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "VPN client", listOf(R_VPNC)) { get ->
        val d = get(R_VPNC)
        var enabled by remember(d) { mutableStateOf(d.s("vpn_client_enable") == "1") }
        var type by remember(d) { mutableStateOf(d.s("vpn_client_select").ifEmpty { "1" }) }
        var byName by remember(d) { mutableStateOf(d.s("vpn_client_server").ifEmpty { "1" }) }
        var sIp by remember(d) { mutableStateOf(d.optString("vpn_client_server_addr")) }
        var sDom by remember(d) { mutableStateOf(d.optString("vpn_client_server_domain")) }
        var user by remember(d) { mutableStateOf(d.optString("vpn_client_username")) }
        var pwd by remember(d) { mutableStateOf(d.optString("vpn_client_password")) }
        val connected = d.truthy("vpn_client_enable") && d.truthy("vpn_client_status")
        Section {
            SwitchRow("VPN client", enabled, { enabled = it }, sub = "Send all home traffic through a VPN server")
            Note("Status: " + if (connected) "connected (${d.optString("ip")})" else "not connected")
        }
        if (enabled) Section("Server") {
            Dropdown("Type", listOf("1" to "L2TP", "0" to "PPTP"), type, { type = it })
            Dropdown("Server address", listOf("1" to "Domain name", "0" to "IP address"), byName, { byName = it })
            if (byName == "1") Field("Server domain", sDom, { sDom = it }, error = if (sDom.isBlank()) "Required" else null)
            else Field("Server IP", sIp, { sIp = it }, error = ipErr(sIp))
            Field("Username", user, { user = it }, error = if (user.isBlank()) "Required" else null)
            Field("Password", pwd, { pwd = it }, password = true, error = if (pwd.isBlank()) "Required" else null)
        }
        val bad = enabled && (user.isBlank() || pwd.isBlank() || !plainAscii(user) || !plainAscii(pwd) ||
            (byName == "1" && sDom.isBlank()) || (byName == "0" && !isIp(sIp)))
        SaveButton(vm, enabled = !bad) {
            val enc: (String) -> String = { WavlinkApi.enc(it) }
            val data = if (enabled) "&vpn_client_select=$type&vpn_client_server=$byName" +
                "&vpn_client_server_domain=${enc(sDom)}&vpn_client_server_addr=${if (byName == "0") sIp else ""}" +
                "&vpn_client_username=${enc(user)}&vpn_client_password=${enc(pwd)}&vpn_client_enable=1"
            else "&vpn_client_select=&vpn_client_server=&vpn_client_server_domain=&vpn_client_server_addr=" +
                "&vpn_client_username=&vpn_client_password=&vpn_client_enable=0"
            vm.applySettings("VPN client saved.", listOf(R_VPNC), tolerateDisconnect = true, reloadDelayMs = 10000) {
                it.set("net", "vpn_client", data)
            }
        }
    }
}

private val R_ZT = Req("system", "zerotier")

@Composable
fun ZeroTierPage(vm: AppViewModel) {
    SettingsPageScaffold(vm, "ZeroTier", listOf(R_ZT)) { get ->
        val d = get(R_ZT)
        var on by remember(d) { mutableStateOf(d.s("enable") == "1") }
        var id by remember(d) { mutableStateOf(d.optString("id")) }
        val idE = if (id.isBlank()) "Required" else if (id.length > 31 || !plainAscii(id)) "Invalid network ID" else null
        Section {
            SwitchRow("ZeroTier", on, { on = it }, sub = "Reach your home network from anywhere")
            if (on) {
                Field("Network ID", id, { id = it.trim() }, error = idE)
                Note("Status: " + if (d.truthy("connect")) "online" else "offline")
            }
        }
        SaveButton(vm, enabled = !on || idE == null) {
            vm.applySettings("ZeroTier saved.", listOf(R_ZT), reloadDelayMs = 5000) {
                it.set("system", "zerotier", "&enable=${b(on)}&id=$id")
            }
        }
    }
}
