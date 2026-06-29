package com.streamvault.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.streamvault.app.settings.SettingsManager
import com.streamvault.app.ui.StreamVaultApp
import com.streamvault.app.ui.theme.StreamVaultTheme

class MainActivity : ComponentActivity() {

    private var backCallback: OnBackPressedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.init(this)

        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val webView = StreamVaultApp.currentWebView
                if (webView != null && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback!!)

        setContent {
            StreamVaultTheme(darkTheme = SettingsManager.darkModeEnabled) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StreamVaultApp()
                }
            }
        }
    }
}
