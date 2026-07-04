package com.streamvault.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlaybackSpeedSheet(
    visible: Boolean,
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f, 4.0f)
    var showCustomInput by remember { mutableStateOf(false) }
    var customSpeedText by remember { mutableStateOf("") }

    BottomSheetContainer(
        visible = visible,
        title = "Playback Speed",
        onDismiss = onDismiss
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(speeds.size) { index ->
                val speed = speeds[index]
                val isSelected = speed == currentSpeed
                SpeedItem(
                    speed = speed,
                    isSelected = isSelected,
                    onClick = {
                        onSpeedSelected(speed)
                        showCustomInput = false
                    }
                )
            }
            item {
                SpeedItem(
                    speed = null,
                    isSelected = false,
                    isCustom = true,
                    onClick = { showCustomInput = !showCustomInput }
                )
            }
        }

        if (showCustomInput) {
            // Custom speed input would go here in a full implementation
            // For now, just select a default custom speed
        }

        // Simpler approach: just a grid of speed buttons
    }
}

@Composable
private fun SpeedItem(
    speed: Float?,
    isSelected: Boolean,
    isCustom: Boolean = false,
    onClick: () -> Unit
) {
    val label = if (isCustom) "Custom" else "${speed}x"
    val bgColor = if (isSelected) Color(0xFFE11D48) else Color(0xFF1A1A2E)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}
