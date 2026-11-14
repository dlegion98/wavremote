package com.dayo.wavremote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PublicOff
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.NetworkWifi
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONObject

enum class SettingsPage(val group: String, val title: String, val subtitle: String, val icon: ImageVector) {
    SPEED_TEST("Tools", "Speed test", "How fast this phone's internet is right now", Icons.Rounded.Bolt),
    SIGNAL("Tools", "Wi-Fi signal meter", "Find weak spots around your home", Icons.Rounded.NetworkWifi),
    SHARE_WIFI("Tools", "Share Wi-Fi", "QR code for guests to join in one tap", Icons.Rounded.QrCode2),
    WAKE("Tools", "Wake a computer", "Turn on a sleeping PC with Wake-on-LAN", Icons.Rounded.PowerSettingsNew),
    ALERTS("Tools", "New-device alerts", "Get notified when something new joins", Icons.Rounded.NotificationsActive),
    WIFI_ADVANCED("Wi-Fi", "Wi-Fi radios & schedule", "Bands on/off, hidden, security, channels, bandwidth, off-times", Icons.Rounded.SettingsInputAntenna),
    GUEST("Wi-Fi", "Guest network", "A separate Wi-Fi for visitors", Icons.Rounded.People),
    KIDS("Wi-Fi", "Kids' Wi-Fi", "Separate network with blocked times", Icons.Rounded.ChildCare),
    MESH("Wi-Fi", "Mesh", "Satellites, names, band steering, roaming", Icons.Rounded.Hub),
    INTERNET("Network", "Internet connection", "DHCP, PPPoE or static IP, DNS, MTU, MAC clone", Icons.Rounded.Language),
    LAN("Network", "LAN & DHCP", "Router address and the addresses it hands out", Icons.Rounded.Lan),
    RESERVATIONS("Network", "Address reservation", "Always give a device the same IP", Icons.Rounded.PushPin),
    IPV6("Network", "IPv6", "IPv6 internet and LAN settings", Icons.Rounded.Numbers),
    SECURE_DNS("Network", "Secure DNS", "DNS over HTTPS / TLS", Icons.Rounded.VerifiedUser),
    QOS("Network", "Smart QoS (SQM)", "Keep latency low when the line is busy", Icons.Rounded.Speed),
    DDNS("Network", "Dynamic DNS", "A fixed name for your home IP", Icons.Rounded.Dns),
    PORT_FORWARD("Ports & security", "Port forwarding", "Open ports to a device", Icons.Rounded.CallSplit),
    DMZ("Ports & security", "DMZ", "Expose one device to the internet", Icons.Rounded.Warning),
    UPNP("Ports & security", "UPnP", "Let apps open ports automatically", Icons.Rounded.SettingsEthernet),
    FIREWALL("Ports & security", "Firewall, ALG & NAT", "Ping/scan/DDoS protection, VPN passthrough, hardware NAT", Icons.Rounded.Security),
    REMOTE("Ports & security", "Remote management", "Reach the router's web page from the internet", Icons.Rounded.PublicOff),
    ADGUARD("Ports & security", "AdGuard Home", "Network-wide ad blocking", Icons.Rounded.Block),
    VPN_CLIENT("VPN", "VPN client", "Connect the router to a PPTP/L2TP server", Icons.Rounded.VpnKey),
    ZEROTIER("VPN", "ZeroTier", "Join a ZeroTier network", Icons.Rounded.Hub),
    TIME("System", "Time zone", "Router clock and daylight saving", Icons.Rounded.Schedule),
    LED("System", "Lights", "Status LED on or off", Icons.Rounded.Lightbulb),
    AUTO_REBOOT("System", "Scheduled restart", "Restart automatically on chosen days", Icons.Rounded.RestartAlt),
    ADMIN_PASSWORD("System", "Admin password", "Change the router's login password", Icons.Rounded.Password),
    FIRMWARE("System", "Firmware update", "Check and install updates for every node", Icons.Rounded.SystemUpdate),
    DIAGNOSTICS("System", "Network check", "Test cable, internet, Wi-Fi and router load", Icons.Rounded.MonitorHeart),
    PING("System", "Ping & traceroute", "Test reaching a website or IP", Icons.Rounded.NetworkPing),
    LOG("System", "System log", "The router's event log", Icons.Rounded.ReceiptLong),
    RESET("System", "Factory reset", "Erase all settings", Icons.Rounded.DeleteForever),
}

// ================================================================ settings list

data class SettingsCategory(val name: String, val blurb: String, val icon: ImageVector)

val SETTINGS_CATEGORIES = listOf(
    SettingsCategory("Tools", "Speed test, signal, sharing", Icons.Rounded.Bolt),
    SettingsCategory("Wi-Fi", "Radios, guests, kids, mesh", Icons.Rounded.SettingsInputAntenna),
    SettingsCategory("Network", "Internet, LAN, DNS, QoS", Icons.Rounded.Language),
    SettingsCategory("Ports & security", "Forwarding, firewall, remote", Icons.Rounded.Security),
    SettingsCategory("VPN", "VPN client, ZeroTier", Icons.Rounded.VpnKey),
    SettingsCategory("System", "Time, firmware, diagnostics", Icons.Rounded.Memory),
)

/** Two-column grid of settings categories. */
@Composable
fun CategoryGrid(vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SETTINGS_CATEGORIES.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { c ->
                    val count = SettingsPage.entries.count { it.group == c.name }
                    Column(
                        Modifier.weight(1f).card().clickable { vm.openSettingsGroup(c.name) }.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconBadge(c.icon, size = 40.dp)
                        Column {
                            Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${c.blurb}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("$count settings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SettingsListScreen(vm: AppViewModel) {
    val group = vm.settingsGroup
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
            BackButton { vm.closeSettings() }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (group == null) {
                item { ScreenTitle("Settings", "Everything your router can do, without the web page") }
                item { CategoryGrid(vm) }
                item {
                    GroupLabel("Only in web settings")
                    Group {
                        GroupRow(
                            "Open web settings",
                            "AI parental controls and website blocking, AI QoE, OpenVPN, WireGuard, USB, IPTV/VLAN, " +
                                "operating mode, backup and restore, Wake-on-LAN schedules, TR-069, manual firmware upload",
                            icon = Icons.Rounded.OpenInBrowser, iconTint = MaterialTheme.colorScheme.secondary,
                            onClick = vm::openWebSettings,
                        )
                    }
                }
            } else {
                val cat = SETTINGS_CATEGORIES.firstOrNull { it.name == group }
                item { ScreenTitle(group, cat?.blurb?.replaceFirstChar { it.uppercase() }) }
                item {
                    Group {
                        SettingsPage.entries.filter { it.group == group }.forEachIndexed { i, p ->
                            if (i > 0) GroupDivider()
                            GroupRow(
                                p.title, p.subtitle, icon = p.icon,
                                iconTint = if (p == SettingsPage.RESET) Wav.colors.fault else MaterialTheme.colorScheme.primary,
                                onClick = { vm.openSettings(p, fromList = true) },
                                trailing = { Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsRouter(vm: AppViewModel, page: SettingsPage) {
    when (page) {
        SettingsPage.SPEED_TEST -> SpeedTestPage(vm)
        SettingsPage.SIGNAL -> SignalPage(vm)
        SettingsPage.SHARE_WIFI -> ShareWifiPage(vm)
        SettingsPage.WAKE -> WakePage(vm)
        SettingsPage.ALERTS -> AlertsPage(vm)
        SettingsPage.WIFI_ADVANCED -> WifiAdvancedPage(vm)
        SettingsPage.GUEST -> GuestWifiPage(vm)
        SettingsPage.KIDS -> KidsWifiPage(vm)
        SettingsPage.MESH -> MeshPage(vm)
        SettingsPage.INTERNET -> InternetPage(vm)
        SettingsPage.LAN -> LanPage(vm)
        SettingsPage.RESERVATIONS -> ReservationsPage(vm)
        SettingsPage.IPV6 -> Ipv6Page(vm)
        SettingsPage.SECURE_DNS -> SecureDnsPage(vm)
        SettingsPage.QOS -> QosPage(vm)
        SettingsPage.DDNS -> DdnsPage(vm)
        SettingsPage.PORT_FORWARD -> PortForwardPage(vm)
        SettingsPage.DMZ -> DmzPage(vm)
        SettingsPage.UPNP -> UpnpPage(vm)
        SettingsPage.FIREWALL -> FirewallPage(vm)
        SettingsPage.REMOTE -> RemoteAccessPage(vm)
        SettingsPage.ADGUARD -> AdGuardPage(vm)
        SettingsPage.VPN_CLIENT -> VpnClientPage(vm)
        SettingsPage.ZEROTIER -> ZeroTierPage(vm)
        SettingsPage.TIME -> TimeZonePage(vm)
        SettingsPage.LED -> LedPage(vm)
        SettingsPage.AUTO_REBOOT -> AutoRebootPage(vm)
        SettingsPage.ADMIN_PASSWORD -> AdminPasswordPage(vm)
        SettingsPage.FIRMWARE -> FirmwarePage(vm)
        SettingsPage.DIAGNOSTICS -> DiagnosticsPage(vm)
        SettingsPage.PING -> PingPage(vm)
        SettingsPage.LOG -> SystemLogPage(vm)
        SettingsPage.RESET -> FactoryResetPage(vm)
    }
}

// ================================================================ page scaffold

/**
 * Loads [reqs] (+ [optional]) and shows [content] once every required read has arrived.
 * [content] receives a getter for the loaded JSON.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPageScaffold(
    vm: AppViewModel,
    title: String,
    reqs: List<Req>,
    optional: List<Req> = emptyList(),
    content: @Composable ColumnScope.(get: (Req) -> JSONObject) -> Unit,
) {
    LaunchedEffect(title) { if (reqs.isNotEmpty() || optional.isNotEmpty()) vm.loadPage(reqs, optional) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { BackButton { vm.closeSettings() } },
                actions = {
                    if (reqs.isNotEmpty()) IconButton(onClick = { vm.loadPage(reqs, optional) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.busy || vm.pageLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            val ready = reqs.all { vm.data(it) != null }
            when {
                ready -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val page = SettingsPage.entries.firstOrNull { it.title == title }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (page != null) {
                            IconBadge(page.icon, if (page == SettingsPage.RESET) Wav.colors.fault else MaterialTheme.colorScheme.primary, size = 48.dp)
                            Spacer(Modifier.width(14.dp))
                        }
                        ScreenTitle(title, page?.subtitle, Modifier.weight(1f))
                    }
                    content { vm.data(it)!! }
                    Spacer(Modifier.height(24.dp))
                }
                vm.pageError != null -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(vm.pageError ?: "", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { vm.loadPage(reqs, optional) }) { Text("Try again") }
                    OutlinedButton(onClick = vm::openWebSettings) { Text("Open web settings") }
                }
                else -> Loading()
            }
        }
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        @Suppress("DEPRECATION")
        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
    }
}

// ================================================================ form pieces

@Composable
fun Section(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (title != null) GroupLabel(title)
        Group {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
fun Note(text: String, error: Boolean = false) {
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, sub: String? = null, enabled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Note(sub)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    error: String? = null,
    number: Boolean = false,
    password: Boolean = false,
    enabled: Boolean = true,
    hint: String? = null,
) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (hint != null) { { Text(hint) } } else null,
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                password -> KeyboardType.Password
                number -> KeyboardType.Number
                else -> KeyboardType.Text
            },
        ),
        trailingIcon = if (password) { { androidx.compose.material3.TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") } } } else null,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun <T> Dropdown(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    val text = options.firstOrNull { it.first == selected }?.second ?: selected.toString()
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (enabled) 1f else 0.5f))
                .clickable(enabled = enabled) { open = true }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text, style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, l) ->
                DropdownMenuItem(text = { Text(l) }, onClick = { onSelect(v); open = false })
            }
        }
    }
}

@Composable
fun TimeRow(label: String, h: Int, m: Int, onChange: (Int, Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Dropdown("$label hour", (0..23).map { it to "%02d".format(it) }, h, { onChange(it, m) })
        }
        Box(Modifier.weight(1f)) {
            Dropdown("Minute", (0..59).map { it to "%02d".format(it) }, m, { onChange(h, it) })
        }
    }
}

private val DAY_LABELS = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaysPicker(days: List<Boolean>, onChange: (List<Boolean>) -> Unit, labels: List<String> = DAY_LABELS) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { i, l ->
            FilterChip(
                selected = days.getOrElse(i) { false },
                onClick = { onChange(days.toMutableList().also { it[i] = !it[i] }) },
                label = { Text(l) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The router's three schedule slots. [what] describes what happens during a slot. */
@Composable
fun ScheduleEditor(rules: List<ScheduleRule>, onChange: (List<ScheduleRule>) -> Unit, what: String) {
    rules.forEachIndexed { i, r ->
        fun upd(n: ScheduleRule) = onChange(rules.toMutableList().also { it[i] = n })
        SwitchRow("Schedule ${i + 1}", r.enabled, { upd(r.copy(enabled = it)) }, sub = what)
        if (r.enabled) {
            DaysPicker(r.days, { upd(r.copy(days = it)) })
            TimeRow("From", r.sh, r.sm) { h, m -> upd(r.copy(sh = h, sm = m)) }
            TimeRow("Until", r.eh, r.em) { h, m -> upd(r.copy(eh = h, em = m)) }
            r.error?.let { Note(it, error = true) }
        }
    }
}

@Composable
fun SaveButton(vm: AppViewModel, enabled: Boolean = true, label: String = "Save", onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !vm.busy, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().height(54.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

// ================================================================ validation helpers

private val IPV4 = Regex("^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$")
private val MAC = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")

fun isIp(s: String) = IPV4.matches(s.trim())
fun isMac(s: String) = MAC.matches(s.trim())
fun ipErr(s: String, required: Boolean = true) =
    if (s.isBlank()) (if (required) "Required" else null) else if (!isIp(s)) "Not a valid IP address" else null
fun portErr(s: String, min: Int = 1) =
    s.toIntOrNull()?.let { if (it < min || it > 65535) "Use $min–65535" else null } ?: "Enter a port number"
/** The web UI's checkSpecialCharacter: printable ASCII only. */
fun plainAscii(s: String) = s.all { it.code in 32..126 }
fun wifiPassErr(p: String) = when {
    p.length < 8 -> "At least 8 characters"
    p.length > 63 -> "At most 63 characters"
    !plainAscii(p) -> "Use plain letters, numbers and symbols"
    else -> null
}
fun ssidErr(s: String) = when {
    s.isBlank() -> "Enter a name"
    s.toByteArray(Charsets.UTF_8).size > 31 -> "Too long (max 31 bytes)"
    s.any { it.code < 32 || it.code == 127 } -> "Contains characters the router doesn't allow"
    else -> null
}

fun ipToLong(ip: String): Long = ip.trim().split(".").fold(0L) { acc, p -> (acc shl 8) or (p.toLongOrNull() ?: 0L) }
fun sameSubnet(a: String, b: String, mask: String) =
    isIp(a) && isIp(b) && isIp(mask) && (ipToLong(a) and ipToLong(mask)) == (ipToLong(b) and ipToLong(mask))
