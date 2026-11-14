package com.dayo.wavremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WavTheme { Root() } }
    }
}

@Composable
private fun Root(vm: AppViewModel = viewModel()) {
    when (val s = vm.screen) {
        Screen.Connect -> ConnectScreen(vm)
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
