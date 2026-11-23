package com.dayo.wavremote

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Switch built-in Tailscale on, sign in, and see whether it's connected. */
@Composable
fun TailscaleCard(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(TailscaleBridge.enabled(ctx)) }
    var status by remember { mutableStateOf(TailscaleBridge.Status()) }
    var busy by remember { mutableStateOf(false) }
    val wav = LocalWav.current

    LaunchedEffect(enabled) {
        if (!enabled) {
            status = TailscaleBridge.Status()
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) { TailscaleBridge.startIfEnabled(ctx) }
        while (true) {
            status = withContext(Dispatchers.IO) { TailscaleBridge.status() }
            delay(if (status.running) 10_000 else 2_000)
        }
    }

    Group(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Built-in Tailscale", style = MaterialTheme.typography.titleMedium)
                    Text("Reach your router from anywhere. No VPN app needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = { on ->
                    enabled = on
                    scope.launch(Dispatchers.IO) { TailscaleBridge.setEnabled(ctx, on) }
                })
            }
            if (enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(
                        when {
                            status.running -> wav.signal
                            status.state == "NeedsLogin" -> wav.activity
                            else -> MaterialTheme.colorScheme.outline
                        }))
                    Spacer(Modifier.width(8.dp))
                    Text(TailscaleBridge.stateText(status, enabled), style = MaterialTheme.typography.bodyMedium)
                }
                if (status.state == "NeedsLogin") {
                    Button(
                        onClick = {
                            val url = status.authUrl
                            if (url != null) {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } else {
                                busy = true
                                scope.launch(Dispatchers.IO) { TailscaleBridge.login(); busy = false }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text(if (status.authUrl == null) "Getting sign-in link…" else "Sign in to Tailscale") }
                }
                if (status.running) {
                    val host = ctx.getSharedPreferences("wavremote", android.content.Context.MODE_PRIVATE)
                        .getString("lastHost", null) ?: "192.168.20.1"
                    val problem = TailscaleBridge.routeProblem(status, host)
                    val shared = TailscaleBridge.routeCovering(status, host)
                    if (problem != null) {
                        Text(problem, style = MaterialTheme.typography.bodySmall, color = wav.activity)
                    } else if (shared != null) {
                        Text("Home network shared by ${shared.device} (${shared.route}).",
                            style = MaterialTheme.typography.bodySmall, color = wav.signal)
                    }
                    OutlinedButton(
                        onClick = {
                            busy = true
                            scope.launch(Dispatchers.IO) { TailscaleBridge.logout(); busy = false }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("Sign out of Tailscale") }
                }
                Text("Your home server must share your home network on Tailscale (subnet route " +
                    "192.168.20.0/24), approved in the Tailscale admin console. WavRemote shows up there as its own device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
