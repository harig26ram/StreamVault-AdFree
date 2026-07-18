package com.freedomplay.app.presentation.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedomplay.app.presentation.ui.theme.ThemeType
import com.freedomplay.app.presentation.viewmodel.SettingsViewModel
import com.freedomplay.app.util.CrashLogger
import java.io.File

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val themeType by viewModel.themeType.collectAsStateWithLifecycle()
    val defaultQuality by viewModel.defaultQuality.collectAsStateWithLifecycle()
    val skipSilence by viewModel.skipSilence.collectAsStateWithLifecycle()
    val audioOnly by viewModel.audioOnly.collectAsStateWithLifecycle()
    val rememberPosition by viewModel.rememberPosition.collectAsStateWithLifecycle()
    val defaultDownloadQuality by viewModel.defaultDownloadQuality.collectAsStateWithLifecycle()

    var showQualityMenu by remember { mutableStateOf(false) }
    var showDownloadQualityMenu by remember { mutableStateOf(false) }
    var showDebugLogs by remember { mutableStateOf(false) }
    var showCrashLogs by remember { mutableStateOf(false) }

    val packageInfo = remember {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (e: Exception) {
            null
        }
    }
    val versionName = packageInfo?.versionName ?: "1.0.0"

    val downloadPath = remember {
        context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)?.absolutePath
            ?: context.filesDir.absolutePath
    }

    if (showDebugLogs) {
        DebugLogDialog(onDismiss = { showDebugLogs = false })
    }

    if (showCrashLogs) {
        CrashLogDialog(onDismiss = { showCrashLogs = false })
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Settings",
                color = Color(0xFFE0E0E0),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Audio Section
        item {
            SettingsSectionHeader(icon = Icons.Default.Equalizer, title = "Audio")
            SettingsCard {
                SettingsToggleRow(
                    icon = Icons.Default.AudioFile,
                    title = "Skip Silence",
                    subtitle = "Automatically skip silent parts",
                    checked = skipSilence,
                    onCheckedChange = { viewModel.setSkipSilence(it) }
                )
            }
        }

        // Playback Section
        item {
            SettingsSectionHeader(icon = Icons.Default.PlayCircle, title = "Playback")
            SettingsCard {
                SettingsClickableRow(
                    icon = Icons.Default.PlayCircle,
                    title = "Default Quality",
                    subtitle = "Current: $defaultQuality"
                ) {
                    showQualityMenu = true
                }
                DropdownMenu(
                    expanded = showQualityMenu,
                    onDismissRequest = { showQualityMenu = false },
                    modifier = Modifier.background(Color(0xFF1A1A2E))
                ) {
                    listOf("Auto", "480p", "720p", "1080p", "2160p", "Max").forEach { quality ->
                        DropdownMenuItem(
                            text = { Text(quality, color = Color(0xFFE0E0E0)) },
                            onClick = {
                                viewModel.setDefaultQuality(quality)
                                showQualityMenu = false
                            }
                        )
                    }
                }
                SettingsDivider()
                SettingsToggleRow(
                    icon = Icons.Default.AudioFile,
                    title = "Audio Only Mode",
                    subtitle = "Play audio without video",
                    checked = audioOnly,
                    onCheckedChange = { viewModel.setAudioOnly(it) }
                )
                SettingsDivider()
                SettingsToggleRow(
                    icon = Icons.Default.Info,
                    title = "Remember Position",
                    subtitle = "Resume from where you left off",
                    checked = rememberPosition,
                    onCheckedChange = { viewModel.setRememberPosition(it) }
                )
            }
        }

        // Downloads Section
        item {
            SettingsSectionHeader(icon = Icons.Default.Download, title = "Downloads")
            SettingsCard {
                SettingsClickableRow(
                    icon = Icons.Default.Download,
                    title = "Download Quality",
                    subtitle = "Current: $defaultDownloadQuality"
                ) {
                    showDownloadQualityMenu = true
                }
                DropdownMenu(
                    expanded = showDownloadQualityMenu,
                    onDismissRequest = { showDownloadQualityMenu = false },
                    modifier = Modifier.background(Color(0xFF1A1A2E))
                ) {
                    listOf("144p", "240p", "360p", "480p", "720p", "1080p").forEach { quality ->
                        DropdownMenuItem(
                            text = { Text(quality, color = Color(0xFFE0E0E0)) },
                            onClick = {
                                viewModel.setDefaultDownloadQuality(quality)
                                showDownloadQualityMenu = false
                            }
                        )
                    }
                }
                SettingsDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF808080),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Storage Path",
                            color = Color(0xFFE0E0E0),
                            fontSize = 14.sp
                        )
                        Text(
                            text = downloadPath,
                            color = Color(0xFF606060),
                            fontSize = 11.sp,
                            maxLines = 2
                        )
                    }
                }
            }
        }

        // Theme Section
        item {
            SettingsSectionHeader(icon = Icons.Default.DarkMode, title = "Theme")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
                shape = RoundedCornerShape(12.dp)
            ) {
                ThemeType.entries.forEach { theme ->
                    SettingsThemeRow(
                        theme = theme,
                        isSelected = themeType == theme,
                        onClick = { viewModel.setThemeType(theme) }
                    )
                    if (theme != ThemeType.entries.last()) {
                        SettingsDivider()
                    }
                }
            }
        }

        // Debug & Logging Section
        item {
            SettingsSectionHeader(icon = Icons.Default.BugReport, title = "Debug & Logging")
            SettingsCard {
                SettingsClickableRow(
                    icon = Icons.Default.BugReport,
                    title = "View Debug Logs",
                    subtitle = "Recent app activity"
                ) {
                    showDebugLogs = true
                }
                SettingsDivider()
                SettingsClickableRow(
                    icon = Icons.Default.BugReport,
                    title = "Crash Reports",
                    subtitle = "View past crashes"
                ) {
                    showCrashLogs = true
                }
                SettingsDivider()
                SettingsClickableRow(
                    icon = Icons.Default.Share,
                    title = "Share Logs",
                    subtitle = "Export logs for debugging"
                ) {
                    shareLogs(context)
                }
                SettingsDivider()
                SettingsClickableRow(
                    icon = Icons.Default.DeleteForever,
                    title = "Clear Logs",
                    subtitle = "Remove all stored logs"
                ) {
                    CrashLogger.clearLogs(context)
                    Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // About Section
        item {
            SettingsSectionHeader(icon = Icons.Default.Info, title = "About")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
                shape = RoundedCornerShape(12.dp)
            ) {
                SettingsInfoRow(label = "Version", value = versionName)
                SettingsDivider()
                SettingsInfoRow(label = "Build", value = if (com.freedomplay.app.BuildConfig.DEBUG) "Debug" else "Release")
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DebugLogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val logs = remember { CrashLogger.getDebugLogs(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A0A0A),
        title = { Text("Debug Logs", color = Color(0xFFE0E0E0)) },
        text = {
            SelectionContainer {
                Text(
                    text = logs.ifEmpty { "No logs yet" },
                    color = Color(0xFF808080),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        },
        confirmButton = {
            Row {
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("logs", logs))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.CopyAll, "Copy", tint = Color(0xFFFF4081))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Close", color = Color(0xFFFF4081))
                }
            }
        }
    )
}

@Composable
private fun CrashLogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val crashLogs = remember { CrashLogger.getCrashLogs(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A0A0A),
        title = { Text("Crash Reports", color = Color(0xFFE0E0E0)) },
        text = {
            SelectionContainer {
                Text(
                    text = crashLogs.ifEmpty { "No crashes recorded" },
                    color = Color(0xFF808080),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        },
        confirmButton = {
            Row {
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("crash_logs", crashLogs))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.CopyAll, "Copy", tint = Color(0xFFFF4081))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Close", color = Color(0xFFFF4081))
                }
            }
        }
    )
}

private fun shareLogs(context: Context) {
    val allLogs = buildString {
        appendLine("=== FreedomPlay Debug Logs ===")
        appendLine(CrashLogger.getDebugLogs(context))
        appendLine()
        appendLine("=== Crash Reports ===")
        appendLine(CrashLogger.getCrashLogs(context))
    }

    val logFile = File(context.cacheDir, "freedomplay_logs.txt")
    logFile.writeText(allLogs)

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        logFile
    )

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "FreedomPlay Logs")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Logs"))
}

@Composable
private fun SettingsSectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color(0xFFFF4081),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = Color(0xFFE0E0E0),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        content()
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color(0xFF808080),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color(0xFFE0E0E0), fontSize = 14.sp)
            Text(text = subtitle, color = Color(0xFF606060), fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFFF4081),
                checkedTrackColor = Color(0xFFFF4081).copy(alpha = 0.3f),
                uncheckedThumbColor = Color(0xFF808080),
                uncheckedTrackColor = Color(0xFF2A2A3E)
            )
        )
    }
}

@Composable
private fun SettingsClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color(0xFF808080),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color(0xFFE0E0E0), fontSize = 14.sp)
            Text(text = subtitle, color = Color(0xFF606060), fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingsThemeRow(
    theme: ThemeType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = Color(0xFFFF4081),
                unselectedColor = Color(0xFF808080)
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = when (theme) {
                    ThemeType.AMOLED -> "AMOLED Dark"
                    ThemeType.GRADIENT -> "Deep Gradient"
                    ThemeType.MATERIAL3 -> "Material 3"
                    ThemeType.LIGHT -> "Light"
                },
                color = Color(0xFFE0E0E0),
                fontSize = 14.sp
            )
            Text(
                text = when (theme) {
                    ThemeType.AMOLED -> "Pure black background, hot pink accents"
                    ThemeType.GRADIENT -> "Deep purple tones with gradient feel"
                    ThemeType.MATERIAL3 -> "Standard Material 3 dark theme"
                    ThemeType.LIGHT -> "Light theme for daytime use"
                },
                color = Color(0xFF606060),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color(0xFFE0E0E0), fontSize = 14.sp)
        Spacer(modifier = Modifier.weight(1f))
        Text(text = value, color = Color(0xFF808080), fontSize = 14.sp)
    }
}

@Composable
private fun SettingsDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = Color(0xFF1A1A2E),
        thickness = 1.dp
    )
}
