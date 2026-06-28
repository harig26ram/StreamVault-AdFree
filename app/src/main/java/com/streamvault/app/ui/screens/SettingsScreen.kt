package com.streamvault.app.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamvault.app.ui.theme.*

data class SettingItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String? = null,
    val iconTint: Color = TextSecondary,
    val hasToggle: Boolean = false,
    val isToggled: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    var adBlockingEnabled by remember { mutableStateOf(true) }
    var darkModeEnabled by remember { mutableStateOf(true) }
    var backgroundPlayEnabled by remember { mutableStateOf(true) }
    var sponsorBlockEnabled by remember { mutableStateOf(true) }

    val settingsGroups = remember {
        listOf(
            "Account" to listOf(
                SettingItem(Icons.Filled.AccountCircle, "Sign in with Google", "Access your YouTube account", Accent),
                SettingItem(Icons.Filled.Person, "Profile", "Manage your profile"),
                SettingItem(Icons.Filled.Subscriptions, "Subscriptions", "12 channels")
            ),
            "Playback" to listOf(
                SettingItem(Icons.Filled.PlayArrow, "Autoplay", "Play next video automatically", hasToggle = true, isToggled = true),
                SettingItem(Icons.Filled.MusicNote, "Background Play", "Play audio in background", hasToggle = true, isToggled = backgroundPlayEnabled),
                SettingItem(Icons.Filled.HighQuality, "Video Quality", "Default: 1080p"),
                SettingItem(Icons.Filled.Subtitles, "Captions", "English (Auto-generated)")
            ),
            "Privacy & Security" to listOf(
                SettingItem(Icons.Filled.Shield, "Ad Blocking", "Block ads and trackers", Accent, hasToggle = true, isToggled = adBlockingEnabled),
                SettingItem(Icons.Filled.Block, "SponsorBlock", "Skip sponsor segments", Accent, hasToggle = true, isToggled = sponsorBlockEnabled),
                SettingItem(Icons.Filled.Visibility, "Watch History", "Save watch history", hasToggle = true, isToggled = true),
                SettingItem(Icons.Filled.Delete, "Clear History", "Delete all watch history", iconTint = Error)
            ),
            "Appearance" to listOf(
                SettingItem(Icons.Filled.DarkMode, "Dark Mode", "Always enabled", hasToggle = true, isToggled = darkModeEnabled),
                SettingItem(Icons.Filled.Palette, "Theme Color", "Green"),
                SettingItem(Icons.Filled.TextFormat, "Text Size", "Medium")
            ),
            "About" to listOf(
                SettingItem(Icons.Filled.Info, "About StreamVault", "Version 1.0.0"),
                SettingItem(Icons.Filled.Star, "Rate App", "Rate us on Play Store"),
                SettingItem(Icons.Filled.Description, "Privacy Policy", ""),
                SettingItem(Icons.Filled.Code, "Open Source", "Licenses and credits")
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top App Bar
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

        // Settings List
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            settingsGroups.forEach { (groupName, settings) ->
                item(key = "header_$groupName") {
                    Text(
                        text = groupName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Accent,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(settings.size) { index ->
                    SettingCard(
                        setting = settings[index],
                        onToggle = { }
                    )
                }

                item(key = "spacer_$groupName") {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun SettingCard(
    setting: SettingItem,
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
                .clickable { if (setting.hasToggle) onToggle(!setting.isToggled) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = setting.icon,
                contentDescription = null,
                tint = setting.iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = setting.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                setting.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }
            if (setting.hasToggle) {
                Switch(
                    checked = setting.isToggled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Accent,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkCardElevated
                    )
                )
            } else {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = TextMuted
                )
            }
        }
    }
}
