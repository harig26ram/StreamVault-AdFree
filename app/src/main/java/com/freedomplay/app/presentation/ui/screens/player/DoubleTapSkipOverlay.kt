package com.freedomplay.app.presentation.ui.screens.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val SKIP_INDICATOR_DURATION_MS = 500L

@Composable
fun DoubleTapSkipOverlay(
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    modifier: Modifier = Modifier
) {
    var skipDirection by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val middleX = size.width / 2f
                        if (offset.x < middleX) {
                            skipDirection = "backward"
                            onSkipBackward()
                        } else {
                            skipDirection = "forward"
                            onSkipForward()
                        }
                    }
                )
            }
    ) {
        if (skipDirection != null) {
            val alpha by animateFloatAsState(
                targetValue = if (skipDirection != null) 1f else 0f,
                animationSpec = tween(300),
                label = "skipAlpha"
            )
            Box(
                modifier = Modifier
                    .align(
                        if (skipDirection == "backward") Alignment.CenterStart
                        else Alignment.CenterEnd
                    )
                    .padding(24.dp)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .alpha(alpha),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (skipDirection == "backward")
                        Icons.Default.Replay10 else Icons.Default.Forward10,
                    contentDescription = "Skip ${skipDirection?.replace("ward", "")} 10s",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            LaunchedEffect(skipDirection) {
                delay(SKIP_INDICATOR_DURATION_MS)
                skipDirection = null
            }
        }
    }
}
