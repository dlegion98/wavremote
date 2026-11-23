package com.dayo.wavremote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(vm: AppViewModel, host: String) {
    var password by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var stayIn by remember { mutableStateOf(true) }
    val err = vm.loginError
    val submit = { if (password.isNotEmpty()) vm.login(host, password, stayIn) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = vm::backToConnect, modifier = Modifier.padding(start = 0.dp)) { Text("Find a different router") }
        IconBadge(Icons.Rounded.Key, size = 56.dp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Sign in to your router", style = MaterialTheme.typography.displaySmall)
            Text(
                "Use the admin password for the Wavlink at $host. If it's never been changed, it's \"admin\".",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (vm.needsSetup) {
            Group {
                GroupRow("Setup isn't finished", "Complete first-time setup in web settings, then sign in here.",
                    icon = Icons.Rounded.Key, iconTint = Wav.colors.activity, onClick = vm::openWebSettings)
            }
        }

        if (vm.hasSavedLogin(host)) {
            OutlinedButton(
                onClick = { vm.openRouter(host) },
                enabled = !vm.loginBusy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (vm.loginBusy) "Connecting…" else "Reconnect with the saved sign-in") }
        }

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Admin password") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") } },
            isError = err != null,
            supportingText = if (err != null) { { Text(err) } } else null,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = stayIn, onCheckedChange = { stayIn = it })
            Text("Stay signed in on this phone", style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = { submit() },
            enabled = !vm.loginBusy && password.isNotEmpty(),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            if (vm.loginBusy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary)
            else Text("Sign in", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(4.dp))
        TextButton(onClick = vm::openWebSettings) { Text("Open the router's web page instead") }

        GroupLabel("Away from home?")
        TailscaleCard()
    }
}
