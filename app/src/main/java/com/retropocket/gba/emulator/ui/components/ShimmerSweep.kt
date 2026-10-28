package com.retropocket.gba.emulator.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

/**
 * Vệt sáng hình bình hành chạy ngang qua phần tử — dùng để làm nổi gói "đáng mua nhất".
 *
 * Tự vẽ thay vì dùng thư viện shimmer vì ở đây chỉ cần đúng một vệt trên một thẻ, kéo cả thư
 * viện về là thừa. Vệt nghiêng bằng cách trượt cạnh trên sang phải một đoạn [skew] × chiều cao,
 * nên chiều cao thẻ đổi thì độ nghiêng vẫn giữ nguyên góc.
 *
 * Đặt SAU `clip(shape)` trong chuỗi modifier, nếu không vệt sáng sẽ tràn ra ngoài bo góc.
 */
@Composable
fun Modifier.shimmerSweep(
    color: Color = Color.White,
    alpha: Float = 0.22f,
    /** Bề ngang vệt sáng, tính theo phần trăm bề ngang phần tử. */
    widthFraction: Float = 0.18f,
    skew: Float = 0.45f,
    periodMillis: Int = 2400,
    /** Khoảng nghỉ giữa 2 lần quét — quét liên tục không ngừng sẽ gây rối mắt. */
    pauseMillis: Int = 1400,
): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val total = periodMillis + pauseMillis
    // Chạy tiến trình 0..1 trên TOÀN chu kỳ rồi tự cắt: phần sau khoảng quét cho vệt ra hẳn ngoài
    // khung, tạo quãng nghỉ mà không cần lồng thêm animation thứ hai.
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(total, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )

    return drawWithContent {
        drawContent()

        val sweep = (progress * total / periodMillis).coerceAtMost(1f)
        val band = size.width * widthFraction
        val lean = size.height * skew
        // Đi từ ngoài mép trái tới ngoài mép phải để hai đầu vệt không bị "mọc ra" giữa thẻ.
        val x = -band - lean + sweep * (size.width + band * 2 + lean)

        val path = Path().apply {
            moveTo(x, size.height)
            lineTo(x + lean, 0f)
            lineTo(x + lean + band, 0f)
            lineTo(x + band, size.height)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, color.copy(alpha = alpha), Color.Transparent),
                startX = x,
                endX = x + lean + band,
            ),
        )
    }
}
