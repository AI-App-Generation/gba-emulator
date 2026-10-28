package com.retropocket.gba.emulator.ads

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.bbl.ads.modules.AdsManager
import com.bbl.ads.modules.billing.AppPurchase
import com.bbl.ads.modules.configs.RemoteConfigManager
import com.retropocket.gba.emulator.features.common.PremiumState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AdsLoader {
    private const val REMOTE_CONFIG_TIMEOUT_MS = 5_000L
    private const val ADS_READY_TIMEOUT_MS = 5_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var started = false

    private val _finished = MutableStateFlow(false)
    val finished = _finished.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive = _isActive.asStateFlow()

    fun setActive(isPurchased: Boolean) {
        _isActive.value = !isPurchased
        // Một nguồn sự thật duy nhất cho toàn app: ẩn quảng cáo, mở khoá game, bỏ giới hạn
        // sức chứa đều đọc từ PremiumState.
        PremiumState.set(isPurchased)
    }

    fun isAdsEnabled(): Boolean = _isActive.value

    /**
     * Trạng thái trả phí, hỏi thẳng Google Play.
     *
     * CỐ Ý không đọc cờ lưu trên máy: gói tuần/tháng hết hạn được, mà một cờ đã ghi xuống đĩa thì
     * không có gì xoá đi, nên user huỷ gói vẫn giữ quyền vĩnh viễn. Google Play mới là nơi biết
     * sự thật, và nó tự cache offline nên mất mạng vẫn trả lời được.
     *
     * Đổi lại, giá trị này chỉ đáng tin SAU khi billing client kết nối xong — trước đó luôn là
     * false. Vì vậy [IapController] gọi lại [setActive] khi billing sẵn sàng.
     */
    private fun isPurchased(): Boolean = AppPurchase.getInstance().isPurchased

    /** Đồng bộ lại quyền lợi khi Google Play có câu trả lời mới. */
    fun refreshPurchaseState() {
        setActive(isPurchased())
    }

    fun initEarly(context: Context) {
        val appContext = context.applicationContext

        if (started || _finished.value) return
        started = true

        waitForRemoteConfig(appContext)
    }

    private fun waitForRemoteConfig(context: Context) {
        var remoteReady = false

        RemoteConfigManager.getInstance(context)
            .addOnConfigReadyListener {
                if (remoteReady || _finished.value) return@addOnConfigReadyListener
                remoteReady = true
                onRemoteConfigReady(context)
            }

        mainHandler.postDelayed({
            if (remoteReady || _finished.value) return@postDelayed
            remoteReady = true
            onRemoteConfigReady(context)
        }, REMOTE_CONFIG_TIMEOUT_MS)
    }

    private fun onRemoteConfigReady(context: Context) {
        AdModeManager.syncFromRemoteConfig(context)
        if (isPurchased()) {
            setActive(true)
            _finished.value = true
            return
        }
        setActive(false)
        waitForAdsManager()
    }

    private fun waitForAdsManager() {

        if (_finished.value) return

        if (AdsManager.getInstance().isReady) {
            _finished.value = true
            return
        }

        AdsManager.getInstance().addOnReadyListener {
            if (_finished.value) return@addOnReadyListener
            _finished.value = true
            setActive(isPurchased())
        }

        mainHandler.postDelayed({
            if (_finished.value) return@postDelayed
            _finished.value = true
            setActive(isPurchased())
        }, ADS_READY_TIMEOUT_MS)
    }
}
