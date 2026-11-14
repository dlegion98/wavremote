package com.dayo.wavremote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun ConnectScreen(vm: AppViewModel) {
    val wav = Wav.colors
    val found = vm.scanResults.filter { it.state == ProbeState.WAVLINK }
    val others = vm.scanResults.filter { it.state != ProbeState.WAVLINK }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SignalMap(online = found.isNotEmpty() || vm.scanning, satellites = 0, devices = 0,
                modifier = Modifier.padding(top = 16.dp))
        }
        item {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text(
                    when {
                        vm.scanning -> "Looking for your router"
                        found.isNotEmpty() -> "Found your Wavlink"
                        else -> "No Wavlink found"
                    },
                    style = MaterialTheme.typography.displaySmall,
                )
                Text(
                    vm.wifiInfo?.let { "This phone is ${it.phoneIp ?: "on Wi-Fi"}, and the network's router is ${it.gateway ?: "unknown"}." }
                        ?: "Join your mesh's Wi-Fi first, then scan again.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (vm.scanning) item {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Checking the usual addresses…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (found.isNotEmpty()) item {
            GroupLabel("Tap to sign in")
            Group {
                found.forEachIndexed { i, r ->
                    if (i > 0) GroupDivider()
                    GroupRow(
                        r.host,
                        r.title?.let { "Wavlink router, $it" } ?: "Wavlink router",
                        icon = Icons.Rounded.Router,
                        onClick = { vm.openRouter(r.host) },
                        trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    )
                }
            }
        }

        if (others.isNotEmpty()) item {
            GroupLabel("Other addresses checked")
            Group {
                others.forEachIndexed { i, r ->
                    if (i > 0) GroupDivider()
                    val reachable = r.state == ProbeState.OTHER_ROUTER
                    GroupRow(
                        r.host,
                        if (reachable) "Answers, but isn't a Wavlink" + ((r.title ?: r.server)?.let { " ($it)" } ?: "")
                        else r.error ?: "No answer",
                        icon = if (reachable) Icons.Rounded.HelpOutline else Icons.Rounded.WifiOff,
                        iconTint = if (reachable) wav.activity else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            GroupLabel("Know the address?")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = vm.customHost,
                    onValueChange = { vm.customHost = it },
                    placeholder = { Text("192.168.20.1") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = vm::scan, enabled = !vm.scanning, modifier = Modifier.height(56.dp)) {
                    Text(if (vm.scanning) "Scanning" else "Scan")
                }
            }
        }

        if (!vm.scanning && found.isEmpty()) item {
            Group {
                GroupRow(
                    "Scan again",
                    "Make sure mobile data isn't the only connection",
                    icon = Icons.Rounded.Refresh,
                    onClick = vm::scan,
                )
            }
        }
    }
}
