package com.dayo.wavremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.install(this)
        // The app is always dark, so keep light status/navigation bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { WavTheme { Root() } }
    }
}

@Composable
private fun Root(vm: AppViewModel = viewModel()) {
    val ctx = LocalContext.current
    var crash by remember { mutableStateOf(CrashReporter.takeLastCrash(ctx)) }
    crash?.let { details ->
        AlertDialog(
            onDismissRequest = { crash = null },
            title = { Text("WavRemote closed unexpectedly") },
            text = {
                Text(
                    "Share these details so the problem can be fixed.\n\n" + details.take(1500),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    ctx.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "WavRemote crash")
                            .putExtra(Intent.EXTRA_TEXT, details),
                        "Share crash details").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    crash = null
                }) { Text("Share") }
            },
            dismissButton = {
                TextButton(onClick = {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("WavRemote crash", details))
                    crash = null
                }) { Text("Copy") }
            },
        )
    }
    // The Surface sets the default text colour for every screen (light on dark).
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = vm.screen,
            transitionSpec = {
                (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 24 }) togetherWith fadeOut(tween(120))
            },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Connect -> ConnectScreen(vm)
                Screen.Starting -> StartingScreen(vm)
                is Screen.Login -> {
                    BackHandler { vm.backToConnect() }
                    LoginScreen(vm, s.host)
                }
                Screen.Main -> MainScreen(vm)
                is Screen.Settings -> {
                    BackHandler { vm.closeSettings() }
                    val page = s.page
                    if (page == null) SettingsListScreen(vm) else SettingsRouter(vm, page)
                }
                Screen.WebSettings -> {
                    BackHandler { vm.closeWebSettings() }
                    WebSettingsScreen(host = vm.apiHost ?: vm.host ?: vm.lastHost ?: "192.168.20.1", onClose = vm::closeWebSettings)
                }
            }
        }
    }
}
