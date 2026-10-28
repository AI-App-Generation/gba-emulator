package com.retropocket.gba.emulator.core

/**
 * Điểm cấu hình tập trung cho các nguồn nội dung bên ngoài.
 *
 * Cố ý tách riêng thay vì rải hằng số trong màn hình: 2 URL này là thứ dễ phải đổi nhất
 * (đổi kho, kho bị gỡ, chuyển sang remote config) nên gom về một chỗ.
 */
object AppConfig {

    /** Kho game mở trong WebView của app. */
    const val STORE_URL = "https://ngocbichday.github.io/gbaweb-gamebackup/"

    /** Kho bên ngoài, mở bằng trình duyệt hệ thống (không nhúng WebView). */
    const val EXTERNAL_STORE_URL = "https://romsgames.net"

    const val EXTERNAL_STORE_LABEL = "romsgames.net"

    /** Chính sách bảo mật — bắt buộc phải truy cập được từ trong app khi lên Google Play. */
    const val PRIVACY_POLICY_URL =
        "https://sites.google.com/view/retropocket-gbaemulator/privacy-policy"

    /**
     * Điều khoản dịch vụ — hiện trỏ tạm về trang chính sách vì chưa có trang riêng.
     * Paywall bắt buộc phải có link này; thay bằng URL thật khi trang được dựng xong.
     */
    const val TERMS_OF_SERVICE_URL = PRIVACY_POLICY_URL
}
