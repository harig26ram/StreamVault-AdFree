package com.freedomplay.app.presentation.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Update
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.Coil
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
    val volumeNormalization by viewModel.volumeNormalization.collectAsStateWithLifecycle()
    val pipedInstanceUrl by viewModel.pipedInstanceUrl.collectAsStateWithLifecycle()

    var showQualityMenu by remember { mutableStateOf(false) }
    var showDownloadQualityMenu by remember { mutableStateOf(false) }
    var showDebugLogs by remember { mutableStateOf(false) }
    var showCrashLogs by remember { mutableStateOf(false) }
    var showClearLogsConfirm by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var editingPipedUrl by remember { mutableStateOf(pipedInstanceUrl) }
    var showUpdateDialog by remember { mutableStateOf(false) }

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

    if (showClearLogsConfirm) {
        ConfirmDialog(
            title = "Clear Logs",
            message = "Are you sure you want to delete all debug and crash logs? This cannot be undone.",
            onConfirm = {
                CrashLogger.clearLogs(context)
                Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                showClearLogsConfirm = false
            },
            onDismiss = { showClearLogsConfirm = false }
        )
    }

    if (showClearCacheConfirm) {
        ConfirmDialog(
            title = "Clear Cache",
            message = "Clear Coil image cache and app temporary files? Downloaded videos will not be affected.",
            onConfirm = {
                try {
                    Coil.imageLoader(context).memoryCache?.clear()
                } catch (_: Exception) {}
                try {
                    context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                } catch (_: Exception) {}
                Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                showClearCacheConfirm = false
            },
            onDismiss = { showClearCacheConfirm = false }
        )
    }

    if (showUpdateDialog) {
        UpdateCheckDialog(
            currentVersion = versionName,
            onDismiss = { showUpdateDialog = false }
        )
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
                color = MaterialTheme.colorScheme.onSurface,
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
                SettingsDivider()
                SettingsToggleRow(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "Volume Normalization",
                    subtitle = "Normalize audio volume across videos",
                    checked = volumeNormalization,
                    onCheckedChange = { viewModel.setVolumeNormalization(it) }
                )
            }
        }

        // Playback Section
        item {
            SettingsSectionHeader(icon = Icons.Default.PlayCircle, title = "Playback")
            SettingsCard {
                Box {
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
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        listOf("Auto", "480p", "720p", "1080p", "2160p", "Max").forEach { quality ->
                            DropdownMenuItem(
                                text = { Text(quality, color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    viewModel.setDefaultQuality(quality)
                                    showQualityMenu = false
                                }
                            )
                        }
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
                Box {
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
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        listOf("144p", "240p", "360p", "480p", "720p", "1080p").forEach { quality ->
                            DropdownMenuItem(
                                text = { Text(quality, color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    viewModel.setDefaultDownloadQuality(quality)
                                    showDownloadQualityMenu = false
                                }
                            )
                        }
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Storage Path",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                        Text(
                            text = downloadPath,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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

        // Network / API Section
        item {
            SettingsSectionHeader(icon = Icons.Default.Cloud, title = "Network")
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Piped Instance",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "API endpoint for video streams",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editingPipedUrl,
                        onValueChange = { editingPipedUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        placeholder = {
                            Text("https://pipedapi.kavin.rocks/", fontSize = 12.sp)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (editingPipedUrl != pipedInstanceUrl) {
                            TextButton(onClick = {
                                editingPipedUrl = pipedInstanceUrl
                            }) {
                                Text("Reset", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val url = editingPipedUrl.trimEnd('/')
                                    viewModel.setPipedInstanceUrl("$url/")
                                    Toast.makeText(context, "Piped instance updated", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }

        // Storage / Cache Section
        item {
            SettingsSectionHeader(icon = Icons.Default.Storage, title = "Storage")
            SettingsCard {
                SettingsClickableRow(
                    icon = Icons.Default.Storage,
                    title = "Clear Cache",
                    subtitle = "Free up space used by images and temp files"
                ) {
                    showClearCacheConfirm = true
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
                    showClearLogsConfirm = true
                }
            }
        }

        // About Section
        item {
            SettingsSectionHeader(icon = Icons.Default.Info, title = "About")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                SettingsInfoRow(label = "Version", value = versionName)
                SettingsDivider()
                SettingsInfoRow(label = "Build", value = if (com.freedomplay.app.BuildConfig.DEBUG) "Debug" else "Release")
                SettingsDivider()
                SettingsClickableRow(
                    icon = Icons.Default.SystemUpdate,
                    title = "Check for Updates",
                    subtitle = "View latest release on GitHub"
                ) {
                    showUpdateDialog = true
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
        text = { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
private fun UpdateCheckDialog(
    currentVersion: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Check for Updates", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text(
                    text = "Current version: $currentVersion",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap below to view the latest release on GitHub.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/freedomplay/StreamVault-AdFree/releases"))
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Cannot open browser", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Open GitHub")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
private fun DebugLogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val logs = remember { CrashLogger.getDebugLogs(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Debug Logs", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            SelectionContainer {
                Text(
                    text = logs.ifEmpty { "No logs yet" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Icon(Icons.Default.CopyAll, "Copy", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Close", color = MaterialTheme.colorScheme.primary)
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
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Crash Reports", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            SelectionContainer {
                Text(
                    text = crashLogs.ifEmpty { "No crashes recorded" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Icon(Icons.Default.CopyAll, "Copy", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Close", color = MaterialTheme.colorScheme.primary)
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
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            )
            Text(
                text = when (theme) {
                    ThemeType.AMOLED -> "Pure black background, hot pink accents"
                    ThemeType.GRADIENT -> "Deep purple tones with gradient feel"
                    ThemeType.MATERIAL3 -> "Standard Material 3 dark theme"
                    ThemeType.LIGHT -> "Light theme for daytime use"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Text(text = label, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
        Spacer(modifier = Modifier.weight(1f))
        Text(text = value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
private fun SettingsDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outline,
        thickness = 1.dp
    )
}
