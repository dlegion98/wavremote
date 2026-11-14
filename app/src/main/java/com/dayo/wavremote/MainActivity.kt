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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    WebSettingsScreen(host = vm.host ?: vm.lastHost ?: "192.168.20.1", onClose = vm::closeWebSettings)
                }
            }
        }
    }
}
