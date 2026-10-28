package com.retropocket.gba.emulator.features.iap

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Giá và thao tác mua cho màn paywall.
 *
 * `IapController.registerPurchaseListener()` đã chạy một lần lúc app khởi động nên ở đây chỉ cần
 * tra giá.
 */
class IapViewModel : ViewModel() {

    /** Giá lấy thẳng từ Controller nên mở lại paywall là có số ngay, không chớp một nhịp trống. */
    val prices = IapController.prices

    /**
     * Hỏi giá cho tới khi đủ cả 3 gói, tối đa [MAX_ATTEMPTS] lần.
     *
     * Vì sao phải thử lại chứ không hỏi một lần: billing client kết nối với Google Play theo kiểu
     * bất đồng bộ, mở paywall ngay sau khi bật app thì lần hỏi đầu gần như chắc chắn rỗng. App
     * cũng đã đăng ký `registerBillingListener()` để nạp giá khi billing sẵn sàng — vòng lặp này
     * là lớp phòng hờ cho trường hợp tín hiệu đó tới trước khi màn hình được mở.
     */
    fun loadPrices() {
        viewModelScope.launch {
            repeat(MAX_ATTEMPTS) {
                if (IapController.refreshPrices().containsAll(Iap.all)) return@launch
                delay(RETRY_DELAY_MS)
            }
        }
    }

    fun buy(activity: Activity, productId: String) {
        IapController.buy(activity, productId)
    }

    private companion object {
        const val MAX_ATTEMPTS = 20
        const val RETRY_DELAY_MS = 300L
    }
}
