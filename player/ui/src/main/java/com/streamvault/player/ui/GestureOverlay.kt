package com.streamvault.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class GestureSide { LEFT, RIGHT }

private data class GestureIndicator(
    val visible: Boolean,
    val icon: ImageVector,
    val label: String
)

@Composable
fun GestureOverlay(
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onVerticalSwipe: (delta: Float, side: GestureSide) -> Unit,
    modifier: Modifier = Modifier
) {
    var indicator by remember { mutableStateOf(GestureIndicator(false, Icons.Default.FastRewind, "")) }
    var horizontalSwipeDistance by remember { mutableFloatStateOf(0f) }
    var lastHorizontalSwipeSide by remember { mutableStateOf<GestureSide?>(null) }

    LaunchedEffect(indicator.visible) {
        if (indicator.visible) {
            delay(800)
            indicator = indicator.copy(visible = false)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { onDoubleTap() }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (horizontalSwipeDistance > 300f) {
                            if (lastHorizontalSwipeSide == GestureSide.RIGHT) {
                                onSwipeRight()
                            } else {
                                onSwipeLeft()
                            }
                        }
                        horizontalSwipeDistance = 0f
                        lastHorizontalSwipeSide = null
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        horizontalSwipeDistance += dragAmount
                        lastHorizontalSwipeSide = if (dragAmount > 0) GestureSide.RIGHT else GestureSide.LEFT
                        indicator = GestureIndicator(
                            visible = true,
                            icon = if (dragAmount > 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                            label = if (dragAmount > 0) "Forward" else "Rewind"
                        )
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        val side = if (change.position.x < size.width / 2) GestureSide.LEFT else GestureSide.RIGHT
                        onVerticalSwipe(dragAmount, side)
                        indicator = GestureIndicator(
                            visible = true,
                            icon = if (side == GestureSide.LEFT) Icons.Default.BrightnessHigh else Icons.Default.VolumeUp,
                            label = if (side == GestureSide.LEFT) "Brightness" else "Volume"
                        )
                    }
                )
            }
    ) {
        AnimatedVisibility(
            visible = indicator.visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Icon(
                    imageVector = indicator.icon,
                    contentDescription = indicator.label,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = indicator.label,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
