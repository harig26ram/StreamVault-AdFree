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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamvault.app.ui.screens.*
import com.streamvault.app.ui.theme.*

object StreamVaultApp {
    var currentWebView: WebView? = null
        internal set
}

@Composable
fun StreamVaultApp() {
    var showSplash by remember { mutableStateOf(true) }

    if (showSplash) {
        SplashScreen(onSplashComplete = { showSplash = false })
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
                contentDescription = "StreamVault",
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
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                contentColor = TextPrimary
            ) {
                NavigationBarItem(
                    icon = {
                        Icon(
                            if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home,
                            contentDescription = "Home"
                        )
                    },
                    label = { Text("Home") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Accent,
                        selectedTextColor = Accent,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = Accent.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            if (selectedTab == 1) Icons.Filled.MusicNote else Icons.Outlined.MusicNote,
                            contentDescription = "Music"
                        )
                    },
                    label = { Text("Music") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Accent,
                        selectedTextColor = Accent,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = Accent.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            if (selectedTab == 2) Icons.Filled.Search else Icons.Outlined.Search,
                            contentDescription = "Search"
                        )
                    },
                    label = { Text("Search") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Accent,
                        selectedTextColor = Accent,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = Accent.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            if (selectedTab == 3) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = "Settings"
                        )
                    },
                    label = { Text("Settings") },
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
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
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> HomeScreen()
                1 -> MusicScreen()
                2 -> SearchScreen()
                3 -> SettingsScreen()
            }
        }
    }
}
