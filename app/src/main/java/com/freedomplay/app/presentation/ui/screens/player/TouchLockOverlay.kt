package com.freedomplay.app.presentation.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val UNLOCK_HINT_TIMEOUT_MS = 3_000L

/**
 * Full-screen touch lock for the player (MX Player / YouTube style). While locked it
 * swallows every pointer event so nothing underneath -- including the WebView-hosted
 * player and Compose controls -- can be triggered by accidental touches. Tapping the
 * screen briefly reveals an unlock pill; tapping the pill calls [onUnlock].
 */
@Composable
fun TouchLockOverlay(
    isLocked: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isLocked) return

    var showUnlockHint by remember { mutableStateOf(false) }
    var revealTick by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    // Keep the screen awake while locked; clear the flag on unlock/dispose.
    DisposableEffect(isLocked) {
        val window = context.findActivityFromContext()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Auto-hide the unlock hint a few seconds after the most recent tap.
    LaunchedEffect(revealTick) {
        if (showUnlockHint) {
            delay(UNLOCK_HINT_TIMEOUT_MS)
            showUnlockHint = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    showUnlockHint = true
                    revealTick++
                    // Swallow the rest of the gesture so nothing underneath reacts.
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = showUnlockHint,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            UnlockPill(onClick = onUnlock)
        }
    }
}

@Composable
private fun UnlockPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "Unlock screen",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "Tap to unlock",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun Context.findActivityFromContext(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
