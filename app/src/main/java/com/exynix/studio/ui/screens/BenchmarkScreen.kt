package com.exynix.studio.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exynix.studio.data.models.*
import com.exynix.studio.ui.components.*
import com.exynix.studio.ui.theme.*
import com.exynix.studio.viewmodel.MainViewModel

@Composable
fun BenchmarkScreen(vm: MainViewModel) {
    val uiState by vm.uiState.collectAsState()
    val profile by vm.hardwareProfile.collectAsState()
    val cpuTemp by vm.cpuTemp.collectAsState()
    val results by vm.benchmarkResults.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Surface0),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Benchmark", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                ThermalIndicator(cpuTemp)
            }
        }

        // Device spec summary
        item {
            DeviceSpecCard(profile)
            Spacer(Modifier.height(12.dp))
        }

        // Run benchmark button
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = Surface1,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Inference Benchmark", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (uiState.isModelLoaded) "Requires loaded model · 64 token generation"
                        else "Load a model first to run inference benchmark",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { vm.runBenchmark() },
                        enabled = uiState.isModelLoaded && !uiState.isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = ExBlue),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(Icons.Default.Speed, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (uiState.isLoading) "Running..." else "Run Benchmark")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Results
        if (results.isNotEmpty()) {
            item { SectionHeader("Results") }
            items(results) { result ->
                BenchmarkResultCard(result)
                Spacer(Modifier.height(8.dp))
            }
        }

        // S20 Ultra reference table
        item {
            Spacer(Modifier.height(8.dp))
            SectionHeader("S20 Ultra Exynos 990 — Expected Range")
            ReferenceTable()
        }
    }
}

@Composable
private fun DeviceSpecCard(profile: HardwareProfile?) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = Surface1,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Device", color = TextSecondary, fontSize = 11.sp, letterSpacing = 1.sp)
            Text(profile?.deviceModel ?: "Galaxy S20 Ultra", color = TextPrimary,
                 fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Divider(color = Divider)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SpecRow("SoC", profile?.socName ?: "Exynos 990")
                SpecRow("RAM", "${(profile?.cpu?.totalRamMb ?: 12288) / 1024} GB LPDDR5")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SpecRow("CPU", "8-core ARMv8.2-A")
                SpecRow("GPU", "Mali-G77 MP11")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SpecRow("Vulkan", if (profile?.gpu?.available == true) "1.1 ✓" else "Probing...")
                SpecRow("NNAPI", if (profile?.nnapi?.available == true) "Available" else "CPU ref only")
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Column {
        Text(label, color = TextSecondary, fontSize = 10.sp)
        Text(value, color = TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun BenchmarkResultCard(result: BenchmarkResult) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = Surface2,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, ExBlue.copy(0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(result.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(result.backend, color = TextSecondary, fontSize = 12.sp)
                Text("${result.timeMs}ms total", color = TextDisabled, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "%.1f t/s".format(result.score),
                    color = ExBlue,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text("gen speed", color = TextSecondary, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ReferenceTable() {
    val rows = listOf(
        listOf("Model", "Backend", "Expected t/s"),
        listOf("Gemma 2 2B Q4", "CPU XNNPACK", "15-25"),
        listOf("Llama 3.2 3B Q4", "GPU Vulkan", "8-18"),
        listOf("Phi-3 Mini Q4", "GPU Vulkan", "7-15"),
        listOf("Llama 3 8B Q4", "GPU Vulkan", "4-10"),
        listOf("Qwen 2.5 1.5B Q8", "CPU XNNPACK", "20-35"),
    )

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = Surface1,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            rows.forEachIndexed { idx, row ->
                val isHeader = idx == 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isHeader) Modifier.background(Surface2, RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    row.forEachIndexed { col, cell ->
                        Text(
                            cell,
                            modifier = Modifier.weight(if (col == 0) 1.5f else 1f),
                            color = when {
                                isHeader -> TextSecondary
                                col == 2 -> ExGreen
                                else -> TextPrimary
                            },
                            fontSize = if (isHeader) 10.sp else 12.sp,
                            fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
                            fontFamily = if (col == 2) FontFamily.Monospace else FontFamily.Default,
                            letterSpacing = if (isHeader) 0.8.sp else 0.sp
                        )
                    }
                }
                if (idx < rows.size - 1 && !isHeader) {
                    Divider(color = Divider, thickness = 0.5.dp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "* Thermal throttling may reduce peak performance. Keep room temp low.",
                color = TextDisabled, fontSize = 10.sp
            )
        }
    }
}
