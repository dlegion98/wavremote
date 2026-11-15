package com.dayo.wavremote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tablet
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A best guess at what kind of device this is, from its name. */
fun deviceIcon(d: Device): ImageVector {
    val n = d.name.lowercase()
    return when {
        listOf("iphone").any { it in n } -> Icons.Rounded.PhoneIphone
        listOf("phone", "galaxy", "pixel", "sm-", "android", "oneplus", "xiaomi", "redmi").any { it in n } -> Icons.Rounded.PhoneAndroid
        listOf("ipad", "tab", "kindle").any { it in n } -> Icons.Rounded.Tablet
        listOf("macbook", "laptop", "notebook", "thinkpad").any { it in n } -> Icons.Rounded.Laptop
        listOf("imac", "desktop", "pc", "windows", "mac-mini", "macmini").any { it in n } -> Icons.Rounded.Computer
        listOf("tv", "roku", "firestick", "fire-tv", "chromecast", "appletv", "apple-tv", "bravia", "shield").any { it in n } -> Icons.Rounded.Tv
        listOf("xbox", "playstation", "ps4", "ps5", "switch", "nintendo", "steam").any { it in n } -> Icons.Rounded.SportsEsports
        listOf("echo", "alexa", "sonos", "homepod", "nest", "speaker").any { it in n } -> Icons.Rounded.SpeakerGroup
        listOf("watch").any { it in n } -> Icons.Rounded.Watch
        listOf("ring", "cam", "doorbell", "arlo", "eufy").any { it in n } -> Icons.Rounded.Videocam
        listOf("print", "hp", "epson", "canon", "brother").any { it in n } -> Icons.Rounded.Print
        listOf("bulb", "hue", "light", "plug", "tapo", "kasa", "shelly", "tuya").any { it in n } -> Icons.Rounded.Lightbulb
        listOf("wavlink", "router", "mesh", "zima", "server", "nas").any { it in n } -> Icons.Rounded.Router
        d.isWired -> Icons.Rounded.Computer
        else -> Icons.Rounded.Devices
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesTab(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.refreshDevices(quiet = vm.devices != null) }
    val list = vm.devices
    var selectedMac by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    val sort = vm.deviceSort
    val ctx = LocalContext.current
    // Home can ask for one device to be opened straight away.
    LaunchedEffect(vm.focusMac) {
        vm.focusMac?.let { mac ->
            selectedMac = mac
            vm.focusMac = null
        }
    }
    fun matches(d: Device) = (query.isBlank() || listOf(d.name, d.ip, d.mac).any { it.contains(query.trim(), ignoreCase = true) }) &&
        when (filter) {
            "Favourites" -> vm.isFavourite(d)
            "Wi-Fi" -> !d.isWired
            "Wired" -> d.isWired
            "Blocked" -> d.blocked
            "Limited" -> d.hasLimit
            else -> true
        }
    // Favourites always come first; the chosen order applies within each part.
    fun sorted(l: List<Device>): List<Device> = when (sort) {
        "Speed" -> l.sortedByDescending { it.downKBs + it.upKBs }
        "Signal" -> l.sortedByDescending { if (it.isWired) 101 else it.rssi }
        "Recent" -> l.sortedBy { it.onlineSec }
        else -> l.sortedBy { it.name.lowercase() }
    }.sortedBy { if (vm.isFavourite(it)) 0 else 1 }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TabHeader(
                "Devices",
                list?.let { "${it.online.size} online" + if (it.offline.isNotEmpty()) ", ${it.offline.size} seen recently" else "" },
                onRefresh = { vm.refreshDevices() },
            )
        }
        if (list != null && list.online.isNotEmpty()) item {
            val g5 = list.online.count { it.band == 1 }
            val wired = list.online.count { it.isWired }
            val g2 = list.online.size - g5 - wired
            DashTile("Connected now") {
                SplitBar(listOf(g5 to Wav.colors.down, g2 to Wav.colors.up, wired to MaterialTheme.colorScheme.secondary))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Legend(Wav.colors.down, "5 GHz  $g5")
                    Legend(Wav.colors.up, "2.4 GHz  $g2")
                    Legend(MaterialTheme.colorScheme.secondary, "Wired  $wired")
                }
            }
        }
        if (list != null) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Search by name, IP or MAC") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = if (query.isNotEmpty()) { { IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } } } else null,
                    singleLine = true, shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    listOf("All", "Favourites", "Wi-Fi", "Wired", "Blocked", "Limited").forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) })
                    }
                    Spacer(Modifier.width(4.dp))
                    var sortOpen by remember { mutableStateOf(false) }
                    Box {
                        AssistChip(onClick = { sortOpen = true }, label = { Text("Sort: $sort") },
                            leadingIcon = { Icon(Icons.Rounded.Sort, null, Modifier.size(18.dp)) })
                        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                            listOf("Name", "Speed", "Signal", "Recent").forEach { o ->
                                DropdownMenuItem(text = { Text(o) }, onClick = { vm.chooseSort(o); sortOpen = false })
                            }
                        }
                    }
                    AssistChip(onClick = { shareDeviceList(ctx, list, vm.favourites, vm.seen) }, label = { Text("Export") },
                        leadingIcon = { Icon(Icons.Rounded.IosShare, null, Modifier.size(18.dp)) })
                }
            }
        }
        if (list == null) {
            item { Loading() }
        } else {
            val online = sorted(list.online.filter(::matches))
            val offline = sorted(list.offline.filter(::matches))
            item {
                GroupLabel("Online now")
                Group {
                    if (online.isEmpty()) GroupRow(
                        when {
                            list.online.isEmpty() -> "Nothing is connected"
                            filter == "Favourites" && vm.favourites.isEmpty() -> "No favourites yet"
                            else -> "No matches"
                        },
                        when {
                            list.online.isEmpty() -> "Devices appear here when they join your Wi-Fi."
                            filter == "Favourites" && vm.favourites.isEmpty() -> "Open a device and tap the star to keep it at the top."
                            else -> "Try a different search or filter."
                        },
                    )
                    online.forEachIndexed { i, d ->
                        if (i > 0) GroupDivider()
                        DeviceRow(d, vm.isFavourite(d), vm.seen[DeviceBook.key(d.mac)]?.last) { selectedMac = d.mac }
                    }
                }
            }
            if (offline.isNotEmpty()) item {
                GroupLabel("Seen recently")
                Group {
                    offline.forEachIndexed { i, d ->
                        if (i > 0) GroupDivider()
                        DeviceRow(d, vm.isFavourite(d), vm.seen[DeviceBook.key(d.mac)]?.last) { selectedMac = d.mac }
                    }
                }
            }
        }
    }

    val selected = list?.let { (it.online + it.offline).firstOrNull { d -> d.mac == selectedMac } }
    if (selected != null && list != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedMac = null },
            containerColor = MaterialTheme.colorScheme.background,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
            tonalElevation = 0.dp,
        ) {
            DeviceSheet(vm, selected, list, onDone = { selectedMac = null })
        }
    }
}

@Composable
private fun DeviceRow(d: Device, favourite: Boolean, lastSeen: Long?, onClick: () -> Unit) {
    val wav = Wav.colors
    val muted = !d.online
    GroupRow(
        title = d.name,
        subtitle = if (d.online) "${d.connection}, ${d.ip}" else if (lastSeen != null) "Last online ${formatAgo(lastSeen)}" else "Offline",
        icon = deviceIcon(d),
        iconTint = when {
            d.blocked -> wav.fault
            muted -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.primary
        },
        onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (favourite) Icon(Icons.Rounded.Star, "Favourite", tint = wav.activity, modifier = Modifier.size(16.dp))
                when {
                    d.blocked -> Tag("Blocked", wav.fault)
                    d.hasLimit -> Tag("Limited", wav.activity)
                    d.online && (d.downKBs > 0 || d.upKBs > 0) -> Throughput(d)
                }
                when {
                    !d.online -> {}
                    d.isWired -> Icon(Icons.Rounded.Cable, "Wired", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> SignalBars(d.rssi, color = when {
                        d.rssi >= 60 -> wav.signal
                        d.rssi >= 35 -> wav.activity
                        else -> wav.fault
                    })
                }
            }
        },
    )
}

@Composable
private fun DeviceSheet(vm: AppViewModel, d: Device, list: DeviceList, onDone: () -> Unit) {
    var renaming by remember { mutableStateOf(false) }
    var limiting by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf(false) }
    val wav = Wav.colors
    val sheetCtx = LocalContext.current
    val favourite = vm.isFavourite(d)
    val history = vm.seen[DeviceBook.key(d.mac)]

    Column(
        Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconBadge(deviceIcon(d), if (d.blocked) wav.fault else MaterialTheme.colorScheme.primary, size = 52.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, style = MaterialTheme.typography.headlineSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (!d.online) MaterialTheme.colorScheme.outline else if (d.blocked) wav.fault else wav.signal, 8.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when { !d.online -> "Offline"; d.blocked -> "Online, internet blocked"; else -> "Online" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { vm.toggleFavourite(d) }) {
                Icon(
                    if (favourite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    if (favourite) "Remove from favourites" else "Add to favourites",
                    tint = if (favourite) wav.activity else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, "Rename") }
        }

        if (d.online) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashTile("Signal", Modifier.weight(1f)) {
                    Text(if (d.isWired) "Wired" else "${d.rssi}%", style = MaterialTheme.typography.titleLarge)
                }
                DashTile("Download", Modifier.weight(1f)) {
                    Text(formatSpeed(d.downKBs), style = MaterialTheme.typography.titleLarge, color = wav.down)
                }
                DashTile("Upload", Modifier.weight(1f)) {
                    Text(formatSpeed(d.upKBs), style = MaterialTheme.typography.titleLarge, color = wav.up)
                }
            }
        }
        Group {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (d.online) {
                    InfoRow("Connection", d.connection)
                    InfoRow("IP address", d.ip, onClick = {
                        copyText(sheetCtx, "IP address", d.ip)
                        vm.message = "IP address copied"
                    })
                }
                InfoRow("MAC address", d.mac.uppercase(), onClick = {
                    copyText(sheetCtx, "MAC address", d.mac.uppercase())
                    vm.message = "MAC address copied"
                })
                if (isPrivateMac(d.mac)) Text(
                    "This is a private address the device made up for your network, so the name may not identify it.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                if (d.online) {
                    InfoRow("Connected for", formatDuration(d.onlineSec))
                }
                if (history != null) {
                    if (!d.online) InfoRow("Last online", formatAgo(history.last).replaceFirstChar { c -> c.uppercase() })
                    InfoRow("First noticed", formatDay(history.first))
                }
                Text(
                    "Tap an address to copy it.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                )
            }
        }

        Group {
            if (d.online) {
                GroupRow(
                    "Block internet",
                    "Stays on Wi-Fi but can't get online",
                    trailing = { Switch(checked = d.blocked, onCheckedChange = { vm.setBlocked(d, it) }, enabled = !vm.busy) },
                )
                GroupDivider(16.dp)
            }
            GroupRow(
                "Speed limit",
                if (d.hasLimit) "Down ${limitText(d.limitDownKB)}, up ${limitText(d.limitUpKB)}" else "Unlimited",
                onClick = { limiting = true },
                trailing = { Text(if (d.hasLimit) "Change" else "Set", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge) },
            )
        }

        if (!isPrivateMac(d.mac) && (!d.online || d.isWired)) {
            val ctx = LocalContext.current
            val scope = rememberCoroutineScope()
            OutlinedButton(onClick = {
                scope.launch {
                    vm.message = try {
                        withContext(Dispatchers.IO) { WakeOnLan.send(d.mac, WifiNetwork.current(ctx)?.network, PhoneWifi.broadcast(ctx)) }
                        "Wake signal sent to ${d.name}."
                    } catch (e: Exception) { "Couldn't send the wake signal." }
                }
            }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Rounded.PowerSettingsNew, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Wake this device")
            }
        }
        if (!d.online) {
            OutlinedButton(onClick = { forgetting = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Remove from list")
            }
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(if (d.name == "Unknown device") "" else d.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename device") },
            text = {
                OutlinedTextField(value = name, onValueChange = { if (it.length <= 31) name = it },
                    singleLine = true, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { renaming = false; vm.rename(d, name) }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }

    if (limiting) {
        var down by remember { mutableStateOf(if (d.limitDownKB > 0) d.limitDownKB.toString() else "") }
        var up by remember { mutableStateOf(if (d.limitUpKB > 0) d.limitUpKB.toString() else "") }
        val full = !d.hasLimit && list.limitsUsed >= list.limitSlots
        fun parse(s: String) = s.trim().toIntOrNull()?.coerceIn(0, 65535) ?: 0
        AlertDialog(
            onDismissRequest = { limiting = false },
            title = { Text("Speed limit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("In KB/s (1 MB/s is 1024). Leave empty for unlimited.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = down, onValueChange = { down = it.filter(Char::isDigit).take(5) },
                        label = { Text("Download") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = up, onValueChange = { up = it.filter(Char::isDigit).take(5) },
                        label = { Text("Upload") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    if (full) Text("The router allows at most ${list.limitSlots} limited devices, and they're all used.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(enabled = !full || (parse(down) == 0 && parse(up) == 0), onClick = {
                    limiting = false
                    vm.setLimit(d, parse(down), parse(up))
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (d.hasLimit) TextButton(onClick = { limiting = false; vm.setLimit(d, 0, 0) }) { Text("Remove limit") }
                    TextButton(onClick = { limiting = false }) { Text("Cancel") }
                }
            },
        )
    }

    if (forgetting) {
        AlertDialog(
            onDismissRequest = { forgetting = false },
            title = { Text("Remove ${d.name}?") },
            text = { Text("It reappears if it connects again.") },
            confirmButton = {
                TextButton(onClick = { forgetting = false; vm.forget(d); onDone() }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { forgetting = false }) { Text("Cancel") } },
        )
    }
}

private fun limitText(kb: Int) = if (kb == 0) "unlimited" else formatSpeed(kb.toLong())
