package com.streamvault.app.presentation.ui.screen

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.streamvault.app.BuildConfig
import com.streamvault.app.R
import com.streamvault.app.presentation.ui.components.MTricolorDivider
import com.streamvault.app.presentation.ui.theme.AppThemes
import com.streamvault.app.presentation.ui.theme.Theme
import com.streamvault.app.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToLogin: () -> Unit = {},
    onNavigateToEqualizer: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTheme by viewModel.selectedTheme.collectAsState()
    var showConnectDialog by remember { mutableStateOf(false) }
    var cookieInput by remember { mutableStateOf("") }
    var sapisidInput by remember { mutableStateOf("") }
    val cookieIsConnected by viewModel.cookieStore.isConnected.collectAsState()
    val scope = rememberCoroutineScope()

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            viewModel.completeSignIn(task)
            viewModel.refreshUserProfile()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        MTricolorDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SettingsSection(title = "Account") {
                if (uiState.userProfile != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (uiState.userProfile?.photoUrl != null) {
                            AsyncImage(
                                model = uiState.userProfile?.photoUrl,
                                contentDescription = "Profile",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.userProfile?.displayName ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = uiState.userProfile?.email ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    SettingsItem(
                        title = "Switch account",
                        subtitle = "Use a different Google account",
                        icon = Icons.Default.SwitchAccount,
                        onClick = {
                            viewModel.prepareAccountSwitch()
                            signInLauncher.launch(viewModel.getSignInIntent())
                        }
                    )
                    SettingsItem(
                        title = "Sign out",
                        subtitle = "Remove this account from FreedomPlay",
                        icon = Icons.Default.Logout,
                        onClick = {
                            viewModel.signOut()
                            onNavigateToLogin()
                        }
                    )
                } else {
                    SettingsItem(
                        title = "Sign in with Google",
                        subtitle = "Connect your YouTube account for subscriptions & history",
                        icon = Icons.Default.AccountCircle,
                        onClick = { onNavigateToLogin() }
                    )
                }
                SettingsItem(
                    title = "Connect YouTube account",
                    subtitle = if (cookieIsConnected) "Connected" else "Paste cookies for personalized feed",
                    icon = Icons.Default.Cookie,
                    onClick = { showConnectDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(title = "Appearance") {
                SettingsSwitch(
                    title = "Dark Mode",
                    subtitle = "Use dark theme",
                    icon = Icons.Default.DarkMode,
                    checked = uiState.isDarkMode,
                    onCheckedChange = { viewModel.setDarkMode(it) }
                )
                SettingsSwitch(
                    title = "AMOLED Black",
                    subtitle = "Pure black for OLED screens",
                    icon = Icons.Default.NightlightRound,
                    checked = uiState.isAmoledMode,
                    onCheckedChange = { viewModel.setAmoledMode(it) }
                )
                SettingsItem(
                    title = "Default Tab",
                    subtitle = uiState.defaultTab.replaceFirstChar { it.uppercase() },
                    icon = Icons.Default.Tab,
                    onClick = { viewModel.showDefaultTabDialog() }
                )
                ThemeSelector(
                    current = currentTheme,
                    onSelect = viewModel::selectTheme
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(title = "Playback") {
                SettingsItem(
                    title = "Video Quality",
                    subtitle = uiState.videoQuality,
                    icon = Icons.Default.HighQuality,
                    onClick = { viewModel.showQualityDialog() }
                )
                SettingsSwitch(
                    title = "Autoplay",
                    subtitle = "Play next video automatically",
                    icon = Icons.Default.PlayCircle,
                    checked = uiState.autoplay,
                    onCheckedChange = { viewModel.setAutoplay(it) }
                )
                SettingsSwitch(
                    title = "Resume Playback",
                    subtitle = "Remember where you left off",
                    icon = Icons.Default.Restore,
                    checked = uiState.rememberPlayback,
                    onCheckedChange = { viewModel.setRememberPlayback(it) }
                )
                SettingsSwitch(
                    title = "Skip Silence",
                    subtitle = "Auto-skip silent segments",
                    icon = Icons.Default.MusicOff,
                    checked = uiState.skipSilence,
                    onCheckedChange = { viewModel.setSkipSilence(it) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(title = "Player Controls") {
                SettingsSwitch(
                    title = "Gesture Controls",
                    subtitle = "Swipe for volume & brightness",
                    icon = Icons.Default.TouchApp,
                    checked = uiState.gestureControls,
                    onCheckedChange = { viewModel.setGestureControls(it) }
                )
                SettingsSwitch(
                    title = "Swipe Brightness",
                    subtitle = "Left side swipe adjusts brightness",
                    icon = Icons.Default.Brightness6,
                    checked = uiState.swipeBrightness,
                    onCheckedChange = { viewModel.setSwipeBrightness(it) }
                )
                SettingsSwitch(
                    title = "Pinch to Zoom",
                    subtitle = "Zoom into videos",
                    icon = Icons.Default.ZoomIn,
                    checked = uiState.pinchToZoom,
                    onCheckedChange = { viewModel.setPinchToZoom(it) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(title = "Features") {
                SettingsSwitch(
                    title = "Mini Player",
                    subtitle = "Picture-in-picture mini player",
                    icon = Icons.Default.PictureInPicture,
                    checked = uiState.miniPlayer,
                    onCheckedChange = { viewModel.setMiniPlayer(it) }
                )
                SettingsSwitch(
                    title = "Background Playback",
                    subtitle = "Play audio when app is minimized",
                    icon = Icons.Default.MusicNote,
                    checked = uiState.backgroundPlay,
                    onCheckedChange = { viewModel.setBackgroundPlay(it) }
                )
                SettingsSwitch(
                    title = "SponsorBlock",
                    subtitle = "Skip sponsor segments",
                    icon = Icons.Default.Block,
                    checked = uiState.sponsorBlock,
                    onCheckedChange = { viewModel.setSponsorBlock(it) }
                )
                SettingsItem(
                    title = "Audio Equalizer",
                    subtitle = if (uiState.equalizerEnabled) "On" else "Off",
                    icon = Icons.Default.Equalizer,
                    onClick = onNavigateToEqualizer
                )
                val equalizerPresets = listOf("Flat" to -1, "Bass Boost" to 0, "Vocal" to 1, "Rock" to 2, "Jazz" to 3, "Classical" to 4, "Pop" to 5)
                SettingsItem(
                    title = "Equalizer Preset",
                    subtitle = equalizerPresets.firstOrNull { it.second == uiState.equalizerPreset }?.first ?: "Flat",
                    icon = Icons.Default.Tune,
                    onClick = { viewModel.showEqualizerPresetDialog() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(title = "Data") {
                SettingsItem(
                    title = "Watch History",
                    subtitle = "${uiState.watchHistory.size} videos watched",
                    icon = Icons.Default.History,
                    onClick = { }
                )
                SettingsItem(
                    title = "Clear Watch History",
                    subtitle = "Remove all watch history",
                    icon = Icons.Default.DeleteForever,
                    onClick = { viewModel.showClearHistoryDialog() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(title = "About") {
                SettingsItem(
                    title = "Version",
                    subtitle = BuildConfig.VERSION_NAME,
                    icon = Icons.Default.Info,
                    onClick = { viewModel.showAboutDialog() }
                )
                SettingsItem(
                    title = stringResource(R.string.app_name),
                    subtitle = "Ad-free YouTube streaming",
                    icon = Icons.Default.Star,
                    onClick = { viewModel.showAboutDialog() }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (uiState.showQualityDialog) {
        val qualities = listOf("Auto", "Highest", "2160p", "1440p", "1080p", "720p", "480p", "360p")
        AlertDialog(
            onDismissRequest = { viewModel.dismissQualityDialog() },
            title = { Text("Video Quality", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    qualities.forEach { quality ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setVideoQuality(quality)
                                    viewModel.dismissQualityDialog()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = uiState.videoQuality == quality,
                                onClick = {
                                    viewModel.setVideoQuality(quality)
                                    viewModel.dismissQualityDialog()
                                }
                            )
                            Text(quality, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissQualityDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.showDefaultTabDialog) {
        val tabs = listOf("Home" to "home", "Search" to "search", "Subscriptions" to "subscriptions", "Library" to "library", "Trending" to "trending")
        AlertDialog(
            onDismissRequest = { viewModel.dismissDefaultTabDialog() },
            title = { Text("Default Tab", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    tabs.forEach { (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setDefaultTab(value)
                                    viewModel.dismissDefaultTabDialog()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = uiState.defaultTab == value,
                                onClick = {
                                    viewModel.setDefaultTab(value)
                                    viewModel.dismissDefaultTabDialog()
                                }
                            )
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDefaultTabDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.showEqualizerPresetDialog) {
        val equalizerPresets = listOf("Flat" to -1, "Bass Boost" to 0, "Vocal" to 1, "Rock" to 2, "Jazz" to 3, "Classical" to 4, "Pop" to 5)
        AlertDialog(
            onDismissRequest = { viewModel.dismissEqualizerPresetDialog() },
            title = { Text("Equalizer Preset", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    equalizerPresets.forEach { (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setEqualizerPreset(value)
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = uiState.equalizerPreset == value,
                                onClick = { viewModel.setEqualizerPreset(value) }
                            )
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissEqualizerPresetDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissClearHistoryDialog() },
            title = { Text("Clear Watch History?", fontWeight = FontWeight.Bold) },
            text = { Text("This will remove all ${uiState.watchHistory.size} videos from your watch history. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearWatchHistory() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClearHistoryDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.showAboutDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAboutDialog() },
            title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Version ${BuildConfig.VERSION_NAME}")
                    Text("Ad-free YouTube streaming with custom UI")
                    Text("Built with YouTube InnerTube API + custom MediaCodec player")
                    Text("Features:", fontWeight = FontWeight.Medium)
                    Text("  \u2022 Ad-free video streaming", fontSize = 13.sp)
                    Text("  \u2022 Background playback", fontSize = 13.sp)
                    Text("  \u2022 Gesture controls", fontSize = 13.sp)
                    Text("  \u2022 SponsorBlock integration", fontSize = 13.sp)
                    Text("  \u2022 Mini player", fontSize = 13.sp)
                    Text("  \u2022 Custom video quality", fontSize = 13.sp)
                    Text("  \u2022 Dark/AMOLED themes", fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissAboutDialog() }) {
                    Text("OK")
                }
            }
        )
    }

    if (showConnectDialog) {
        AlertDialog(
            onDismissRequest = { showConnectDialog = false },
            title = { Text("Connect YouTube Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Paste your YouTube cookies for a personalized feed. Get these from your browser's DevTools > Application > Cookies > youtube.com",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = cookieInput,
                        onValueChange = { cookieInput = it },
                        label = { Text("Cookie string") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    OutlinedTextField(
                        value = sapisidInput,
                        onValueChange = { sapisidInput = it },
                        label = { Text("SAPISID value") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (cookieInput.isNotBlank() && sapisidInput.isNotBlank()) {
                            viewModel.connectCookies(cookieInput.trim(), sapisidInput.trim())
                            showConnectDialog = false
                            cookieInput = ""
                            sapisidInput = ""
                        }
                    }
                ) { Text("Connect") }
            },
            dismissButton = {
                Row {
                    if (cookieIsConnected) {
                        TextButton(onClick = {
                            viewModel.disconnectCookies()
                            showConnectDialog = false
                        }) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = { showConnectDialog = false }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun ThemeSelector(
    current: Theme,
    onSelect: (Theme) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = "Accent Color",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppThemes.forEach { theme ->
                val selected = theme.id == current.id
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(theme.accent)
                        .border(
                            width = 3.dp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { onSelect(theme) },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
