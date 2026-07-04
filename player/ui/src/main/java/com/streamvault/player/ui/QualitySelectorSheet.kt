package com.streamvault.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PlayerFormat(
    val itag: Int,
    val label: String,
    val resolution: String?,
    val isAudioOnly: Boolean,
    val isVideoOnly: Boolean,
    val isSelected: Boolean = false
)

@Composable
fun QualitySelectorSheet(
    visible: Boolean,
    formats: List<PlayerFormat>,
    currentFormat: PlayerFormat?,
    onFormatSelected: (PlayerFormat) -> Unit,
    onDismiss: () -> Unit
) {
    BottomSheetContainer(
        visible = visible,
        title = "Video Quality",
        onDismiss = onDismiss
    ) {
        val autoFormats = formats.filter { !it.isAudioOnly && !it.isVideoOnly }
        val videoOnlyFormats = formats.filter { it.isVideoOnly }
        val audioFormats = formats.filter { it.isAudioOnly }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            if (autoFormats.isNotEmpty()) {
                item {
                    SectionHeader("Auto")
                }
                autoFormats.forEach { format ->
                    item {
                        FormatItem(format = format, current = currentFormat, onClick = { onFormatSelected(format) })
                    }
                }
                item { HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp)) }
            }

            if (videoOnlyFormats.isNotEmpty()) {
                item {
                    SectionHeader("Video Only")
                }
                videoOnlyFormats.forEach { format ->
                    item {
                        FormatItem(format = format, current = currentFormat, onClick = { onFormatSelected(format) })
                    }
                }
                item { HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp)) }
            }

            if (audioFormats.isNotEmpty()) {
                item {
                    SectionHeader("Audio Only")
                }
                audioFormats.forEach { format ->
                    item {
                        FormatItem(format = format, current = currentFormat, onClick = { onFormatSelected(format) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun FormatItem(
    format: PlayerFormat,
    current: PlayerFormat?,
    onClick: () -> Unit
) {
    val isSelected = current?.itag == format.itag

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = format.label,
                color = if (isSelected) Color(0xFFE11D48) else Color.White,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            if (format.resolution != null) {
                Text(
                    text = format.resolution,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color(0xFFE11D48)
            )
        }
    }
}
