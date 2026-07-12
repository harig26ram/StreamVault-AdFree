package com.streamvault.app.presentation

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import com.streamvault.app.data.local.SettingsManager
import com.streamvault.app.presentation.navigation.MainNavGraph
import com.streamvault.app.presentation.navigation.Screen
import com.streamvault.app.presentation.ui.theme.FreedomPlayTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var themeManager: com.streamvault.app.presentation.ui.theme.ThemeManager

    var pipModeActive by mutableStateOf(false)
        private set

    private var onPipModeChanged: ((Boolean) -> Unit)? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            var isDarkMode by remember { mutableStateOf(settingsManager.isDarkMode) }
            var isAmoledMode by remember { mutableStateOf(settingsManager.isAmoledMode) }
            val accent by themeManager.selectedTheme.collectAsState()
            var deepLinkUri by remember { mutableStateOf(intent?.data) }

            DisposableEffect(settingsManager) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "dark_mode") isDarkMode = settingsManager.isDarkMode
                    if (key == "amoled_mode") isAmoledMode = settingsManager.isAmoledMode
                }
                settingsManager.registerPreferenceChangeListener(listener)
                onDispose {
                    settingsManager.unregisterPreferenceChangeListener(listener)
                }
            }

            LaunchedEffect(Unit) {
                onPipModeChanged = { isInPip ->
                    pipModeActive = isInPip
                }
            }

            FreedomPlayTheme(
                darkTheme = isDarkMode,
                amoledMode = isAmoledMode,
                accent = accent.accent
            ) {
                val startDestination = when {
                    intent?.hasExtra("navigate_to") == true &&
                        intent.getStringExtra("navigate_to") == "subscriptions" -> Screen.Subscriptions.route
                    settingsManager.defaultTab == "search" -> Screen.Search.route
                    settingsManager.defaultTab == "subscriptions" -> Screen.Subscriptions.route
                    settingsManager.defaultTab == "library" -> Screen.Library.route
                    settingsManager.defaultTab == "music" -> Screen.Trending.route
                    else -> Screen.Home.route
                }
                MainNavGraph(
                    startDestination = startDestination,
                    deepLinkUri = deepLinkUri
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        onPipModeChanged?.invoke(isInPictureInPictureMode)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val navigateTo = intent.getStringExtra("navigate_to")
        if (navigateTo == "player") {
            val videoId = intent.getStringExtra("videoId")
            if (videoId != null) {
                // Navigation will be handled by NavGraph observing the intent
            }
        }
    }
}
