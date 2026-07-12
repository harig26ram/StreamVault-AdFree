package com.streamvault.app.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val MGreen = Color(0xFF22C55E)
private val MDarkGreen = Color(0xFF0A2E14)
private val MOrange = Color(0xFFE8813B)

@Composable
fun MTricolorDivider(
    modifier: Modifier = Modifier,
    height: Dp = 3.dp
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MGreen)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MDarkGreen)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MOrange)
        )
    }
}
