package com.streamvault.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlayerSeekBar(
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPercent: Int,
    onSeek: (Long) -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }

    val progress = if (durationMs > 0) {
        (if (isDragging) dragPositionMs else currentPositionMs.toFloat()) / durationMs.toFloat()
    } else 0f

    val positionText = formatTime(if (isDragging) dragPositionMs.toLong() else currentPositionMs)
    val durationText = formatTime(durationMs)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val width = size.width.toFloat()
                        val tappedProgress = (offset.x / width).coerceIn(0f, 1f)
                        onSeekStart()
                        onSeek((tappedProgress * durationMs).toLong())
                        onSeekEnd()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            onSeekStart()
                            isDragging = true
                        },
                        onDragEnd = {
                            isDragging = false
                            onSeek(dragPositionMs.toLong())
                            onSeekEnd()
                        },
                        onDragCancel = {
                            isDragging = false
                            onSeekEnd()
                        },
                        onDrag = { change, _ ->
                            val width = size.width.toFloat()
                            val tappedProgress = (change.position.x / width).coerceIn(0f, 1f)
                            dragPositionMs = tappedProgress * durationMs
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.Center)
            ) {
                val barHeight = size.height
                val barWidth = size.width

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.2f),
                    cornerRadius = CornerRadius(barHeight / 2),
                    size = Size(barWidth, barHeight)
                )

                val bufferedWidth = barWidth * (bufferedPercent / 100f).coerceIn(0f, 1f)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.35f),
                    cornerRadius = CornerRadius(barHeight / 2),
                    size = Size(bufferedWidth, barHeight)
                )

                val progressWidth = barWidth * progress.coerceIn(0f, 1f)
                drawRoundRect(
                    color = Color(0xFFE11D48),
                    cornerRadius = CornerRadius(barHeight / 2),
                    size = Size(progressWidth, barHeight)
                )

                val thumbRadius = 6.dp.toPx()
                drawCircle(
                    color = Color(0xFFE11D48),
                    radius = thumbRadius,
                    center = Offset(progressWidth.coerceAtLeast(thumbRadius), barHeight / 2)
                )
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius * 0.6f,
                    center = Offset(progressWidth.coerceAtLeast(thumbRadius), barHeight / 2)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = positionText,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = durationText,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
