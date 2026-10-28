package com.retropocket.gba.emulator.core

import com.retropocket.gba.emulator.features.common.PremiumState

/**
 * Sức chứa của bản miễn phí, tính bằng **slot mở khoá**.
 *
 * Bản free cho cài bao nhiêu game tuỳ thích — thư viện hiện đủ hết — nhưng chỉ mở khoá được
 * [FREE_UNLOCK_SLOTS] game. Hết slot thì không mở thêm, kể cả xem quảng cáo thưởng; muốn nữa thì
 * mua gói. Xoá một game đã mở khoá sẽ trả lại slot.
 *
 * Game mẫu đóng gói sẵn KHÔNG tốn slot vì user không chọn chúng — tính vào thì vừa cài app đã hết
 * sạch chỗ.
 *
 * Gom về một chỗ thay vì rải hằng số: mọi nơi hỏi cùng một nguồn, và sau này đổi số hay đổi luật
 * thì chỉ sửa ở đây.
 */
object AppCapability {

    const val FREE_UNLOCK_SLOTS = 5

    fun isUnlimited(): Boolean = PremiumState.isPremium.value

    /** Còn mở khoá được bao nhiêu game nữa; bản trả phí trả về [Int.MAX_VALUE]. */
    fun remainingSlots(usedSlots: Int): Int = when {
        isUnlimited() -> Int.MAX_VALUE
        else -> (FREE_UNLOCK_SLOTS - usedSlots).coerceAtLeast(0)
    }

    fun canUnlockMore(usedSlots: Int): Boolean = remainingSlots(usedSlots) > 0
}
