package com.retropocket.gba.emulator.features.iap

/**
 * Id sản phẩm trên Google Play Console. Cả 3 gói đều tắt quảng cáo — xem khai báo
 * `adFreeSubscriptionProductIds` / `adFreeInAppProductIds` trong GlobalApp.
 */
object Iap {
    /** Gói tuần — subscription. */
    const val weeklyId: String = "weekly_51k"

    /** Gói tháng — subscription. */
    const val monthlyId: String = "monthly_129k"

    /** Gói vĩnh viễn — mua đứt một lần (in-app product, không phải subscription). */
    const val lifetimeId: String = "lifetime_229k"

    val subscriptions = listOf(weeklyId, monthlyId)
    val inAppProducts = listOf(lifetimeId)
    val all = subscriptions + inAppProducts

    /**
     * Số ngày dùng thử đã cấu hình cho gói tuần trên Play Console.
     *
     * Chỉ là nhãn tạm cho lúc Google Play chưa kịp trả về chi tiết sản phẩm. Khi Play trả lời,
     * `IapController.trialDays()` sẽ thay bằng con số thật — kể cả khi con số thật là 0.
     */
    const val EXPECTED_TRIAL_DAYS: Int = 3

    fun isSubscription(productId: String): Boolean = productId in subscriptions
}
