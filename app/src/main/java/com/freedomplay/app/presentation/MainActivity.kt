package com.freedomplay.app.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.freedomplay.app.presentation.navigation.FreedomPlayNavGraph
import com.freedomplay.app.presentation.navigation.Screen
import com.freedomplay.app.presentation.ui.components.BottomNavBar
import com.freedomplay.app.presentation.ui.components.MiniPlayer
import com.freedomplay.app.data.local.preferences.PreferencesManager
import com.freedomplay.app.presentation.ui.theme.FreedomPlayTheme
import com.freedomplay.app.presentation.ui.theme.ThemeType
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

data class MiniPlayerData(
    val videoId: String,
    val title: String,
    val thumbnail: String,
    val isPlaying: Boolean
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermission()

        setContent {
            val themeType by preferencesManager.theme.collectAsStateWithLifecycle(
                initialValue = "AMOLED"
            )
            val resolvedThemeType = try { ThemeType.valueOf(themeType) } catch (_: Exception) { ThemeType.AMOLED }

            var miniPlayerData by remember { mutableStateOf<MiniPlayerData?>(null) }
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            FreedomPlayTheme(themeType = resolvedThemeType) {
                Scaffold(
                    bottomBar = {
                        if (currentRoute != Screen.Player.route) {
                            BottomNavBar(
                                currentRoute = currentRoute,
                                onItemSelected = { route ->
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        FreedomPlayNavGraph(
                            navController = navController,
                            onVideoStarted = { videoId, title, thumbnail ->
                                miniPlayerData = MiniPlayerData(
                                    videoId = videoId,
                                    title = title,
                                    thumbnail = thumbnail,
                                    isPlaying = true
                                )
                            }
                        )

                        if (miniPlayerData != null && currentRoute != Screen.Player.route) {
                            MiniPlayer(
                                title = miniPlayerData!!.title,
                                thumbnail = miniPlayerData!!.thumbnail,
                                isPlaying = miniPlayerData!!.isPlaying,
                                onPlayPause = {
                                    miniPlayerData = miniPlayerData?.copy(
                                        isPlaying = !miniPlayerData!!.isPlaying
                                    )
                                },
                                onClose = { miniPlayerData = null },
                                onExpand = {
                                    navController.navigate(
                                        Screen.Player.createRoute(miniPlayerData!!.videoId)
                                    )
                                },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
