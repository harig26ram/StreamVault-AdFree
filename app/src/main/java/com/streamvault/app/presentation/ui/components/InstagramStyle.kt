package com.streamvault.app.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** Brand tricolor gradient (pink → purple → cyan). */
fun brandGradient(vertical: Boolean = false): Brush {
    val colors = listOf(
        Color(0xFFFF4081),
        Color(0xFF2B115A),
        Color(0xFF008AC9)
    )
    return if (vertical) Brush.verticalGradient(colors) else Brush.horizontalGradient(colors)
}

/** Soft vertical gradient overlay for top app bars (tinted brand color → transparent). */
@Composable
fun TopBarGradientOverlay(
    height: Dp = 110.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        Color.Transparent
                    )
                )
            )
    )
}

/** Gentle Instagram-like entry animation (fade + subtle scale-in). */
@Composable
fun AppearAnim(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = true,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
            scaleIn(
                initialScale = 0.96f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
            )
    ) {
        content()
    }
}

/**
 * Circular channel avatar wrapped in a brand-colored gradient ring.
 * Optionally shows a "new content" dot (brand red with white plus).
 */
@Composable
fun BrandRingAvatar(
    model: Any?,
    contentDescription: String?,
    size: Dp = 56.dp,
    ringWidth: Dp = 2.5.dp,
    showDot: Boolean = false,
    onClick: () -> Unit = {}
) {
    val dotSize = (size * 0.30f).coerceAtLeast(12.dp)
    Box(
        modifier = Modifier
            .size(size + ringWidth * 2)
            .clip(CircleShape)
            .background(brandGradient(false))
            .clickable(onClick = onClick)
            .padding(ringWidth),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(size * 0.5f)
                )
            }
        }

        if (showDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(dotSize)
                    .background(Color(0xFFF11A22), CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(dotSize * 0.7f)
                )
            }
        }
    }
}
