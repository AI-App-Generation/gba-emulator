package com.retropocket.gba.emulator.features.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Nguồn sự thật duy nhất cho câu hỏi "user có đang trả phí không".
 *
 * CỐ Ý chỉ giữ trong bộ nhớ, không ghi xuống đĩa: gói tuần/tháng là subscription và có thể hết
 * hạn, lưu một cờ true vĩnh viễn thì hết hạn rồi app vẫn mở khoá mọi thứ. Mỗi lần mở app,
 * [com.retropocket.gba.emulator.ads.AdsLoader] hỏi lại Google Play rồi set lại giá trị này;
 * trong phiên thì `IapController` set ngay khi mua xong.
 *
 * Nó chi phối: ẩn quảng cáo, mở khoá toàn bộ game, và bỏ giới hạn sức chứa thư viện
 * ([com.retropocket.gba.emulator.core.AppCapability]).
 */
object PremiumState {

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    fun set(premium: Boolean) {
        _isPremium.value = premium
    }
}
