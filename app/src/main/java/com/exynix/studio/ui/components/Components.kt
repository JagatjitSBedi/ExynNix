package com.exynix.studio.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exynix.studio.data.models.InferenceBackend
import com.exynix.studio.ui.theme.*

// ── Backend chip ──────────────────────────────────────────────────────────────

@Composable
fun BackendChip(backend: InferenceBackend, modifier: Modifier = Modifier) {
    val color = Color(backend.color)
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(0.5.dp, color.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = backend.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ── HW capability badge ───────────────────────────────────────────────────────

@Composable
fun CapabilityBadge(label: String, enabled: Boolean, color: Color, modifier: Modifier = Modifier) {
    val activeColor = if (enabled) color else TextDisabled
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) color.copy(alpha = 0.12f) else Surface3)
            .border(0.5.dp, activeColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(activeColor)
        )
        Text(
            text = label,
            color = activeColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ── Stats row ─────────────────────────────────────────────────────────────────

@Composable
fun StatItem(label: String, value: String, color: Color = TextSecondary) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold,
             fontFamily = FontFamily.Monospace)
        Text(label, color = TextSecondary, fontSize = 10.sp)
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp
    )
}

// ── Glowing progress bar ──────────────────────────────────────────────────────

@Composable
fun GlowingProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    label: String = ""
) {
    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, color = TextSecondary, fontSize = 11.sp)
                Text("${(progress * 100).toInt()}%", color = color,
                     fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(4.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Surface3)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(color.copy(alpha = 0.7f), color)
                        )
                    )
            )
        }
    }
}

// ── Thermal indicator ─────────────────────────────────────────────────────────

@Composable
fun ThermalIndicator(tempC: Float) {
    val color = when {
        tempC > 83f -> ExRed
        tempC > 70f -> ExOrange
        tempC > 50f -> ExGreen
        else -> ExBlue
    }
    val label = when {
        tempC > 83f -> "THROTTLING"
        tempC > 70f -> "WARM"
        tempC > 0f -> "NORMAL"
        else -> "--"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val pulse = rememberInfiniteTransition(label = "thermal")
        val alpha by pulse.animateFloat(
            initialValue = 0.5f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "alpha"
        )
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = if (tempC > 70f) alpha else 1f))
        )
        Text(
            text = if (tempC > 0) "%.1f°C".format(tempC) else "--",
            color = color,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
        Text(label, color = color.copy(alpha = 0.7f), fontSize = 9.sp,
             fontWeight = FontWeight.Medium)
    }
}

// ── ExynNix Logo Mark ─────────────────────────────────────────────────────────

@Composable
fun ExynNixLogo(compact: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
    ) {
        // Logo mark — stylized "E9" for Exynos 990
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = ExBlue,
            modifier = Modifier.size(if (compact) 24.dp else 32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "E9",
                    color = Color.Black,
                    fontSize = if (compact) 9.sp else 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        if (!compact) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    "ExynNix",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Exynos 990 · On-Device AI",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// ── Dot stream animation (streaming indicator) ─────────────────────────────────

@Composable
fun StreamingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    val dot1 by transition.animateFloat(
        0.2f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "d1"
    )
    val dot2 by transition.animateFloat(
        0.2f, 1f, infiniteRepeatable(tween(500, delayMillis = 150), RepeatMode.Reverse), label = "d2"
    )
    val dot3 by transition.animateFloat(
        0.2f, 1f, infiniteRepeatable(tween(500, delayMillis = 300), RepeatMode.Reverse), label = "d3"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(dot1, dot2, dot3).forEach { a ->
            Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(ExBlue.copy(alpha = a)))
        }
    }
}
