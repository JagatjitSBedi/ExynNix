package com.exynix.studio.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
fun DashboardScreen(vm: MainViewModel, onNavigateToChat: () -> Unit, onNavigateToModels: () -> Unit) {
    val profile by vm.hardwareProfile.collectAsState()
    val cpuTemp by vm.cpuTemp.collectAsState()
    val uiState by vm.uiState.collectAsState()
    val models by vm.models.collectAsState()
    val stats by vm.activeStats.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface0),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExynNixLogo()
                ThermalIndicator(cpuTemp)
            }
        }

        // ── SoC overview card ──────────────────────────────────────────────────
        item {
            SocCard(profile)
            Spacer(Modifier.height(12.dp))
        }

        // ── Capability grid ────────────────────────────────────────────────────
        item {
            SectionHeader("Hardware Capabilities")
            CapabilityGrid(profile)
            Spacer(Modifier.height(12.dp))
        }

        // ── Active model card ──────────────────────────────────────────────────
        if (uiState.isModelLoaded) {
            item {
                SectionHeader("Active Model")
                ActiveModelCard(
                    modelId = uiState.loadedModelId,
                    models = models,
                    stats = stats,
                    onChat = onNavigateToChat
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        // ── Quick actions ──────────────────────────────────────────────────────
        item {
            SectionHeader("Quick Actions")
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Default.Chat,
                    label = "Chat",
                    color = ExBlue,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToChat
                )
                QuickActionCard(
                    icon = Icons.Default.Memory,
                    label = "Models",
                    color = ExGreen,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToModels
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── Last inference stats ───────────────────────────────────────────────
        if (stats != null) {
            item {
                SectionHeader("Last Inference")
                InferenceStatsCard(stats!!)
            }
        }
    }
}

@Composable
private fun SocCard(profile: HardwareProfile?) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = Surface1,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        profile?.socName ?: "Samsung Exynos 990",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        profile?.deviceModel ?: "Galaxy S20 Ultra",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Surface(
                    color = ExBlue.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "arm64-v8a",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = ExBlue,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Divider(color = Divider)
            Spacer(Modifier.height(12.dp))

            // CPU cluster info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ClusterInfo("BIG", "2×A77", "2.73 GHz", ExRed)
                ClusterInfo("MID", "2×A77", "2.50 GHz", ExOrange)
                ClusterInfo("LIT", "4×A55", "2.00 GHz", ExGreen)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "GPU: Mali-G77 MP11",
                    color = ExBlue,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "RAM: ${profile?.cpu?.totalRamMb?.div(1024) ?: 12} GB LPDDR5",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(8.dp))

            // Recommended backend
            profile?.let { p ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Best:", color = TextSecondary, fontSize = 11.sp)
                    val b = InferenceBackend.entries.firstOrNull { it.name == p.recommendedBackend }
                        ?: InferenceBackend.AUTO
                    BackendChip(b)
                }
            }
        }
    }
}

@Composable
private fun ClusterInfo(tag: String, cores: String, freq: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tag, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(cores, color = TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Text(freq, color = TextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun CapabilityGrid(profile: HardwareProfile?) {
    val p = profile ?: HardwareProfile()
    val caps = listOf(
        Triple("CPU XNNPACK", true, ExGreen),
        Triple("GPU Vulkan", p.gpu.available, ExBlue),
        Triple("NNAPI GPU", p.nnapi.hasGpu, ExPurple),
        Triple("NNAPI NPU", p.nnapi.hasNpu, ExOrange),
        Triple("FP16", p.cpu.hasFp16, ExCyan),
        Triple("DotProd", p.cpu.hasDotprod, ExCyan),
        Triple("NEON", true, ExGreen),
        Triple("MMAP", true, TextSecondary)
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(caps) { (label, enabled, color) ->
            CapabilityBadge(label, enabled, color)
        }
    }
}

@Composable
private fun ActiveModelCard(
    modelId: String?,
    models: List<ModelEntry>,
    stats: InferenceStats?,
    onChat: () -> Unit
) {
    val model = models.firstOrNull { it.id == modelId }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = Surface2,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, ExBlue.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    model?.name ?: "Unknown Model",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${model?.paramCount} · ${model?.quantization} · ${model?.format}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                if (stats != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "%.1f t/s · ${stats.backendUsed}".format(stats.genTps),
                        color = ExGreen,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Button(
                onClick = onChat,
                colors = ButtonDefaults.buttonColors(containerColor = ExBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Chat")
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(28.dp))
            Text(label, color = color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun InferenceStatsCard(stats: InferenceStats) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = Surface1,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("t/s gen", "%.1f".format(stats.genTps), ExBlue)
            StatItem("t/s prompt", "%.1f".format(stats.promptTps), ExGreen)
            StatItem("TTFT", "${stats.ttftMs}ms", ExOrange)
            StatItem("tokens", "${stats.genTokens}", TextSecondary)
        }
    }
}
