package com.retropocket.gba.emulator.features.iap

import android.app.Activity
import android.util.Log
import com.bbl.ads.modules.billing.AppPurchase
import com.bbl.ads.modules.function.BillingListener
import com.bbl.ads.modules.function.PurchaseListener
import com.bbl.ads.modules.function.UpdatePurchaseListener
import com.retropocket.gba.emulator.ads.AdsLoader
import com.retropocket.gba.emulator.core.Analytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Đầu mối mua hàng: đăng ký listener, mua gói, lấy giá.
 *
 * Khác app mẫu ở một điểm: app này có CẢ gói mua đứt, và **cả 3 gói đều tắt quảng cáo**, nên khi
 * mua thành công thì tắt ads bất kể là subscription hay in-app.
 */
object IapController {

    private val _prices = MutableStateFlow<Map<String, String>>(emptyMap())

    /**
     * Giá hiển thị, cập nhật dần khi Google Play trả về.
     *
     * Là StateFlow chứ không phải giá trị đọc một lần: lúc mở paywall, billing client có thể chưa
     * kịp tải xong chi tiết sản phẩm, và khi nó xong thì giao diện phải tự hiện giá ra.
     */
    val prices: StateFlow<Map<String, String>> = _prices.asStateFlow()

    fun cachedPrices(): Map<String, String> = _prices.value

    /**
     * Gọi một lần lúc khởi động, NGAY SAU `AppPurchase.initBilling()`.
     *
     * Giá chỉ có sau khi billing client kết nối xong với Play, nên phải chờ đúng tín hiệu đó rồi
     * nạp giá — không thể hỏi ngay sau initBilling().
     */
    fun registerBillingListener() {
        AppPurchase.getInstance().setBillingListener(object : BillingListener {
            override fun onInitBillingFinished(resultCode: Int) {
                Log.d(TAG, "Billing ready, code=$resultCode")
                // Quyền lợi KHÔNG được lưu trên máy nên phải dựng lại từ câu trả lời của Play.
                // Trước thời điểm này isPurchased luôn là false, kể cả với user đang trả phí.
                AdsLoader.refreshPurchaseState()
                warmUpPrices()
            }
        })

        // Play làm mới danh sách mua (gia hạn, huỷ, hoàn tiền, khôi phục trên máy mới) thì đồng
        // bộ lại — đây cũng là đường để quyền lợi TỰ MẤT khi gói hết hạn.
        AppPurchase.getInstance().setUpdatePurchaseListener(object : UpdatePurchaseListener {
            override fun onUpdateFinished() {
                if (!AppPurchase.getInstance().initBillingFinish) return
                AdsLoader.refreshPurchaseState()
            }
        })
    }

    /**
     * Nạp giá ngay từ lúc khởi động, lặp lại cho tới khi đủ cả 3 gói.
     *
     * Không chờ tới lúc user mở paywall: `onInitBillingFinished` báo billing client đã kết nối,
     * nhưng chi tiết sản phẩm còn được tải tiếp sau đó một nhịp. Nạp sẵn ở đây thì paywall mở ra
     * là có giá luôn, không phải nhìn dấu gạch ngang.
     */
    fun warmUpPrices() {
        if (isWarmingUp) return
        isWarmingUp = true
        scope.launch {
            repeat(WARM_UP_ATTEMPTS) { attempt ->
                val known = refreshPrices()
                if (known.containsAll(Iap.all)) {
                    Log.d(TAG, "Prices ready after ${attempt + 1} attempt(s): ${_prices.value}")
                    isWarmingUp = false
                    return@launch
                }
                delay(WARM_UP_DELAY_MS)
            }
            isWarmingUp = false
            // Hết lượt mà vẫn thiếu: gần như luôn là bản cài không khớp với bản trên Play
            // (khác chữ ký hoặc chưa có bản nào trên track nào), chứ không phải lỗi mã nguồn.
            Log.w(
                TAG,
                "Gave up fetching prices. Have=${_prices.value.keys}, want=${Iap.all}, " +
                    "billingAvailable=${AppPurchase.getInstance().isAvailable}, " +
                    "initFinished=${AppPurchase.getInstance().initBillingFinish}",
            )
        }
    }

    fun registerPurchaseListener() {
        AppPurchase.getInstance().setPurchaseListener(object : PurchaseListener {
            override fun onProductPurchased(productId: String?, transactionDetails: String?) {
                Log.d(TAG, "Success: $productId | $transactionDetails")
                if (productId == null || productId !in Iap.all) return

                // Tắt ads ngay trong phiên, không đợi Play làm mới. setActive() bật PremiumState,
                // kéo theo: mở khoá toàn bộ game, bỏ giới hạn slot, ẩn mọi lối mời mua.
                AdsLoader.setActive(isPurchased = true)

                // Kèm id gói: không có nó thì chỉ đếm được tổng lượt mua, không biết gói nào bán
                // được — mà đó mới là thứ quyết định chỉnh giá.
                Analytics.log("iap_purchased", "product_id" to productId)
            }

            override fun displayErrorMessage(errorMsg: String?) {
                Log.e(TAG, "Error: $errorMsg")
            }

            override fun onUserCancelBilling() {
                Log.d(TAG, "User canceled")
            }
        })
    }

    fun buy(activity: Activity, productId: String) {
        if (Iap.isSubscription(productId)) {
            AppPurchase.getInstance().subscribe(activity, productId, 0)
        } else {
            AppPurchase.getInstance().purchase(activity, productId)
        }
    }

    /**
     * Hỏi lại giá từ Google Play và trả về danh sách id ĐÃ có giá.
     *
     * Chỉ ghi đè những id lấy được: một lần hỏi hụt không được phép xoá mất giá đang hiển thị.
     */
    fun refreshPrices(productIds: List<String> = Iap.all): Set<String> {
        val fetched = productIds.mapNotNull { id ->
            priceOf(id).takeIf { it.isNotBlank() }?.let { id to it }
        }.toMap()

        if (fetched.isNotEmpty()) {
            _prices.value = _prices.value + fetched
        }
        return _prices.value.keys
    }

    /**
     * Số ngày dùng thử miễn phí thật của một gói, đọc từ chu kỳ giá Google Play trả về.
     *
     * Trả `null` khi Play CHƯA trả lời, khác hẳn với `0` nghĩa là Play đã trả lời và gói không
     * có dùng thử. Phân biệt hai trường hợp này để nút mua không đổi nhãn loạn xạ trong lúc chờ,
     * mà cũng không hứa một bản dùng thử Play sẽ không cấp.
     */
    fun trialDays(productId: String): Int? = runCatching {
        val phases = AppPurchase.getInstance().getPricePricingPhaseList(productId)
        if (phases.isNullOrEmpty()) return null
        val free = phases.firstOrNull { it.priceAmountMicros == 0L } ?: return 0
        isoPeriodToDays(free.billingPeriod)
    }.getOrNull()

    /** Đổi chu kỳ ISO-8601 của Play (P3D, P1W, P1M) sang số ngày. */
    private fun isoPeriodToDays(period: String?): Int {
        val match = Regex("""P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?""")
            .matchEntire(period.orEmpty()) ?: return 0
        val (y, m, w, d) = match.destructured
        return (y.toIntOrNull() ?: 0) * 365 +
            (m.toIntOrNull() ?: 0) * 30 +
            (w.toIntOrNull() ?: 0) * 7 +
            (d.toIntOrNull() ?: 0)
    }

    private fun priceOf(productId: String): String = if (Iap.isSubscription(productId)) {
        AppPurchase.getInstance().getPriceSub(productId)
    } else {
        AppPurchase.getInstance().getPrice(productId)
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var isWarmingUp = false

    private const val WARM_UP_ATTEMPTS = 40
    private const val WARM_UP_DELAY_MS = 500L
    private const val TAG = "IAP"
}
