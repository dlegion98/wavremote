package com.dayo.wavremote

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.NetworkWifi
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.ui.platform.LocalContext

private data class TabSpec(val tab: Tab, val label: String, val on: ImageVector, val off: ImageVector)

private val TABS = listOf(
    TabSpec(Tab.HOME, "Overview", Icons.Rounded.SpaceDashboard, Icons.Outlined.SpaceDashboard),
    TabSpec(Tab.DEVICES, "Devices", Icons.Rounded.Devices, Icons.Outlined.Devices),
    TabSpec(Tab.WIFI, "Wi-Fi", Icons.Rounded.Wifi, Icons.Outlined.Wifi),
    TabSpec(Tab.MORE, "More", Icons.Rounded.Tune, Icons.Outlined.Tune),
)

@Composable
fun MainScreen(vm: AppViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    // Live refresh of the visible tab every 10 s.
    LaunchedEffect(vm.tab) {
        while (true) {
            delay(10_000)
            when (vm.tab) {
                Tab.HOME -> vm.refreshOverview(quiet = true)
                Tab.DEVICES -> vm.refreshDevices(quiet = true)
                else -> {}
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Flat tab bar: hairline top border, accent marker over the active tab, no pill indicator.
            Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().navigationBarsPadding().height(62.dp)) {
                    TABS.forEach { t ->
                        val sel = vm.tab == t.tab
                        val c by animateColorAsState(
                            if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            tween(200), label = "tabColor")
                        val marker by animateDpAsState(if (sel) 32.dp else 0.dp, tween(220), label = "tabMarker")
                        Column(
                            Modifier.weight(1f).fillMaxHeight().clickable { vm.tab = t.tab },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.width(marker).height(3.dp).background(MaterialTheme.colorScheme.primary))
                            Spacer(Modifier.height(7.dp))
                            Icon(if (sel) t.on else t.off, contentDescription = null, tint = c, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(3.dp))
                            Text(t.label, style = MaterialTheme.typography.labelSmall, color = c,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium)
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (vm.away) Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { vm.openSettings(SettingsPage.AWAY) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Public, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Away from home, connected through your VPN", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            AnimatedContent(
                targetState = vm.tab,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(100)) },
                label = "tab",
            ) { tab ->
                when (tab) {
                    Tab.HOME -> DashboardHome(vm)
                    Tab.DEVICES -> DevicesTab(vm)
                    Tab.WIFI -> WifiTab(vm)
                    Tab.MORE -> MoreTab(vm)
                }
            }
        }
    }
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Label/value pair used inside sheets and dense groups. */
@Composable
fun InfoRow(label: String, value: String?, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** Header used at the top of each tab, with an optional refresh action. */
@Composable
fun TabHeader(title: String, subtitle: String? = null, onRefresh: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp), verticalAlignment = Alignment.Top) {
        ScreenTitle(title, subtitle, Modifier.weight(1f))
        if (onRefresh != null) IconButton(onClick = onRefresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ================================================================ More

@Composable
private fun NodeTile(name: String, sub: String, status: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Column(
        Modifier.width(150.dp).card().clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Router, color, size = 40.dp)
            Spacer(Modifier.weight(1f))
            StatusDot(color)
        }
        Column {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(status, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
private fun MoreTab(vm: AppViewModel) {
    var confirmReboot by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (vm.meshNodes == null) vm.refreshMesh(quiet = true) }
    val wav = Wav.colors

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TabHeader("More", onRefresh = { vm.refreshMesh() }) }

        item {
            GroupLabel("Mesh points")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NodeTile("Main router", vm.host ?: "", "Online", wav.signal) { vm.openSettings(SettingsPage.MESH) }
                vm.meshNodes?.forEach { n ->
                    NodeTile(n.name, n.ip ?: "", meshLinkLabel(n.rssi),
                        if ((n.rssi ?: -60) < -73) wav.activity else wav.signal) { vm.openSettings(SettingsPage.MESH) }
                }
                if (vm.meshNodes?.isEmpty() == true) NodeTile("No satellites", "Add one in web settings", "Not paired",
                    MaterialTheme.colorScheme.outline) { vm.openWebSettings() }
            }
        }

        item {
            GroupLabel("Settings")
            CategoryGrid(vm)
        }

        item {
            GroupLabel("App")
            AppUpdateCard(vm)
        }

        item {
            GroupLabel("Manage")
            Group {
                GroupRow("All settings", "Wi-Fi, guests, internet, ports, VPN, time, firmware",
                    icon = Icons.Rounded.Settings, onClick = { vm.openSettings() })
                GroupDivider()
                GroupRow("Restart router", "Everyone is offline for about 2 minutes",
                    icon = Icons.Rounded.RestartAlt, iconTint = wav.activity, onClick = { confirmReboot = true })
                GroupDivider()
                GroupRow("Web settings", "The router's own pages",
                    icon = Icons.Rounded.OpenInBrowser, iconTint = MaterialTheme.colorScheme.secondary,
                    onClick = vm::openWebSettings)
            }
        }

        item {
            Group {
                GroupRow("Sign out", icon = Icons.Rounded.Logout, iconTint = wav.fault, titleColor = wav.fault,
                    onClick = { confirmLogout = true })
            }
        }
    }

    if (confirmReboot) {
        AlertDialog(
            onDismissRequest = { confirmReboot = false },
            title = { Text("Restart the router?") },
            text = { Text("All devices lose internet for about 2 minutes while it restarts.") },
            confirmButton = { TextButton(onClick = { confirmReboot = false; vm.reboot() }) { Text("Restart") } },
            dismissButton = { TextButton(onClick = { confirmReboot = false }) { Text("Cancel") } },
        )
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Sign out?") },
            text = { Text("You'll need the router password to sign in again.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; vm.logout(forget = true) }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
        )
    }
}
