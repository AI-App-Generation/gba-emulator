package com.retropocket.gba.emulator.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/* ---------- Neon Arcade Dark ----------
 * App chỉ có dark theme. Chiều sâu tạo bằng độ sáng nền + dropShadow, không dùng viền dày.
 */

// Nền phân tầng
val BgRoot = Color(0xFF080B14)
val BgSurface = Color(0xFF111726)
val BgSurfaceHigh = Color(0xFF1A2233)
val BgSurfacePress = Color(0xFF232C40)

// Neon accent
val NeonCyan = Color(0xFF22E6FF)
val NeonCyanDim = Color(0xFF0FA8BF)

/** Mép sáng của nút chính — chỉ dùng cho gradient/viền, không dùng làm màu chữ. */
val CtaHighlight = Color(0xFF9DF7FF)
val NeonMagenta = Color(0xFFFF3D9A)
val NeonAmber = Color(0xFFFFC53D)
val NeonGreen = Color(0xFF4AE08A)
val NeonRed = Color(0xFFFF5A5A)

// Text
val TextPrimary = Color(0xFFF2F5FF)
val TextSecondary = Color(0xFF98A3BD)
val TextTertiary = Color(0xFF5C6780)

// Viền
val BorderSubtle = Color(0xFF212A3D)
val BorderStrong = Color(0xFF33405C)

// Overlay
val ScrimHeavy = Color(0xCC05070E)

/** Phủ chân ảnh bìa để chữ luôn đọc được trên mọi ảnh. */
val ScrimGradient = Brush.verticalGradient(
    listOf(Color.Transparent, Color(0xE60A0E1A))
)

/**
 * Màu định danh theo hệ máy — dùng cho badge và gradient placeholder khi thiếu ảnh bìa.
 * Giúp user quét mắt nhận ra hệ máy mà không cần đọc chữ.
 */
object SystemColors {
    val GBA = Color(0xFF7C5CFF)
    val GB = Color(0xFF9BBC0F)
    val GBC = Color(0xFFFF7A45)
    val NES = Color(0xFFE23C3C)
    val SNES = Color(0xFF8B6CE0)
    val GENESIS = Color(0xFF3D8BFF)
    val SMS = Color(0xFF00B3A4)
    val GG = Color(0xFF00C2A8)
    val PSX = Color(0xFFB0B8C9)
    val N64 = Color(0xFF35C759)
    val NDS = Color(0xFF00A6FF)
    val ATARI = Color(0xFFD46A2C)
    val UNKNOWN = Color(0xFF5C6780)
}
