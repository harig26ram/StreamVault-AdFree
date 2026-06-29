package com.streamvault.app.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamvault.app.settings.SettingsManager
import com.streamvault.app.ui.StreamVaultState
import com.streamvault.app.ui.theme.*
import com.streamvault.app.webview.YouTubeWebView

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    var progress by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayCircle,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "StreamVault",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkBackground
            ),
            windowInsets = WindowInsets(0, 0, 0, 0)
        )

        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = progress / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = Accent,
                trackColor = DarkBackground
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            YouTubeWebView(
                url = "https://m.youtube.com",
                modifier = Modifier.fillMaxSize(),
                adBlockingEnabled = SettingsManager.adBlockingEnabled,
                sponsorBlockEnabled = SettingsManager.sponsorBlockEnabled,
                backgroundPlayEnabled = SettingsManager.backgroundPlayEnabled,
                onProgressChanged = { progress = it },
                webViewRef = { StreamVaultState.setWebView(0, it) }
            )
        }
    }
}
