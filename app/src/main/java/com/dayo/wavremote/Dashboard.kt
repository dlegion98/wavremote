package com.dayo.wavremote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NetworkWifi
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// ================================================================ dashboard building blocks

/** Card surface: tonal fill, plus a hairline outline in dark mode. */
@Composable
fun Modifier.card(color: Color = MaterialTheme.colorScheme.surfaceContainer): Modifier {
    val shape = MaterialTheme.shapes.large
    val base = this.clip(shape).background(color)
    return if (Wav.colors.outlined) base.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape) else base
}

/** A dashboard tile: rounded surface, small title, any content. */
@Composable
fun DashTile(title: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .card()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/** Circular score gauge (Experience). */
@Composable
fun RingGauge(score: Int, color: Color, size: Dp = 76.dp) {
    val track = MaterialTheme.colorScheme.outlineVariant
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * score / 100f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("$score", style = MaterialTheme.typography.headlineMedium)
    }
}

/** Proportional bar of coloured segments (e.g. 5 GHz / 2.4 GHz / wired). */
@Composable
fun SplitBar(parts: List<Pair<Int, Color>>, modifier: Modifier = Modifier) {
    val total = parts.sumOf { it.first }.coerceAtLeast(1)
    val empty = MaterialTheme.colorScheme.outlineVariant
    Row(modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(empty), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        parts.filter { it.first > 0 }.forEach { (n, c) ->
            Box(Modifier.weight(n.toFloat() / total).fillMaxSize().background(c))
        }
    }
}

@Composable
fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(color, 8.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Download (filled area) and upload (line) over time. */
@Composable
fun TrafficChart(samples: List<TrafficSample>, modifier: Modifier = Modifier) {
    val down = Wav.colors.signal
    val up = Wav.colors.activity
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(96.dp)) {
        listOf(0.25f, 0.5f, 0.75f).forEach { f ->
            drawLine(grid.copy(alpha = 0.6f), Offset(0f, size.height * f), Offset(size.width, size.height * f), 1f)
        }
        if (samples.size < 2) return@Canvas
        val max = samples.maxOf { maxOf(it.downKBs, it.upKBs) }.coerceAtLeast(64).toFloat()
        val step = size.width / (60 - 1)
        fun x(i: Int) = size.width - (samples.size - 1 - i) * step
        fun y(v: Long) = size.height * (1f - (v / max) * 0.92f)
        val area = Path().apply {
            moveTo(x(0), size.height)
            samples.forEachIndexed { i, s -> lineTo(x(i), y(s.downKBs)) }
            lineTo(x(samples.lastIndex), size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(down.copy(alpha = 0.35f), down.copy(alpha = 0.02f))))
        val dLine = Path().apply { samples.forEachIndexed { i, s -> if (i == 0) moveTo(x(i), y(s.downKBs)) else lineTo(x(i), y(s.downKBs)) } }
        drawPath(dLine, down, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        val uLine = Path().apply { samples.forEachIndexed { i, s -> if (i == 0) moveTo(x(i), y(s.upKBs)) else lineTo(x(i), y(s.upKBs)) } }
        drawPath(uLine, up, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Big round one-tap toggle (guest Wi-Fi, kids' Wi-Fi, lights). */
@Composable
fun ToggleTile(
    label: String,
    sub: String,
    icon: ImageVector,
    on: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit,
) {
    val bg = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer
    val fg = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier
            .card(bg)
            .clickable(enabled = enabled) { onToggle(!on) }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .background(if (on) fg.copy(alpha = 0.18f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (on) fg else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Small square action button for the tools row. */
@Composable
fun ActionTile(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .card()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconBadge(icon, size = 40.dp)
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
    }
}

// ================================================================ topology

@Composable
private fun TopoNode(
    icon: ImageVector,
    title: String,
    sub: String,
    color: Color,
    filled: Boolean,
    size: Dp,
    onClick: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(max = 120.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(4.dp),
    ) {
        val bg = MaterialTheme.colorScheme.surfaceContainer
        Box(
            Modifier.size(size).clip(CircleShape)
                .background(if (filled) color else bg)
                .border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (filled) Color.White else color, modifier = Modifier.size(size * 0.48f))
        }
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun VLink(color: Color, height: Dp, dashed: Boolean = false) {
    Canvas(Modifier.width(2.dp).height(height)) {
        if (dashed) {
            var y = 0f
            val seg = 4.dp.toPx()
            while (y < size.height) { drawLine(color, Offset(1f, y), Offset(1f, minOf(y + seg, size.height)), 2.dp.toPx()); y += seg * 2 }
        } else drawLine(color, Offset(1f, 0f), Offset(1f, size.height), 2.dp.toPx())
    }
}

/** Internet → main router → satellites, like the mesh maps in consumer router apps. */
@Composable
fun TopologyMap(
    o: Overview,
    nodes: List<MeshNode>?,
    clients: Int?,
    onInternet: () -> Unit,
    onRouter: () -> Unit,
    onSatellite: () -> Unit,
) {
    val wav = Wav.colors
    val up = o.internetUp
    val link = if (up) wav.signal else wav.fault
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TopoNode(Icons.Rounded.Public, "Internet", if (up) (o.wanIp ?: "Connected") else "Offline", link, filled = false, size = 48.dp, onClick = onInternet)
        VLink(link, 22.dp, dashed = !up)
        TopoNode(Icons.Rounded.Router, o.model?.let { "Main router" } ?: "Router",
            clients?.let { "$it client${if (it == 1) "" else "s"}" } ?: "Router", wav.signal, filled = true, size = 64.dp, onClick = onRouter)
        val sats = nodes.orEmpty()
        if (sats.isNotEmpty()) {
            VLink(wav.signal.copy(alpha = 0.6f), 14.dp)
            val lineColor = wav.signal.copy(alpha = 0.6f)
            Box(Modifier.fillMaxWidth()) {
                Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                    val n = sats.size
                    val xs = (0 until n).map { size.width * (it + 0.5f) / n }
                    drawLine(lineColor, Offset(xs.first(), 1f), Offset(xs.last(), 1f), 2.dp.toPx())
                    xs.forEach { x -> drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx()) }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                sats.forEach { s ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        TopoNode(Icons.Rounded.Router, s.name, meshLinkLabel(s.rssi),
                            if ((s.rssi ?: -60) < -73) wav.activity else wav.signal, filled = false, size = 48.dp, onClick = onSatellite)
                    }
                }
            }
        }
    }
}

// ================================================================ Home (dashboard)

@Composable
fun DashboardHome(vm: AppViewModel) {
    val o = vm.overview
    if (o == null) {
        Column(Modifier.padding(horizontal = 16.dp)) { TabHeader("Overview"); Loading() }
        return
    }
    val wav = Wav.colors
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        vm.loadQuick()
        if (vm.devices == null) vm.refreshDevices(quiet = true)
        if (vm.meshNodes == null) vm.refreshMesh(quiet = true)
    }
    // Keep the throughput chart moving while Overview is open.
    LaunchedEffect(Unit) { while (true) { delay(10_000); vm.refreshDevices(quiet = true) } }
    var phone by remember { mutableStateOf(PhoneWifi.link(ctx)) }
    LaunchedEffect(Unit) { while (true) { delay(5000); phone = PhoneWifi.link(ctx) } }

    val dl = vm.devices
    val clients = dl?.online?.size ?: o.onlineDevices
    val title = o.ssid2 ?: o.ssid5 ?: "Your network"

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TabHeader(
                title,
                if (o.internetUp) "Online, ${clients ?: 0} devices connected"
                else if (o.wanLink) "No internet from your provider" else "Internet cable unplugged",
                onRefresh = { vm.refreshOverview(); vm.refreshDevices(); vm.refreshMesh() },
            )
        }

        // ---- network map
        item {
            Group {
                TopologyMap(
                    o, vm.meshNodes, clients,
                    onInternet = { vm.openSettings(SettingsPage.INTERNET) },
                    onRouter = { vm.tab = Tab.DEVICES },
                    onSatellite = { vm.openSettings(SettingsPage.MESH) },
                )
            }
        }

        if (vm.update != null) item { AppUpdateCard(vm, compact = true) }

        if (o.updateAvailable) item {
            Group {
                GroupRow("Firmware update available", "Install the latest fixes and security patches.",
                    icon = Icons.Rounded.SystemUpdate, iconTint = wav.activity,
                    onClick = { vm.openSettings(SettingsPage.FIRMWARE) })
            }
        }

        // ---- stats grid
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val score = dl?.let { experienceScore(it) }
                DashTile("Wi-Fi experience", Modifier.weight(1f), onClick = { vm.tab = Tab.DEVICES }) {
                    if (score == null) Text("—", style = MaterialTheme.typography.headlineMedium)
                    else Row(verticalAlignment = Alignment.CenterVertically) {
                        val c = when { score >= 80 -> wav.signal; score >= 60 -> wav.signal.copy(alpha = 0.75f); score >= 40 -> wav.activity; else -> wav.fault }
                        RingGauge(score, c, 64.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(experienceLabel(score), style = MaterialTheme.typography.titleMedium)
                    }
                }
                DashTile("Clients", Modifier.weight(1f), onClick = { vm.tab = Tab.DEVICES }) {
                    Text("${clients ?: "—"}", style = MaterialTheme.typography.headlineLarge)
                    if (dl != null) {
                        val g5 = dl.online.count { it.band == 1 }
                        val g2 = dl.online.count { it.band == 0 }
                        val wired = dl.online.count { it.isWired }
                        val other = dl.online.size - g5 - g2 - wired
                        SplitBar(listOf(g5 to wav.signal, (g2 + other) to wav.activity, wired to MaterialTheme.colorScheme.secondary))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Legend(wav.signal, "5 GHz  $g5")
                            Legend(wav.activity, "2.4 GHz  ${g2 + other}")
                            Legend(MaterialTheme.colorScheme.secondary, "Wired  $wired")
                        }
                    }
                }
            }
        }

        item {
            val last = vm.trafficHistory.lastOrNull()
            DashTile("Throughput", Modifier.fillMaxWidth(), onClick = { vm.tab = Tab.DEVICES }) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column {
                        Text(formatSpeed(last?.downKBs ?: 0), style = MaterialTheme.typography.titleLarge, color = wav.signal)
                        Text("Download", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column {
                        Text(formatSpeed(last?.upKBs ?: 0), style = MaterialTheme.typography.titleLarge, color = wav.activity)
                        Text("Upload", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TrafficChart(vm.trafficHistory)
                Text(if (vm.trafficHistory.size < 2) "The graph fills in while this screen is open." else "Last ${vm.trafficHistory.size * 10 / 60} min",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ---- quick toggles
        item {
            val guest = vm.data(Quick.GUEST)
            val kids = vm.data(Quick.KIDS)
            val led = vm.data(Quick.LED)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val gOn = guest?.s("guest_enable") == "1"
                ToggleTile("Guest Wi-Fi", if (guest == null) "…" else if (gOn) "On" else "Off", Icons.Rounded.People, gOn,
                    enabled = guest != null && !vm.busy, modifier = Modifier.weight(1f)) {
                    if (!vm.quickGuest(it)) vm.openSettings(SettingsPage.GUEST)
                }
                val kOn = kids?.s("enable") == "1"
                ToggleTile("Kids' Wi-Fi", if (kids == null) "…" else if (kOn) "On" else "Off", Icons.Rounded.ChildCare, kOn,
                    enabled = kids != null && !vm.busy, modifier = Modifier.weight(1f)) {
                    if (!vm.quickKids(it)) vm.openSettings(SettingsPage.KIDS)
                }
                val lOn = led?.s("led") == "1"
                ToggleTile("Lights", if (led == null) "…" else if (lOn) "On" else "Off", Icons.Rounded.Lightbulb, lOn,
                    enabled = led != null && !vm.busy, modifier = Modifier.weight(1f)) { vm.quickLights(it) }
            }
        }

        // ---- top clients
        dl?.let { list ->
            val busy = list.online.filter { !it.blocked && it.downKBs + it.upKBs > 0 }.sortedByDescending { it.downKBs + it.upKBs }.take(4)
            if (busy.isNotEmpty()) item {
                GroupLabel("Top clients")
                Group {
                    busy.forEachIndexed { i, d ->
                        if (i > 0) GroupDivider()
                        GroupRow(
                            d.name, d.connection, icon = deviceIcon(d), onClick = { vm.tab = Tab.DEVICES },
                            trailing = { Throughput(d) },
                        )
                    }
                }
            }
        }

        // ---- tools
        item {
            GroupLabel("Tools")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile("Speed test", Icons.Rounded.Bolt, Modifier.weight(1f)) { vm.openSettings(SettingsPage.SPEED_TEST) }
                ActionTile("Signal", Icons.Rounded.NetworkWifi, Modifier.weight(1f)) { vm.openSettings(SettingsPage.SIGNAL) }
                ActionTile("Share Wi-Fi", Icons.Rounded.QrCode2, Modifier.weight(1f)) { vm.openSettings(SettingsPage.SHARE_WIFI) }
            }
        }

        // ---- details
        item {
            GroupLabel("Details")
            Group {
                val p = phone
                GroupRow(
                    if (p != null) "This phone: ${p.verdict.lowercase()} signal" else "This phone isn't on Wi-Fi",
                    if (p != null) "${p.rssi} dBm on ${p.band}, ${p.linkMbps} Mbps link" else "Join your Wi-Fi to see its signal",
                    icon = Icons.Rounded.Wifi, onClick = { vm.openSettings(SettingsPage.SIGNAL) },
                    trailing = { if (p != null) SignalBars(p.quality) },
                )
                GroupDivider()
                GroupRow(o.wanType, listOfNotNull(o.wanIp, o.gateway?.let { "gateway $it" }).joinToString(", ").ifEmpty { "Internet connection" },
                    icon = Icons.Rounded.Public, onClick = { vm.openSettings(SettingsPage.INTERNET) })
                GroupDivider()
                GroupRow("${o.model?.let { "Wavlink $it" } ?: "Router"}, ${o.modeLabel.lowercase()} mode",
                    listOfNotNull(o.firmware, vm.host).joinToString(", "),
                    icon = Icons.Rounded.Memory, onClick = { vm.openSettings(SettingsPage.FIRMWARE) })
                if (o.uptimeSec != null) {
                    GroupDivider()
                    GroupRow(formatDuration(o.uptimeSec), "Running since the last restart", icon = Icons.Rounded.Timer)
                }
            }
        }
    }
}

/** Two-line ↓/↑ throughput column used in client rows. */
@Composable
fun Throughput(d: Device) {
    Column(horizontalAlignment = Alignment.End) {
        Text("↓ ${formatSpeed(d.downKBs)}", style = MaterialTheme.typography.labelMedium, color = Wav.colors.signal)
        Text("↑ ${formatSpeed(d.upKBs)}", style = MaterialTheme.typography.labelMedium, color = Wav.colors.activity)
    }
}

// ================================================================ app update card

/** Shows the available update with notes and an Install button, or the current version and a check button. */
@Composable
fun AppUpdateCard(vm: AppViewModel, compact: Boolean = false) {
    val u = vm.update
    val wav = Wav.colors
    if (u == null && compact) return
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.SystemUpdate, if (u != null) wav.activity else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (u != null) "App update: ${u.name}" else "WavRemote ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleMedium)
                Text(
                    vm.updateStatus ?: if (u != null) "${"%.1f".format(u.sizeBytes / 1_048_576.0)} MB" else
                        if (Updater.enabled) "Updates come from github.com/${Updater.repo}" else "Installed from Android Studio",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (u != null && !compact && u.notes.isNotBlank()) {
            Text(u.notes.take(600), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        vm.updateProgress?.let {
            androidx.compose.material3.LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
        }
        if (u != null) {
            androidx.compose.material3.Button(
                onClick = vm::installUpdate, enabled = vm.updateProgress == null,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(if (vm.updateProgress != null) "Downloading…" else "Install update") }
        } else if (Updater.enabled) {
            androidx.compose.material3.OutlinedButton(onClick = { vm.checkForUpdate() }, modifier = Modifier.fillMaxWidth()) {
                Text("Check for updates")
            }
        }
    }
}
