package com.dayo.wavremote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val AUTH_NAMES = mapOf(0 to "Open", 1 to "WPA2", 2 to "WPA3/WPA2", 3 to "WPA/WPA2", 4 to "Enterprise")

@Composable
fun WifiTab(vm: AppViewModel) {
    LaunchedEffect(Unit) {
        if (vm.wifiConfig == null) vm.refreshWifi()
        vm.loadQuick()
    }
    val cfg = vm.wifiConfig
    var editing by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TabHeader("Wi-Fi", "The networks your devices join", onRefresh = { vm.refreshWifi(); vm.loadQuick() })
        if (cfg == null) {
            Loading()
            return@Column
        }

        if (editing) {
            WifiEditor(vm, cfg, onDone = { editing = false })
        } else {
            if (cfg.bandsMatch) {
                NetworkCard(vm, cfg.band2.ssid, cfg.band2.password, cfg.band2.security,
                    bands = listOfNotNull("2.4 GHz".takeIf { cfg.band2.enabled }, "5 GHz".takeIf { cfg.band5.enabled }),
                    off = !cfg.band2.enabled && !cfg.band5.enabled, onEdit = { editing = true })
            } else {
                NetworkCard(vm, cfg.band2.ssid, cfg.band2.password, cfg.band2.security, listOf("2.4 GHz"),
                    off = !cfg.band2.enabled, onEdit = { editing = true })
                NetworkCard(vm, cfg.band5.ssid, cfg.band5.password, cfg.band5.security, listOf("5 GHz"),
                    off = !cfg.band5.enabled, onEdit = { editing = true })
            }
        }

        // ---- other networks
        GroupLabel("Other networks")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val guest = vm.data(Quick.GUEST)
            val kids = vm.data(Quick.KIDS)
            NetTile(
                "Guest", Icons.Rounded.People,
                guest?.let { if (it.s("guest_enable") == "1") it.optString("SSID") else "Off" } ?: "…",
                on = guest?.s("guest_enable") == "1",
                modifier = Modifier.weight(1f),
            ) { vm.openSettings(SettingsPage.GUEST) }
            NetTile(
                "Kids", Icons.Rounded.ChildCare,
                kids?.let { if (it.s("enable") == "1") it.optString("SSID") else "Off" } ?: "…",
                on = kids?.s("enable") == "1",
                modifier = Modifier.weight(1f),
            ) { vm.openSettings(SettingsPage.KIDS) }
        }

        GroupLabel("Radio")
        Group {
            GroupRow(
                "Channels, security and off-times",
                "2.4 GHz channel ${chan(cfg.band2)}, 5 GHz channel ${chan(cfg.band5)}",
                icon = Icons.Rounded.Tune,
                onClick = { vm.openSettings(SettingsPage.WIFI_ADVANCED) },
            )
            GroupDivider()
            GroupRow("Mesh", "Satellites, band steering and roaming", icon = Icons.Rounded.Wifi,
                onClick = { vm.openSettings(SettingsPage.MESH) })
        }
    }
}

private fun chan(b: BandConfig) =
    if (b.channel == "0" || b.channel.isEmpty()) "auto" + (b.autoChannel?.let { " ($it)" } ?: "") else b.channel

/** A Wi-Fi network shown as a card: name, bands, security, password with copy, share and edit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NetworkCard(
    vm: AppViewModel,
    ssid: String,
    password: String,
    security: Int,
    bands: List<String>,
    off: Boolean,
    onEdit: () -> Unit,
) {
    var reveal by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val wav = Wav.colors
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Wifi, if (off) MaterialTheme.colorScheme.outline else wav.signal, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(ssid, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (off) "Switched off" else "Broadcasting", style = MaterialTheme.typography.bodySmall,
                    color = if (off) wav.fault else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            bands.forEach { Tag(it, wav.signal) }
            Tag(AUTH_NAMES[security] ?: "Secured", if (security == 0) wav.fault else MaterialTheme.colorScheme.secondary)
        }
        if (security != 0) {
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text("Password", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (reveal) password else "•".repeat(password.length.coerceIn(8, 16)),
                        style = MaterialTheme.typography.bodyLarge, fontFamily = if (reveal) FontFamily.Monospace else null)
                }
                IconButton(onClick = { reveal = !reveal }) {
                    Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (reveal) "Hide" else "Show")
                }
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(password))
                    vm.message = "Password copied."
                }) { Icon(Icons.Rounded.ContentCopy, "Copy password") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = { vm.openSettings(SettingsPage.SHARE_WIFI) }, modifier = Modifier.weight(1f).height(46.dp)) {
                Icon(Icons.Rounded.QrCode2, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Share")
            }
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f).height(46.dp)) {
                Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Edit")
            }
        }
    }
}

@Composable
private fun NetTile(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, sub: String, on: Boolean,
                    modifier: Modifier, onClick: () -> Unit) {
    val wav = Wav.colors
    Column(modifier.card().clickable(onClick = onClick).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, if (on) wav.signal else MaterialTheme.colorScheme.outline, size = 36.dp)
            Spacer(Modifier.weight(1f))
            androidx.compose.foundation.layout.Box(Modifier.size(10.dp).clip(CircleShape)
                .background(if (on) wav.signal else MaterialTheme.colorScheme.outlineVariant))
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ================================================================ editor

@Composable
private fun WifiEditor(vm: AppViewModel, cfg: WifiConfig, onDone: () -> Unit) {
    var combined by remember(cfg) { mutableStateOf(cfg.bandsMatch) }
    var ssid2 by remember(cfg) { mutableStateOf(cfg.band2.ssid) }
    var pass2 by remember(cfg) { mutableStateOf(cfg.band2.password) }
    var ssid5 by remember(cfg) { mutableStateOf(cfg.band5.ssid) }
    var pass5 by remember(cfg) { mutableStateOf(cfg.band5.password) }
    var showPw by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    val radius = cfg.band2.isRadius || cfg.band5.isRadius
    val outS5 = if (combined) ssid2 else ssid5
    val outP5 = if (combined) pass2 else pass5
    fun passError(p: String, open: Boolean) = if (open) null else wifiPassErr(p)

    val e2 = ssidErr(ssid2)
    val ep2 = passError(pass2, cfg.band2.isOpen)
    val e5 = if (combined) null else ssidErr(ssid5)
    val ep5 = if (combined) null else passError(pass5, cfg.band5.isOpen)
    val changed = ssid2 != cfg.band2.ssid || pass2 != cfg.band2.password ||
        outS5 != cfg.band5.ssid || outP5 != cfg.band5.password
    val valid = listOf(e2, ep2, e5, ep5).all { it == null }

    Group {
        GroupRow(
            "One network for both bands",
            "Devices pick 2.4 or 5 GHz automatically",
            icon = Icons.Rounded.Wifi,
            trailing = {
                Switch(checked = combined, onCheckedChange = {
                    combined = it
                    if (it) { ssid5 = ssid2; pass5 = pass2 }
                }, enabled = cfg.band2.security == cfg.band5.security)
            },
        )
    }
    BandFields(if (combined) "Your network" else "2.4 GHz", cfg.band2, ssid2, { ssid2 = it }, e2, pass2, { pass2 = it }, ep2,
        showPw, { showPw = !showPw }, !radius)
    if (!combined) BandFields("5 GHz", cfg.band5, ssid5, { ssid5 = it }, e5, pass5, { pass5 = it }, ep5,
        showPw, { showPw = !showPw }, !radius)
    if (radius) Note("This network uses enterprise (RADIUS) security. Change it in web settings.")

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f).height(52.dp)) { Text("Cancel") }
        Button(onClick = { confirm = true }, enabled = changed && valid && !radius && !vm.busy,
            modifier = Modifier.weight(1f).height(52.dp)) { Text("Save") }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Apply Wi-Fi changes?") },
            text = {
                Text("Wi-Fi restarts and every device disconnects for about 20 seconds. " +
                    "Devices need the new name or password if you changed them, including this phone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    vm.saveWifi(ssid2, pass2, outS5, outP5)
                    onDone()
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BandFields(
    title: String,
    band: BandConfig,
    ssid: String, onSsid: (String) -> Unit, ssidError: String?,
    pass: String, onPass: (String) -> Unit, passError: String?,
    showPw: Boolean, onToggleShow: () -> Unit,
    editable: Boolean,
) {
    Column {
        GroupLabel(title)
        Group {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!band.enabled) Note("This band is switched off.", error = true)
                OutlinedTextField(
                    value = ssid, onValueChange = onSsid,
                    label = { Text("Network name") },
                    singleLine = true, enabled = editable,
                    isError = ssidError != null,
                    supportingText = if (ssidError != null) { { Text(ssidError) } } else null,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (band.isOpen) {
                    Note("Open network with no password. Add one in Channels, security and off-times.", error = true)
                } else {
                    OutlinedTextField(
                        value = pass, onValueChange = onPass,
                        label = { Text("Password") },
                        singleLine = true, enabled = editable,
                        visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = { TextButton(onClick = onToggleShow) { Text(if (showPw) "Hide" else "Show") } },
                        isError = passError != null,
                        supportingText = if (passError != null) { { Text(passError) } } else null,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
