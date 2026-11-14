package com.dayo.wavremote

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** The router's own web pages, for settings the app doesn't cover yet. */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebSettingsScreen(host: String, onClose: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Web settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                    }
                },
            )
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { ctx ->
                WebView(ctx).apply {
                    // The router serves a phone layout (/phone_ui/) to mobile browsers. Use the normal
                    // mobile viewport so its login card isn't pushed off the top of the screen.
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = false
                    settings.loadWithOverviewMode = false
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webViewClient = WebViewClient()
                    loadUrl("http://$host/")
                    webView = this
                }
            },
        )
    }
}
