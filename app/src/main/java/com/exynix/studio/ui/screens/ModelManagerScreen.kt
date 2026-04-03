package com.exynix.studio.ui.screens

import androidx.compose.animation.*
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
fun ModelManagerScreen(vm: MainViewModel, onNavigateToChat: () -> Unit) {
    val models by vm.models.collectAsState()
    val uiState by vm.uiState.collectAsState()
    var selectedBackend by remember { mutableStateOf(InferenceBackend.AUTO) }
    var expandedModelId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface0),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Models", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${models.size} available",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Backend selector
        item {
            SectionHeader("Default Backend")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(InferenceBackend.entries.toList()) { b ->
                    val selected = selectedBackend == b
                    Surface(
                        modifier = Modifier.clickable { selectedBackend = b },
                        color = if (selected) Color(b.color).copy(alpha = 0.2f) else Surface2,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            if (selected) 1.dp else 0.5.dp,
                            if (selected) Color(b.color) else Divider
                        )
                    ) {
                        Text(
                            b.label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = if (selected) Color(b.color) else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        item { SectionHeader("Model Catalog — S20 Ultra Optimized") }

        items(models) { model ->
            ModelCard(
                model = model,
                isLoaded = uiState.loadedModelId == model.id,
                isExpanded = expandedModelId == model.id,
                downloadProgress = uiState.downloadProgress?.takeIf { it.first == model.id }?.second,
                selectedBackend = selectedBackend,
                onExpand = {
                    expandedModelId = if (expandedModelId == model.id) null else model.id
                },
                onDownload = { vm.downloadModel(model.id) },
                onLoad = {
                    vm.loadModel(model.id, selectedBackend)
                },
                onUnload = { vm.unloadModel() },
                onChat = onNavigateToChat
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun ModelCard(
    model: ModelEntry,
    isLoaded: Boolean,
    isExpanded: Boolean,
    downloadProgress: Float?,
    selectedBackend: InferenceBackend,
    onExpand: () -> Unit,
    onDownload: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onChat: () -> Unit
) {
    val status = ModelStatus.valueOf(model.status)
    val hasLocal = model.localPath.isNotEmpty()
    val preferredBackend = InferenceBackend.entries.firstOrNull { it.name == model.preferredBackend }
        ?: InferenceBackend.AUTO

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onExpand),
        color = if (isLoaded) ExBlue.copy(alpha = 0.08f) else Surface1,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            if (isLoaded) 1.dp else 0.5.dp,
            if (isLoaded) ExBlue.copy(alpha = 0.5f) else Divider
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            model.name,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        if (isLoaded) {
                            Surface(color = ExBlue.copy(0.2f), shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    "LOADED",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = ExBlue,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(model.paramCount, color = TextSecondary, fontSize = 12.sp)
                        Text("·", color = TextDisabled, fontSize = 12.sp)
                        Text(model.quantization, color = ExGreen, fontSize = 12.sp,
                             fontFamily = FontFamily.Monospace)
                        Text("·", color = TextDisabled, fontSize = 12.sp)
                        Text(model.format, color = ExOrange, fontSize = 12.sp,
                             fontFamily = FontFamily.Monospace)
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatSize(model.sizeBytes),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    BackendChip(preferredBackend)
                }
            }

            // Download progress
            if (downloadProgress != null) {
                Spacer(Modifier.height(8.dp))
                GlowingProgressBar(
                    progress = downloadProgress,
                    color = ExBlue,
                    label = "Downloading"
                )
            }

            // Status row
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(status, hasLocal)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        isLoaded -> {
                            OutlinedButton(
                                onClick = onUnload,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.5.dp, TextSecondary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Unload", color = TextSecondary, fontSize = 12.sp)
                            }
                            Button(
                                onClick = onChat,
                                colors = ButtonDefaults.buttonColors(containerColor = ExBlue),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Chat, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Chat", fontSize = 12.sp)
                            }
                        }
                        hasLocal -> {
                            Button(
                                onClick = onLoad,
                                colors = ButtonDefaults.buttonColors(containerColor = ExGreen),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Load", fontSize = 12.sp)
                            }
                        }
                        downloadProgress == null -> {
                            OutlinedButton(
                                onClick = onDownload,
                                border = BorderStroke(0.5.dp, ExBlue),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Download, null, Modifier.size(14.dp), tint = ExBlue)
                                Spacer(Modifier.width(4.dp))
                                Text("Download", color = ExBlue, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Expanded details
            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Divider(color = Divider)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        model.description.ifEmpty { "High-quality GGUF model for on-device inference on Exynos 990." },
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                    if (model.downloadUrl.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Source: ${model.downloadUrl.substringAfter("resolve/main/")}",
                            color = TextDisabled,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                    if (model.benchGenTps > 0) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            StatItem("t/s", "%.1f".format(model.benchGenTps), ExBlue)
                            StatItem("TTFT", "${model.benchTtftMs}ms", ExOrange)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ModelStatus, hasLocal: Boolean) {
    val (text, color) = when {
        hasLocal && status == ModelStatus.READY -> "READY" to ExGreen
        status == ModelStatus.DOWNLOADING -> "DOWNLOADING" to ExBlue
        status == ModelStatus.RUNNING -> "RUNNING" to ExOrange
        status == ModelStatus.ERROR -> "ERROR" to ExRed
        else -> "NOT DOWNLOADED" to TextDisabled
    }
    Text(
        text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace
    )
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0)
        else -> "-- GB"
    }
}
