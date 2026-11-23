package com.dayo.wavremote

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** How to use the app away from home, plus a live check of the connection. */
@Composable
fun AwayPage(vm: AppViewModel) {
    val ctx = LocalContext.current
    SettingsPageScaffold(vm, "Away from home", emptyList()) { _ ->
        TailscaleCard()

        Section("Right now") {
            val vpn = RouterLink.vpnActive(ctx)
            Text(
                when {
                    vm.apiHost == null -> "Not connected to your router."
                    vm.away -> "Connected through your VPN, at ${vm.apiHost}."
                    else -> "Connected at home over Wi-Fi."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Note(if (vpn) "A VPN is on." else "No VPN is on.")
            vm.routeCheck?.let { Note(it) }
            OutlinedButton(onClick = vm::testRoute, enabled = !vm.routeChecking,
                modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(if (vm.routeChecking) "Checking…" else "Test the connection")
            }
        }

        Section("How it works") {
            Note("Your router's controls only answer inside your home network. To use the app when you're out, " +
                "your phone joins your home network through a private VPN. The app then works exactly as it does at home, " +
                "including the widgets and new-device alerts.")
            Note("Don't use port forwarding or the router's remote management for this. The router's page isn't encrypted, " +
                "so your admin password would cross the internet in the open.")
        }

        Section("Option 1: Tailscale (recommended)") {
            Note("1. On your home server (or any computer that's always on at home), install Tailscale and sign in.")
            Note("2. Share your home network through it: run  tailscale up --advertise-routes=192.168.20.0/24  " +
                "(use your router's network if it isn't 192.168.20.x).")
            Note("3. In the Tailscale admin page, approve that route for the machine.")
            Note("4. Switch on Built-in Tailscale above and sign in with the same account. " +
                "(The Tailscale app works too, if you'd rather use that.)")
            Note("5. Tap Test the connection. Nothing needs filling in below.")
        }

        Section("Option 2: ZeroTier on the router") {
            Note("1. Create a free network at my.zerotier.com and copy its network ID.")
            Note("2. Turn on ZeroTier on the router with that ID, then approve the router in the ZeroTier website.")
            Note("3. Install ZeroTier on this phone, join the same network and approve the phone too.")
            Note("4. Enter the router's ZeroTier address (shown in the ZeroTier website) below, then test.")
            OutlinedButton(onClick = { vm.openSettings(SettingsPage.ZEROTIER) },
                modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Open ZeroTier settings") }
        }

        Section("Router address away from home") {
            var value by remember { mutableStateOf(vm.awayHostSetting) }
            val err = if (value.isNotBlank() && !isIp(value)) "Enter an IP address, like 10.147.17.1" else null
            Field("Address (optional)", value, { value = it.trim() }, error = err,
                hint = "Only needed when your VPN gives the router a different address, as ZeroTier does.")
            Button(onClick = { vm.saveAwayHost(value) }, enabled = err == null && value != vm.awayHostSetting,
                modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Save address") }
        }
    }
}
