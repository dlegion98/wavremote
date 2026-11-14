package com.dayo.wavremote

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Big number + unit, used by the speed test and signal meter. */
@Composable
private fun Readout(value: String, unit: String, caption: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.displayLarge, color = color)
            Spacer(Modifier.width(6.dp))
            Text(unit, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ================================================================ Speed test

private enum class Phase { IDLE, PING, DOWN, UP, DONE, ERROR }

@Composable
fun SpeedTestPage(vm: AppViewModel) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("wavremote", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var ping by remember { mutableStateOf<Int?>(null) }
    var jitter by remember { mutableStateOf<Int?>(null) }
    var down by remember { mutableStateOf<Double?>(null) }
    var up by remember { mutableStateOf<Double?>(null) }
    var live by remember { mutableStateOf(0.0) }
    var error by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(prefs.getString("speedHistory", "")!!.lines().filter { it.isNotBlank() }) }
    val link = remember { PhoneWifi.link(ctx) }

    fun run() {
        phase = Phase.PING; ping = null; jitter = null; down = null; up = null; live = 0.0; error = null
        scope.launch {
            try {
                val (p, j) = withContext(Dispatchers.IO) { SpeedTest.latency() }
                ping = p; jitter = j
                phase = Phase.DOWN
                down = withContext(Dispatchers.IO) { SpeedTest.download { m -> live = m } }
                phase = Phase.UP; live = 0.0
                up = withContext(Dispatchers.IO) { SpeedTest.upload { m -> live = m } }
                phase = Phase.DONE
                val stamp = SimpleDateFormat("d MMM HH:mm", Locale.UK).format(Date())
                val line = "$stamp|${"%.0f".format(down)}|${"%.0f".format(up)}|$p"
                history = (listOf(line) + history).take(8)
                prefs.edit().putString("speedHistory", history.joinToString("\n")).apply()
            } catch (e: Exception) {
                phase = Phase.ERROR
                error = "The test couldn't reach the internet (${e.javaClass.simpleName}). Check you're online and try again."
            }
        }
    }

    SettingsPageScaffold(vm, "Speed test", emptyList()) {
        val running = phase in listOf(Phase.PING, Phase.DOWN, Phase.UP)
        Group {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (phase) {
                    Phase.IDLE -> Readout("—", "Mbps", "Tests this phone's speed through your Wi-Fi and internet")
                    Phase.PING -> Readout("…", "ms", "Measuring response time")
                    Phase.DOWN -> Readout("%.0f".format(live), "Mbps", "Downloading", Wav.colors.signal)
                    Phase.UP -> Readout("%.0f".format(live), "Mbps", "Uploading", Wav.colors.activity)
                    Phase.DONE -> Readout("%.0f".format(down ?: 0.0), "Mbps", "Download speed", Wav.colors.signal)
                    Phase.ERROR -> Text(error ?: "", color = Wav.colors.fault)
                }
                if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniStat("Download", down?.let { "%.0f Mbps".format(it) } ?: "—", Modifier.weight(1f))
                    MiniStat("Upload", up?.let { "%.0f Mbps".format(it) } ?: "—", Modifier.weight(1f))
                    MiniStat("Ping", ping?.let { "$it ms" } ?: "—", Modifier.weight(1f))
                }
                jitter?.let { Note("Jitter $it ms. Under 30 ms is good for calls and games.") }
            }
        }
        link?.let {
            Note("This phone is on ${it.band} with a ${it.linkMbps} Mbps link to the router, so it can't measure faster than that.")
        }
        SaveButton(vm, enabled = !running, label = if (running) "Testing…" else if (phase == Phase.DONE) "Test again" else "Start test") { run() }
        if (history.isNotEmpty()) {
            GroupLabel("Recent results")
            Group {
                history.forEachIndexed { i, h ->
                    val p = h.split("|")
                    if (p.size == 4) {
                        if (i > 0) GroupDivider(16.dp)
                        GroupRow("↓ ${p[1]}  ↑ ${p[2]} Mbps", "${p[0]}, ping ${p[3]} ms")
                    }
                }
            }
        }
        Note("Uses Cloudflare's public speed test. A test uses roughly 100–300 MB of data.")
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ================================================================ Signal meter

@Composable
fun SignalPage(vm: AppViewModel) {
    val ctx = LocalContext.current
    var link by remember { mutableStateOf(PhoneWifi.link(ctx)) }
    val samples = remember { mutableStateListOf<Int>() }
    LaunchedEffect(Unit) {
        while (true) {
            link = PhoneWifi.link(ctx)
            link?.let { samples.add(it.rssi); if (samples.size > 90) samples.removeAt(0) }
            delay(1000)
        }
    }
    SettingsPageScaffold(vm, "Wi-Fi signal", emptyList()) {
        val l = link
        if (l == null) {
            Group { GroupRow("Not connected to Wi-Fi", "Join your network to measure the signal.") }
        } else {
            val color = when {
                l.rssi >= -65 -> Wav.colors.signal
                l.rssi >= -75 -> Wav.colors.activity
                else -> Wav.colors.fault
            }
            Group {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Readout("${l.rssi}", "dBm", "${l.verdict} signal on ${l.band}", color)
                    LinearProgressIndicator(progress = { l.quality / 100f }, color = color, modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.extraSmall))
                    Sparkline(samples, color)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MiniStat("Link speed", "${l.linkMbps} Mbps", Modifier.weight(1f))
                        MiniStat("Band", l.band, Modifier.weight(1f))
                        MiniStat("Channel freq.", "${l.freqMhz} MHz", Modifier.weight(1f))
                    }
                }
            }
            Note("Walk around with this screen open to find weak spots. Above −65 dBm is great; below −75 dBm, " +
                "consider moving a mesh satellite closer.")
        }
    }
}

@Composable
private fun Sparkline(values: List<Int>, color: Color) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val lo = -90f; val hi = -30f
        fun y(v: Int) = size.height * (1f - ((v - lo) / (hi - lo)).coerceIn(0f, 1f))
        listOf(-50, -65, -75).forEach { g ->
            drawLine(grid, Offset(0f, y(g)), Offset(size.width, y(g)), strokeWidth = 1f)
        }
        if (values.size > 1) {
            val step = size.width / 89f
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = size.width - (values.size - 1 - i) * step
                if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
            }
            drawPath(path, color, style = Stroke(width = 2.5.dp.toPx()))
        }
    }
}

// ================================================================ Share Wi-Fi

private data class ShareNet(val key: String, val label: String, val ssid: String, val pass: String, val open: Boolean, val hidden: Boolean)

private val R_S2 = Req("net", "wifi_ap")
private val R_S5 = Req("net", "wifi_ap_5g")
private val R_SG = Req("net", "wifi_ap_guest")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareWifiPage(vm: AppViewModel) {
    val ctx = LocalContext.current
    SettingsPageScaffold(vm, "Share Wi-Fi", listOf(R_S2, R_S5), optional = listOf(R_SG)) { get ->
        val b2 = BandEdit.from(get(R_S2), is5 = false)
        val b5 = BandEdit.from(get(R_S5), is5 = true)
        val guest = vm.data(R_SG)?.takeIf { it.s("guest_enable") == "1" }
        val nets = buildList {
            if (b2.ssid == b5.ssid) add(ShareNet("main", "Home", b2.ssid, b2.password, b2.auth == 0, b2.hidden))
            else {
                add(ShareNet("2", "2.4 GHz", b2.ssid, b2.password, b2.auth == 0, b2.hidden))
                add(ShareNet("5", "5 GHz", b5.ssid, b5.password, b5.auth == 0, b5.hidden))
            }
            if (guest != null) add(ShareNet("g", "Guest", guest.optString("SSID"), guest.optString("passwd"),
                guest.s("guest_mode") == "1", false))
        }
        var sel by remember { mutableStateOf(nets.last().key.takeIf { it == "g" } ?: nets.first().key) }
        var reveal by remember { mutableStateOf(false) }
        val n = nets.firstOrNull { it.key == sel } ?: nets.first()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            nets.forEach { x -> FilterChip(selected = x.key == n.key, onClick = { sel = x.key; reveal = false }, label = { Text(x.label) }) }
        }
        Group {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val qr = remember(n) { WifiQr.bitmap(WifiQr.payload(n.ssid, n.pass, n.open, n.hidden)) }
                Box(Modifier.fillMaxWidth(0.8f).aspectRatio(1f).clip(MaterialTheme.shapes.medium).background(Color.White).padding(12.dp)) {
                    Image(qr, contentDescription = "Wi-Fi QR code for ${n.ssid}", modifier = Modifier.fillMaxWidth())
                }
                Text(n.ssid, style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (n.open) "No password needed" else if (reveal) n.pass else "Password hidden",
                    style = MaterialTheme.typography.bodyLarge, fontWeight = if (reveal) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!n.open) TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Hide password" else "Show password") }
            }
        }
        Note("Point the other phone's camera at the code and tap the Wi-Fi prompt.")
        OutlinedButton(onClick = {
            shareText(ctx, "Wi-Fi details", if (n.open) "Wi-Fi: ${n.ssid}\n(no password)" else "Wi-Fi: ${n.ssid}\nPassword: ${n.pass}")
        }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Send name and password as a message") }
    }
}

// ================================================================ Wake-on-LAN

@Composable
fun WakePage(vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (vm.devices == null) vm.refreshDevices(quiet = true) }
    SettingsPageScaffold(vm, "Wake a computer", emptyList()) {
        var mac by remember { mutableStateOf("") }
        fun wake(m: String, name: String) {
            scope.launch {
                vm.message = try {
                    withContext(Dispatchers.IO) {
                        WakeOnLan.send(m, WifiNetwork.current(ctx)?.network, PhoneWifi.broadcast(ctx))
                    }
                    "Wake signal sent to $name."
                } catch (e: Exception) {
                    "Couldn't send the wake signal: ${e.message ?: e.javaClass.simpleName}"
                }
            }
        }
        Note("Turns on a computer that's asleep or off, if Wake-on-LAN is enabled in its settings. Works best with wired computers.")
        val list = vm.devices?.let { (it.offline + it.online).sortedBy { d -> if (d.isWired || !d.online) 0 else 1 } }.orEmpty()
            .filter { !isPrivateMac(it.mac) }
        if (list.isNotEmpty()) {
            GroupLabel("Your devices")
            Group {
                list.take(25).forEachIndexed { i, d ->
                    if (i > 0) GroupDivider()
                    GroupRow(d.name, (if (d.online) "Online" else "Offline") + ", ${d.mac.uppercase()}",
                        icon = deviceIcon(d),
                        iconTint = if (d.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        trailing = { TextButton(onClick = { wake(d.mac, d.name) }) { Text("Wake") } })
                }
            }
        }
        Section("Another device") {
            Field("MAC address", mac, { mac = it.uppercase() }, error = if (mac.isEmpty() || isMac(mac)) null else "Format AA:BB:CC:DD:EE:FF")
            SaveButton(vm, enabled = isMac(mac), label = "Send wake signal") { wake(mac, mac) }
        }
    }
}

// ================================================================ New-device alerts

@Composable
fun AlertsPage(vm: AppViewModel) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(NewDeviceWorker.isEnabled(ctx)) }
    var allowed by remember { mutableStateOf(NewDeviceWorker.canNotify(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        if (granted) { NewDeviceWorker.enable(ctx, vm.devices); on = true }
    }
    LaunchedEffect(Unit) { if (vm.devices == null) vm.refreshDevices(quiet = true) }

    SettingsPageScaffold(vm, "New-device alerts", emptyList()) {
        Group {
            GroupRow(
                "Alert me about new devices",
                "Get a notification when something joins your Wi-Fi for the first time",
                icon = Icons.Rounded.NotificationsActive,
                trailing = {
                    Switch(checked = on, onCheckedChange = { want ->
                        if (want) {
                            if (Build.VERSION.SDK_INT >= 33 && !allowed) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else { NewDeviceWorker.enable(ctx, vm.devices); on = true }
                        } else { NewDeviceWorker.disable(ctx); on = false }
                    })
                },
            )
        }
        if (on) Note("Checking about every 15 minutes while this phone is on your home Wi-Fi. " +
            "${NewDeviceWorker.knownCount(ctx)} devices are already known and won't trigger alerts.")
        if (!allowed && Build.VERSION.SDK_INT >= 33) Note("Notifications are off for WavRemote. Allow them when asked, or in Android settings.", error = true)
        Note("Each check signs in to the router, which signs out the router's web page if it's open.")
        if (on) OutlinedButton(onClick = {
            ctx.getSharedPreferences("wavremote", Context.MODE_PRIVATE).edit().remove(NewDeviceWorker.KEY_KNOWN).apply()
            NewDeviceWorker.enable(ctx, vm.devices)
            vm.message = "Known devices reset to what's on the network now."
        }, modifier = Modifier.fillMaxWidth()) { Text("Treat current devices as known") }
    }
}

