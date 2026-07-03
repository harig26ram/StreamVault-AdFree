package com.streamvault.app.presentation

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.streamvault.app.data.local.SettingsManager
import com.streamvault.app.presentation.navigation.MainNavGraph
import com.streamvault.app.presentation.ui.theme.StreamVaultTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsManager: SettingsManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request POST_NOTIFICATIONS on Android 13+ to avoid install warning
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

            StreamVaultTheme(
                darkTheme = isDarkMode,
                amoledMode = isAmoledMode
            ) {
                MainNavGraph()
            }
        }
    }
}
