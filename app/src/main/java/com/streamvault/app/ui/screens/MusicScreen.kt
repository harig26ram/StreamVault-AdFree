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
import com.streamvault.app.ui.StreamVaultApp
import com.streamvault.app.ui.theme.*
import com.streamvault.app.webview.YouTubeWebView

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen() {
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
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "YouTube Music",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            },
            actions = {
                IconButton(onClick = { }) {
                    Icon(
                        Icons.Filled.Cast,
                        contentDescription = "Cast",
                        tint = TextSecondary
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
            url = "https://music.youtube.com",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onProgressChanged = { progress = it },
            webViewRef = { StreamVaultApp.currentWebView = it }
        )
    }
}
