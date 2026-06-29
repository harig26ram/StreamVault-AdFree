package com.streamvault.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamvault.app.settings.SettingsManager
import com.streamvault.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var adBlockingEnabled by remember { mutableStateOf(SettingsManager.adBlockingEnabled) }
    var darkModeEnabled by remember { mutableStateOf(SettingsManager.darkModeEnabled) }
    var backgroundPlayEnabled by remember { mutableStateOf(SettingsManager.backgroundPlayEnabled) }
    var sponsorBlockEnabled by remember { mutableStateOf(SettingsManager.sponsorBlockEnabled) }
    var autoplayEnabled by remember { mutableStateOf(SettingsManager.autoplayEnabled) }
    var watchHistoryEnabled by remember { mutableStateOf(SettingsManager.watchHistoryEnabled) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Settings",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkBackground
            )
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "header_account") {
                Text(
                    text = "Account",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Accent,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SettingCard(
                    icon = Icons.Filled.AccountCircle,
                    title = "Sign in with Google",
                    subtitle = "Access your YouTube account",
                    iconTint = Accent,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://accounts.google.com"))
                        context.startActivity(intent)
                    }
                )
            }

            item {
                SettingCard(
                    icon = Icons.Filled.Subscriptions,
                    title = "Subscriptions",
                    subtitle = "View your YouTube subscriptions",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com/feed/channels"))
                        context.startActivity(intent)
                    }
                )
            }

            item(key = "spacer_account") {
                Spacer(modifier = Modifier.height(8.dp))
            }

            item(key = "header_playback") {
                Text(
                    text = "Playback",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Accent,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SettingCardToggle(
                    icon = Icons.Filled.PlayArrow,
                    title = "Autoplay",
                    subtitle = "Play next video automatically",
                    isToggled = autoplayEnabled,
                    onToggle = {
                        autoplayEnabled = it
                        SettingsManager.autoplayEnabled = it
                    }
                )
            }

            item {
                SettingCardToggle(
                    icon = Icons.Filled.MusicNote,
                    title = "Background Play",
                    subtitle = "Play audio when app is in background",
                    isToggled = backgroundPlayEnabled,
                    onToggle = {
                        backgroundPlayEnabled = it
                        SettingsManager.backgroundPlayEnabled = it
                    }
                )
            }

            item {
                SettingCard(
                    icon = Icons.Filled.HighQuality,
                    title = "Video Quality",
                    subtitle = "Default: ${SettingsManager.videoQuality}",
                    onClick = { }
                )
            }

            item(key = "spacer_playback") {
                Spacer(modifier = Modifier.height(8.dp))
            }

            item(key = "header_privacy") {
                Text(
                    text = "Privacy & Security",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Accent,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SettingCardToggle(
                    icon = Icons.Filled.Shield,
                    title = "Ad Blocking",
                    subtitle = "Block ads and trackers on YouTube",
                    iconTint = Accent,
                    isToggled = adBlockingEnabled,
                    onToggle = {
                        adBlockingEnabled = it
                        SettingsManager.adBlockingEnabled = it
                    }
                )
            }

            item {
                SettingCardToggle(
                    icon = Icons.Filled.Block,
                    title = "SponsorBlock",
                    subtitle = "Skip sponsor segments in videos",
                    iconTint = Accent,
                    isToggled = sponsorBlockEnabled,
                    onToggle = {
                        sponsorBlockEnabled = it
                        SettingsManager.sponsorBlockEnabled = it
                    }
                )
            }

            item {
                SettingCardToggle(
                    icon = Icons.Filled.Visibility,
                    title = "Watch History",
                    subtitle = "Save your watch history on YouTube",
                    isToggled = watchHistoryEnabled,
                    onToggle = {
                        watchHistoryEnabled = it
                        SettingsManager.watchHistoryEnabled = it
                    }
                )
            }

            item(key = "spacer_privacy") {
                Spacer(modifier = Modifier.height(8.dp))
            }

            item(key = "header_appearance") {
                Text(
                    text = "Appearance",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Accent,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SettingCardToggle(
                    icon = Icons.Filled.DarkMode,
                    title = "Dark Mode",
                    subtitle = if (darkModeEnabled) "Enabled (restart app to apply)" else "Disabled (restart app to apply)",
                    isToggled = darkModeEnabled,
                    onToggle = {
                        darkModeEnabled = it
                        SettingsManager.darkModeEnabled = it
                    }
                )
            }

            item(key = "spacer_appearance") {
                Spacer(modifier = Modifier.height(8.dp))
            }

            item(key = "header_about") {
                Text(
                    text = "About",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Accent,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SettingCard(
                    icon = Icons.Filled.Info,
                    title = "About StreamVault",
                    subtitle = "Version 1.1.0",
                    onClick = { }
                )
            }

            item {
                SettingCard(
                    icon = Icons.Filled.Code,
                    title = "Open Source",
                    subtitle = "Licenses and credits",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/harig26ram/StreamVault-AdFree"))
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun SettingCard(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = TextSecondary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkCard
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                subtitle?.let {
                    Text(
                        text = it,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TextMuted
            )
        }
    }
}

@Composable
fun SettingCardToggle(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = TextSecondary,
    isToggled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkCard
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!isToggled) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                subtitle?.let {
                    Text(
                        text = it,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }
            Switch(
                checked = isToggled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = DarkCardElevated
                )
            )
        }
    }
}
