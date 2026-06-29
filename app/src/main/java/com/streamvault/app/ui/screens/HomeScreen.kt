package com.streamvault.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamvault.app.ui.StreamVaultApp
import com.streamvault.app.ui.theme.*
import com.streamvault.app.webview.YouTubeWebView

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    var progress by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableStateOf("YouTube") }

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
            )
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

        YouTubeWebView(
            url = "https://m.youtube.com",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onProgressChanged = { progress = it },
            onPageStarted = { url ->
                currentPage = "YouTube"
            },
            onPageFinished = { url ->
                StreamVaultApp.currentWebView?.let { webView ->
                    StreamVaultApp.currentWebView = webView
                }
            },
            webViewRef = { StreamVaultApp.currentWebView = it }
        )
    }
}
