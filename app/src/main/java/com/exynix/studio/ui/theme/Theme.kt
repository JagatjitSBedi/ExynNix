package com.exynix.studio.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── ExynNix Dark Theme ────────────────────────────────────────────────────────
// Dark-first, tech aesthetic inspired by Samsung's dark mode aesthetic.
// Accent: electric blue (0x2196F3) — matches Vulkan/GPU color coding.

val ExBlue       = Color(0xFF2196F3)    // Primary accent — GPU/Vulkan
val ExBlueLight  = Color(0xFF64B5F6)
val ExGreen      = Color(0xFF4CAF50)    // CPU/XNNPACK
val ExOrange     = Color(0xFFFF9800)    // NPU/NNAPI
val ExPurple     = Color(0xFF9C27B0)    // ONNX
val ExCyan       = Color(0xFF00BCD4)    // Auto
val ExRed        = Color(0xFFF44336)    // Error/thermal warning

val Surface0     = Color(0xFF0A0E1A)    // Main background (almost black)
val Surface1     = Color(0xFF101624)    // Card background
val Surface2     = Color(0xFF161D2E)    // Elevated card
val Surface3     = Color(0xFF1E2A40)    // Chip/input background

val TextPrimary  = Color(0xFFE8EBF4)
val TextSecondary= Color(0xFF8E9BB5)
val TextDisabled = Color(0xFF4A5568)
val Divider      = Color(0xFF1E2A40)

private val DarkColorScheme = darkColorScheme(
    primary          = ExBlue,
    onPrimary        = Color.Black,
    primaryContainer = Color(0xFF0D3F7A),
    onPrimaryContainer = ExBlueLight,
    secondary        = ExCyan,
    onSecondary      = Color.Black,
    tertiary         = ExGreen,
    onTertiary       = Color.Black,
    background       = Surface0,
    onBackground     = TextPrimary,
    surface          = Surface1,
    onSurface        = TextPrimary,
    surfaceVariant   = Surface2,
    onSurfaceVariant = TextSecondary,
    error            = ExRed,
    onError          = Color.White,
    outline          = Divider,
)

@Composable
fun ExynNixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
