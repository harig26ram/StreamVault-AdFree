package com.freedomplay.app.presentation.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplay.app.util.TimeUtils

@Composable
fun BottomPlayerControls(
    duration: Float,
    currentPosition: Float,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onMaxQuality: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val durationSeconds = if (duration > 0f) duration else 1f

    var isDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(currentPosition) }

    if (!isDragging) {
        sliderValue = currentPosition
    }

    val elapsedTime = TimeUtils.formatDuration(sliderValue.toLong())
    val totalTime = TimeUtils.formatDuration(durationSeconds.toLong())

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = elapsedTime,
                    color = Color.White,
                    fontSize = 12.sp
                )

                Slider(
                    value = sliderValue,
                    onValueChange = { value ->
                        isDragging = true
                        sliderValue = value
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        onSeek(sliderValue)
                    },
                    valueRange = 0f..durationSeconds,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )

                Text(
                    text = totalTime,
                    color = Color.White,
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSkipBackward) {
                    Icon(
                        imageVector = Icons.Filled.Replay10,
                        contentDescription = "Skip backward 10s",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = onSkipForward) {
                    Icon(
                        imageVector = Icons.Filled.Forward10,
                        contentDescription = "Skip forward 10s",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = onMaxQuality) {
                    Icon(
                        imageVector = Icons.Filled.HighQuality,
                        contentDescription = "Max quality",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        imageVector = Icons.Filled.Fullscreen,
                        contentDescription = "Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
