package com.retropocket.gba.emulator.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.retropocket.gba.emulator.R

/**
 * Typography 2 tầng:
 *  - PressStart2P (pixel) chỉ cho heading ngắn / nhãn / số liệu — đọc nhiều sẽ mỏi mắt.
 *  - Font hệ thống cho mọi đoạn text dài.
 */
val PressStart2P = FontFamily(Font(R.font.press_start_2p))

// Pixel — dùng tiết chế
val DisplayPixel = TextStyle(
    fontFamily = PressStart2P,
    fontSize = 18.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.5.sp,
)

val TitlePixel = TextStyle(
    fontFamily = PressStart2P,
    fontSize = 12.sp,
    lineHeight = 18.sp,
)

val LabelPixel = TextStyle(
    fontFamily = PressStart2P,
    fontSize = 9.sp,
    lineHeight = 14.sp,
)

// Font hệ thống — nội dung đọc được
val TitleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
val TitleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
val BodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
val BodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal)
val Caption = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium)

val Typography = Typography(
    bodyLarge = BodyMedium,
    bodyMedium = BodySmall,
)
