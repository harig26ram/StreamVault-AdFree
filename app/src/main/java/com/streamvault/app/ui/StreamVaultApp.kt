package com.streamvault.app.ui

import android.webkit.WebView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamvault.app.ui.screens.*
import com.streamvault.app.ui.theme.*

object StreamVaultState {
    val webViews = mutableStateMapOf<Int, WebView?>()
    var currentTab by mutableIntStateOf(0)
    var isSplashDone by mutableStateOf(false)

    fun getWebView(tab: Int): WebView? = webViews[tab]
    fun setWebView(tab: Int, webView: WebView?) {
        webViews[tab] = webView
    }
}

@Composable
fun StreamVaultApp() {
    if (!StreamVaultState.isSplashDone) {
        SplashScreen(onSplashComplete = { StreamVaultState.isSplashDone = true })
    } else {
        MainScreen()
    }
}

@Composable
fun SplashScreen(onSplashComplete: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2500)
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkBackground, Primary)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.scale(scale).alpha(alpha)
        ) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = "StreamVault Logo",
                modifier = Modifier.size(120.dp),
                tint = Accent
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "StreamVault",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ad-Free Streaming",
                fontSize = 16.sp,
                color = TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val tabs = listOf(
        TabItem("Home", Icons.Filled.Home, Icons.Outlined.Home, 0),
        TabItem("Music", Icons.Filled.MusicNote, Icons.Outlined.MusicNote, 1),
        TabItem("Search", Icons.Filled.Search, Icons.Outlined.Search, 2),
        TabItem("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, 3)
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                contentColor = TextPrimary,
                tonalElevation = 0.dp
            ) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                if (StreamVaultState.currentTab == tab.index) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label, fontSize = 11.sp) },
                        selected = StreamVaultState.currentTab == tab.index,
                        onClick = { StreamVaultState.currentTab = tab.index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent,
                            selectedTextColor = Accent,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = Accent.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (StreamVaultState.currentTab) {
                0 -> HomeScreen()
                1 -> MusicScreen()
                2 -> SearchScreen()
                3 -> SettingsScreen()
            }
        }
    }
}

data class TabItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val index: Int
)
