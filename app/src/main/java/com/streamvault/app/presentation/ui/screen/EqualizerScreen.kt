package com.streamvault.app.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.streamvault.app.presentation.viewmodel.EqualizerViewModel

private val AmoledBlack = Color(0xFF000000)
private val SurfaceDark = Color(0xFF1A1A1A)
private val HotPink = Color(0xFFFF4081)
private val SliderInactive = Color(0xFF333333)
private val GrayText = Color(0xFFB0B0B0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Equalizer",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            actions = {
                Text(
                    text = if (uiState.enabled) "ON" else "OFF",
                    color = if (uiState.enabled) HotPink else GrayText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Switch(
                    checked = uiState.enabled,
                    onCheckedChange = { viewModel.setEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = HotPink,
                        checkedTrackColor = HotPink.copy(alpha = 0.3f),
                        uncheckedThumbColor = GrayText,
                        uncheckedTrackColor = SliderInactive
                    )
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = AmoledBlack
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (uiState.presets.isNotEmpty()) {
                PresetSelector(
                    presets = uiState.presets,
                    selectedIndex = uiState.presetIndex,
                    onSelect = { viewModel.setPreset(it) }
                )
            }

            if (uiState.numberOfBands > 0) {
                BandSliders(
                    numberOfBands = uiState.numberOfBands,
                    bandLevels = uiState.bandLevels,
                    minLevel = uiState.minBandLevel,
                    maxLevel = uiState.maxBandLevel,
                    frequencies = uiState.bandFrequencies,
                    enabled = uiState.enabled,
                    onLevelChange = { band, level -> viewModel.setBandLevel(band, level) }
                )
            }

            HorizontalDivider(color = SliderInactive)

            BassBoostSection(
                value = uiState.bassBoost,
                enabled = uiState.enabled,
                onValueChange = { viewModel.setBassBoost(it) }
            )

            VirtualizerSection(
                value = uiState.virtualizer,
                enabled = uiState.enabled,
                onValueChange = { viewModel.setVirtualizer(it) }
            )

            HorizontalDivider(color = SliderInactive)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.reset() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = HotPink
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(HotPink.copy(alpha = 0.5f))
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset")
                }

                Button(
                    onClick = { onBack() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HotPink,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun PresetSelector(
    presets: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Preset",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = GrayText
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEachIndexed { index, name ->
                val isSelected = index == selectedIndex
                Surface(
                    onClick = { onSelect(index) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) HotPink.copy(alpha = 0.15f) else SurfaceDark,
                    border = if (isSelected) ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(HotPink)
                    ) else null,
                    modifier = Modifier.height(36.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Text(
                            text = name,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) HotPink else GrayText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BandSliders(
    numberOfBands: Int,
    bandLevels: Map<Short, Short>,
    minLevel: Short,
    maxLevel: Short,
    frequencies: List<String>,
    enabled: Boolean,
    onLevelChange: (Short, Short) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Bands",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = GrayText
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            for (i in 0 until numberOfBands) {
                val band = i.toShort()
                val level = bandLevels[band] ?: 0
                val freq = frequencies.getOrElse(i) { "Band ${i + 1}" }
                val normalizedValue = if (maxLevel != minLevel) {
                    (level.toFloat() - minLevel.toFloat()) / (maxLevel.toFloat() - minLevel.toFloat())
                } else 0.5f

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    val dBValue = level / 100
                    Text(
                        text = "${if (dBValue > 0) "+" else ""}${dBValue}dB",
                        fontSize = 8.sp,
                        color = if (level != 0.toShort()) HotPink else GrayText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .width(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Slider(
                            value = normalizedValue,
                            onValueChange = { newValue ->
                                val newLevel = (newValue * (maxLevel - minLevel) + minLevel).toInt().toShort()
                                onLevelChange(band, newLevel)
                            },
                            valueRange = 0f..1f,
                            enabled = enabled,
                            colors = SliderDefaults.colors(
                                thumbColor = HotPink,
                                activeTrackColor = HotPink,
                                inactiveTrackColor = SliderInactive,
                                disabledThumbColor = GrayText.copy(alpha = 0.5f),
                                disabledActiveTrackColor = HotPink.copy(alpha = 0.3f),
                                disabledInactiveTrackColor = SliderInactive.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .width(180.dp)
                                .graphicsLayer {
                                    rotationZ = -90f
                                    translationX = size.height / 2 - size.width / 2
                                    translationY = size.width / 2 - size.height / 2
                                }
                        )
                    }

                    Text(
                        text = freq,
                        fontSize = 8.sp,
                        color = GrayText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BassBoostSection(
    value: Short,
    enabled: Boolean,
    onValueChange: (Short) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bass Boost",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = "$value",
                fontSize = 13.sp,
                color = if (value > 0) HotPink else GrayText
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().toShort()) },
            valueRange = 0f..1000f,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = HotPink,
                activeTrackColor = HotPink,
                inactiveTrackColor = SliderInactive,
                disabledThumbColor = GrayText.copy(alpha = 0.5f),
                disabledActiveTrackColor = HotPink.copy(alpha = 0.3f),
                disabledInactiveTrackColor = SliderInactive.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun VirtualizerSection(
    value: Short,
    enabled: Boolean,
    onValueChange: (Short) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Virtualizer",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = "$value",
                fontSize = 13.sp,
                color = if (value > 0) HotPink else GrayText
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().toShort()) },
            valueRange = 0f..1000f,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = HotPink,
                activeTrackColor = HotPink,
                inactiveTrackColor = SliderInactive,
                disabledThumbColor = GrayText.copy(alpha = 0.5f),
                disabledActiveTrackColor = HotPink.copy(alpha = 0.3f),
                disabledInactiveTrackColor = SliderInactive.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
