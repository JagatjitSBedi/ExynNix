package com.exynix.studio.ui.screens

import androidx.compose.foundation.background
import kotlin.ranges.ClosedFloatingPointRange
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exynix.studio.ui.components.SectionHeader
import com.exynix.studio.ui.theme.*
import com.exynix.studio.viewmodel.MainViewModel

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val config = vm.inferenceConfig

    var temperature by remember { mutableStateOf(config.temperature) }
    var topP by remember { mutableStateOf(config.topP) }
    var maxTokens by remember { mutableStateOf(config.maxNewTokens.toFloat()) }
    var nCtx by remember { mutableStateOf(config.nCtx.toFloat()) }
    var nGpuLayers by remember { mutableStateOf(config.nGpuLayers.toFloat()) }
    var systemPrompt by remember { mutableStateOf(config.systemPrompt) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface0),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Settings", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        item {
            SectionHeader("Generation Parameters")
            SettingsCard {
                SliderSetting(
                    label = "Temperature",
                    value = temperature,
                    onValueChange = {
                        temperature = it
                        vm.updateConfig(temperature = it)
                    },
                    valueRange = 0f..2f,
                    formatValue = { "%.2f".format(it) },
                    description = "Higher = more creative, lower = more deterministic"
                )
                Divider(color = Divider)
                SliderSetting(
                    label = "Top-P",
                    value = topP,
                    onValueChange = {
                        topP = it
                        vm.updateConfig(topP = it)
                    },
                    valueRange = 0f..1f,
                    formatValue = { "%.2f".format(it) },
                    description = "Nucleus sampling — 0.9 recommended"
                )
                Divider(color = Divider)
                SliderSetting(
                    label = "Max New Tokens",
                    value = maxTokens,
                    onValueChange = {
                        maxTokens = it
                        vm.updateConfig(maxNewTokens = it.toInt())
                    },
                    valueRange = 64f..2048f,
                    steps = 30,
                    formatValue = { it.toInt().toString() },
                    description = "Maximum tokens per response"
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        item {
            SectionHeader("Context & Memory")
            SettingsCard {
                SliderSetting(
                    label = "Context Length (n_ctx)",
                    value = nCtx,
                    onValueChange = {
                        nCtx = it
                        vm.updateConfig(nCtx = it.toInt())
                    },
                    valueRange = 512f..4096f,
                    steps = 6,
                    formatValue = { it.toInt().toString() },
                    description = "Larger = more memory. 2048 recommended for S20 Ultra"
                )
                Divider(color = Divider)
                SliderSetting(
                    label = "GPU Layers (n_gpu_layers)",
                    value = nGpuLayers,
                    onValueChange = {
                        nGpuLayers = it
                        vm.updateConfig(nGpuLayers = it.toInt())
                    },
                    valueRange = -1f..35f,
                    steps = 35,
                    formatValue = { if (it.toInt() == -1) "Auto" else it.toInt().toString() },
                    description = "-1 = auto-detect. Higher = more layers on Mali-G77"
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        item {
            SectionHeader("System Prompt")
            SettingsCard {
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = {
                        systemPrompt = it
                        vm.updateConfig(systemPrompt = it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ExBlue,
                        unfocusedBorderColor = Divider,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = ExBlue
),
                    shape = RoundedCornerShape(10.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        item {
            SectionHeader("Exynos 990 Hints")
            SettingsCard {
                HintRow("Optimal thread count: 4 (big+mid cluster)", ExGreen)
                Divider(color = Divider)
                HintRow("GPU layers for 3B Q4_K_M: 30-35 (full GPU)", ExBlue)
                Divider(color = Divider)
                HintRow("GPU layers for 7B Q4_K_M: 20-24 (split CPU+GPU)", ExBlue)
                Divider(color = Divider)
                HintRow("Avoid mlock on Exynos 990 — causes OOM with 12 GB RAM", ExOrange)
                Divider(color = Divider)
                HintRow("Thermal throttle at ~83°C — watch the top-right indicator", ExRed)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = Surface1,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    formatValue: (Float) -> String,
    description: String = ""
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = TextPrimary, fontSize = 13.sp)
            Text(
                formatValue(value),
                color = ExBlue,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = ExBlue,
                activeTrackColor = ExBlue,
                inactiveTrackColor = Surface3
            )
        )
        if (description.isNotEmpty()) {
            Text(description, color = TextDisabled, fontSize = 11.sp)
        }
    }
}

@Composable
private fun HintRow(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(5.dp)
                .background(color, RoundedCornerShape(3.dp))
                .padding(top = 5.dp)
        )
        Text(text, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
    }
}
