package com.exynix.studio.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exynix.studio.ui.components.*
import com.exynix.studio.ui.theme.*
import com.exynix.studio.viewmodel.ChatMessageUi
import com.exynix.studio.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(vm: MainViewModel) {
    val messages by vm.chatMessages.collectAsState()
    val uiState by vm.uiState.collectAsState()
    val stats by vm.activeStats.collectAsState()
    val cpuTemp by vm.cpuTemp.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface0)
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Surface(
            color = Surface1,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExynNixLogo(compact = true)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ThermalIndicator(cpuTemp)
                        if (uiState.isGenerating) {
                            IconButton(onClick = { vm.cancelGeneration() }) {
                                Icon(Icons.Default.Stop, "Stop",
                                     tint = ExRed, modifier = Modifier.size(20.dp))
                            }
                        } else {
                            IconButton(onClick = { vm.clearChat() }) {
                                Icon(Icons.Default.Delete, "Clear",
                                     tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Stats bar
                if (!uiState.isModelLoaded) {
                    Text(
                        "⚠ Load a model in the Models tab to start chatting",
                        color = ExOrange,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else if (stats != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InlineStatChip("%.1f t/s".format(stats!!.genTps), ExBlue)
                        InlineStatChip(stats!!.backendUsed, ExGreen)
                        InlineStatChip("TTFT ${stats!!.ttftMs}ms", ExOrange)
                    }
                }
            }
        }

        // ── Messages ──────────────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    EmptyState(uiState.isModelLoaded)
                }
            }

            items(messages, key = { it.id }) { msg ->
                MessageBubble(msg)
            }
        }

        // ── Input bar ─────────────────────────────────────────────────────────
        Surface(color = Surface1, tonalElevation = 4.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .imePadding(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (uiState.isModelLoaded) "Message the model..." else "Load a model first",
                            color = TextDisabled, fontSize = 14.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ExBlue,
                        unfocusedBorderColor = Divider,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = ExBlue,
                        containerColor = Surface3
                    ),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 4,
                    enabled = uiState.isModelLoaded && !uiState.isGenerating
                )

                val canSend = uiState.isModelLoaded && !uiState.isGenerating && inputText.isNotBlank()

                FilledIconButton(
                    onClick = {
                        if (canSend) {
                            vm.sendMessage(inputText.trim())
                            inputText = ""
                            scope.launch { listState.animateScrollToItem(messages.size) }
                        }
                    },
                    enabled = canSend,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = ExBlue,
                        disabledContainerColor = Surface3
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    if (uiState.isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = TextPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (canSend) MaterialTheme.colorScheme.onPrimary else TextDisabled
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessageUi) {
    val isUser = msg.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ExBlue),
                contentAlignment = Alignment.Center
            ) {
                Text("E9", color = Surface0, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                     fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                color = if (isUser) ExBlue.copy(alpha = 0.2f) else Surface2,
                shape = RoundedCornerShape(
                    topStart = if (isUser) 14.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 14.dp,
                    bottomStart = 14.dp,
                    bottomEnd = 14.dp
                ),
                border = BorderStroke(
                    0.5.dp,
                    if (isUser) ExBlue.copy(alpha = 0.3f) else Divider
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (msg.isStreaming && msg.content.isEmpty()) {
                        StreamingDots()
                    } else {
                        Text(
                            msg.content,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                        if (msg.isStreaming) {
                            Spacer(Modifier.height(4.dp))
                            StreamingDots()
                        }
                    }
                }
            }

            // Stats under assistant messages
            if (!isUser && msg.stats != null) {
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("%.1f t/s".format(msg.stats.genTps), color = ExBlue,
                         fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(msg.stats.backendUsed, color = ExGreen,
                         fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("${msg.stats.genTokens} tok", color = TextDisabled,
                         fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modelLoaded: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExynNixLogo()
        Spacer(Modifier.height(8.dp))
        Text(
            if (modelLoaded) "Model loaded — start chatting" else "No model loaded",
            color = TextSecondary,
            fontSize = 14.sp
        )
        if (modelLoaded) {
            Text(
                "Inference runs on your Exynos 990 — fully offline",
                color = TextDisabled,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun InlineStatChip(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = color,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
    }
}
