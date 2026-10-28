package com.retropocket.gba.emulator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ArcadeColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = BgRoot,
    secondary = NeonMagenta,
    tertiary = NeonAmber,
    background = BgRoot,
    onBackground = TextPrimary,
    surface = BgSurface,
    onSurface = TextPrimary,
    surfaceVariant = BgSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = BorderStrong,
    error = NeonRed,
    onError = Color.White,
)

/**
 * App cố định dark theme — emulator retro luôn hiển thị trên nền tối để khung game nổi bật
 * và letterbox đen của core hoà liền với nền, không tạo viền chói.
 */
@Composable
fun GameEmulatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ArcadeColorScheme,
        typography = Typography,
        content = content,
    )
}
