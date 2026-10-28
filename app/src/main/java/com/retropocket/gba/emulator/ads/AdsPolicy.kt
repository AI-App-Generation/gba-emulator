package com.retropocket.gba.emulator.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.bbl.ads.modules.AdsManager
import com.bbl.ads.modules.configs.RemoteConfigManager
import com.bbl.ads.modules.function.AdCallback
import com.retropocket.gba.emulator.core.SettingManager
import com.facebook.shimmer.ShimmerFrameLayout
import com.retropocket.gba.emulator.R
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AdModeManager {

    private val modeMap = ConcurrentHashMap<String, Int>()

    init {
        initDefaultModes()
    }

    private fun initDefaultModes() {
        AdsPlacementHolder::class.java.fields
            .filter { field ->
                field.type == String::class.java
            }
            .forEach { field ->
                val placement = field.get(null) as? String ?: return@forEach
                modeMap[placement] = 1
            }
    }

    fun syncFromRemoteConfig(context: Context) {
        val remote = RemoteConfigManager.getInstance(context)

        AdsPlacementHolder::class.java.fields
            .filter { field ->
                field.type == String::class.java
            }
            .forEach { field ->
                val placement = field.get(null) as? String ?: return@forEach

                val mode = try {
                    remote.getString(placement, "1")
                        ?.toIntOrNull() ?: 1
                } catch (_: Exception) {
                    1
                }

                modeMap[placement] = mode
            }
    }

    fun getMode(placement: String): Int {
        return modeMap[placement] ?: 1
    }

    fun isEnabled(placement: String): Boolean {
        return getMode(placement) != 0
    }
}

object AdsPolicy {
    /**
     * Khoảng cách tối thiểu giữa 2 inter KHÔNG force. Hiện chỉ inter_home rơi vào diện này.
     * Phải khớp với interAdInterval khai trong GlobalApp — đây mới là cửa chặn thật ở phía app,
     * còn interAdInterval là tham số của module-ads.
     */
    private const val INTERVAL = 20_000L

    @Volatile
    private var lastTimeShowedAt: Long = 0L

    @Volatile
    private var runtimeInterCount = 0

    @Volatile
    var allowShowWelcomeBack = true

    @Volatile
    var isShowingInterAd = false
        private set

    private val _isWaitingInterAd = MutableStateFlow(false)
    val isWaitingInterAd: StateFlow<Boolean> = _isWaitingInterAd.asStateFlow()

    private fun setShowingInter(showing: Boolean) {
        isShowingInterAd = showing
    }

    private fun setWaitingInterAd(waiting: Boolean) {
        _isWaitingInterAd.value = waiting
    }

    fun canShowInterAd(): Boolean = System.currentTimeMillis() - lastTimeShowedAt >= INTERVAL

    private fun resolvePlacement(placement: String): String {
        if (SettingManager.isFirstTime.value) return placement
        return when (placement) {
            AdsPlacementHolder.INTER_SPLASH -> AdsPlacementHolder.INTER_SPLASH_2
            AdsPlacementHolder.NATIVE_LANGUAGE_1_1 -> AdsPlacementHolder.NATIVE_LANGUAGE_1_2
            else -> placement
        }
    }

    private inline fun skipIfAdsInactive(onSkip: () -> Unit): Boolean {
        if (!AdsLoader.isAdsEnabled()) {
            onSkip()
            return true
        }
        return false
    }

    private inline fun skipIfDisabled(
        placement: String,
        onDisabled: () -> Unit
    ): Boolean {
        if (!AdModeManager.isEnabled(placement)) {
            onDisabled()
            return true
        }
        return false
    }

    /**
     * Quảng cáo thưởng.
     *
     * Ba kết cục tách bạch, vì hệ quả của chúng trái ngược nhau:
     *  - [onRewardGranted] xem hết, đáng được thưởng.
     *  - [onAdFailed] không hiện nổi (hết fill, remote config tắt) — lỗi của ta, phải có đường lui.
     *  - [onDismissedWithoutReward] user tự đóng giữa chừng — KHÔNG thưởng, nếu không thì mở lên
     *    đóng ngay là có quà và chẳng ai xem hết quảng cáo nữa.
     */
    fun showRewardAd(
        activity: Activity,
        adPlacement: String,
        onRewardGranted: () -> Unit,
        onAdFailed: () -> Unit = {},
        onDismissedWithoutReward: () -> Unit = {},
    ) {
        setShowingInter(true)
        setWaitingInterAd(true)

        AdsManager.getInstance().loadAndShow(activity, adPlacement, object : AdCallback {
            // SDK bắn onAdClosed cả sau khi đã thưởng và sau khi lỗi, nên phải lọc để chỉ kết cục
            // ĐẦU TIÊN được tính. Callback đều về main thread nên một cờ thường là đủ.
            private var settled = false

            /**
             * Dọn cờ rồi mới lọc: cờ phải sạch kể cả ở lần gọi thừa.
             * Bỏ sót đúng chỗ này là kẹt [isShowingInterAd] và chết cả màn Welcome Back.
             */
            private fun settle(outcome: () -> Unit) {
                setWaitingInterAd(false)
                setShowingInter(false)
                if (settled) return
                settled = true
                outcome()
            }

            override fun onAdLoaded(name: String) = setWaitingInterAd(false)

            override fun onRewardGranted(name: String) = settle(onRewardGranted)

            override fun onAdClosed(name: String) = settle(onDismissedWithoutReward)

            override fun onAdFailed(name: String, error: String) {
                Log.e("AdsLib", "$adPlacement: $error")
                settle(onAdFailed)
            }
        })
    }

    fun showInterAd(
        activity: Activity,
        adPlacement: String,
        onContinue: () -> Unit,
        delayTime: Long = 0,
        force: Boolean = false, // Show ngay lập tức, không áp dụng luật bên dưới
        showWaitingOverlay: Boolean = true,
    ) {
        val hasContinued = AtomicBoolean(false)
        val continueOnce = {
            if (hasContinued.compareAndSet(false, true)) {
                onContinue()
            }
        }

        if (skipIfAdsInactive { continueOnce() }) return

        val placement = resolvePlacement(adPlacement)

        val mode = AdModeManager.getMode(placement)
        if (mode == 0) {
            continueOnce()
            return
        }

        if (!force) {
            when (mode) {
                1 -> {
                    if (!canShowInterAd()) {
                        continueOnce()
                        return
                    }
                }
                2 -> {
                    runtimeInterCount++
                    if (runtimeInterCount % 2 != 1) {
                        continueOnce()
                        return
                    }
                }
            }
        }

        val useCooldownOnImpression = !force && mode == 1

        setShowingInter(true)
        if (showWaitingOverlay) setWaitingInterAd(true)
        AdsManager.getInstance()
            .loadAndShowWithTimeDelay(
                activity,
                placement,
                25000,
                delayTime,
                object : AdCallback {
                    override fun onAdLoaded(name: String) {
                        if (showWaitingOverlay) setWaitingInterAd(false)
                        Log.i("AdsLib", "$placement loaded")
                        loadNextAd(placement)
                    }

                    override fun onAdFailed(name: String, error: String) {
                        if (showWaitingOverlay) setWaitingInterAd(false)
                        setShowingInter(false)
                        Log.e("AdsLib", "$placement: $error")
                        continueOnce()
                        loadNextAd(placement)
                    }

                    override fun onAdClosed(name: String) {
                        if (showWaitingOverlay) setWaitingInterAd(false)
                        setShowingInter(false)
                        Log.i("AdsLib", "$placement closed")
                        continueOnce()
                    }

                    override fun onAdImpression(name: String) {
                        super.onAdImpression(name)
                        Log.i("AdsLib", "[Impression] $placement")
                        if (useCooldownOnImpression) {
                            lastTimeShowedAt = System.currentTimeMillis()
                        }
                    }
                }
            )
    }

    fun showAppOpenResumeAd(
        activity: Activity,
        onContinue: () -> Unit = {},
        delayTime: Long = 0L,
        showWaitingOverlay: Boolean = true,
    ) {
        showInterAd(
            activity = activity,
            adPlacement = AdsPlacementHolder.APP_OPEN,
            onContinue = onContinue,
            delayTime = delayTime,
            force = true,
            showWaitingOverlay = showWaitingOverlay,
        )
    }

    fun bindBannerAd(
        activity: Activity,
        adPlacement: String,
        onAdFailed: (() -> Unit)? = null
    ) {
        val placement = resolvePlacement(adPlacement)
        loadNextAd(placement)
        val bannerContainer = activity.findViewById<FrameLayout>(R.id.banner_container)

        if (skipIfAdsInactive {
                bannerContainer?.visibility = View.GONE
                onAdFailed?.invoke()
            }) return

        if (skipIfDisabled(placement) {
                bannerContainer?.visibility = View.GONE
            }) return

        AdsManager.getInstance().show(activity, placement, object : AdCallback {
            override fun onAdFailed(name: String, error: String) {
                bannerContainer?.visibility = View.GONE
                onAdFailed?.invoke()
                Log.e("AdsLib", "$name: $error")
            }

            override fun onAdImpression(name: String) {
                super.onAdImpression(name)
                Log.i("AdsLib", "[Impression] $placement")
            }
        })
    }

    fun bindNativeAd(
        activity: Activity,
        adPlacement: String,
        container: FrameLayout? = null,
        shimmer: ShimmerFrameLayout? = null,
        parentContainer: ViewGroup? = null,
        templateAdLayoutRes: Int = R.layout.template_ad,
        onAdFailed: (() -> Unit)? = null,
        onAdLoaded: (() -> Unit)? = null,
    ) {
        val placement = resolvePlacement(adPlacement)

        val adContainer: FrameLayout? = container ?: activity.findViewById(R.id.frAds)
        val shimmerView: ShimmerFrameLayout? = shimmer ?: activity.findViewById(R.id.shimmerNative)

        if (adContainer == null || shimmerView == null) return

        if (skipIfAdsInactive {
                shimmerView.stopShimmer()
                shimmerView.visibility = View.GONE
                adContainer.visibility = View.GONE
                parentContainer?.visibility = View.GONE
                onAdFailed?.invoke()
            }) return

        if (skipIfDisabled(placement) {
                shimmerView.stopShimmer()
                shimmerView.visibility = View.GONE
                adContainer.visibility = View.GONE
                parentContainer?.visibility = View.GONE
            }) return

        adContainer.visibility = View.VISIBLE
        adContainer.removeAllViews()

        shimmerView.visibility = View.VISIBLE
        shimmerView.startShimmer()

        if (shimmerView.parent != adContainer) {
            (shimmerView.parent as? ViewGroup)?.removeView(shimmerView)
            adContainer.addView(shimmerView)
        }

        AdsManager.getInstance().loadAndPopulateNativeAd(
            activity,
            placement,
            templateAdLayoutRes,
            adContainer,
            shimmerView,
            object : AdCallback {

                override fun onAdLoaded(name: String) {
                    shimmerView.stopShimmer()
                    shimmerView.visibility = View.GONE
                    onAdLoaded?.invoke()
                    Log.i("AdsLib", "$placement loaded")
                    preloadAfterShowNativeAd(placement)
                }

                override fun onAdImpression(name: String) {
                    super.onAdImpression(name)
                    Log.i("AdsLib", "[Impression] $placement")
                }

                override fun onAdFailed(name: String, error: String) {
                    shimmerView.stopShimmer()
                    shimmerView.visibility = View.GONE
                    adContainer.visibility = View.GONE

                    parentContainer?.visibility = View.GONE

                    onAdFailed?.invoke()

                    Log.e("AdsLib", "$placement: $error")
                    preloadAfterShowNativeAd(placement)
                }
            }
        )
    }

    fun loadAd(adPlacement: String) {
        if (!AdsLoader.isAdsEnabled()) return
        val placement = resolvePlacement(adPlacement)
        AdsManager.getInstance().load(placement, object : AdCallback {
            override fun onAdLoaded(name: String) {
                super.onAdLoaded(name)
                Log.i("AdsLib", "$name preloaded")
            }

            override fun onAdFailed(name: String, error: String) {
                super.onAdFailed(name, error)
                Log.e("AdsLib", "$name preload failed: $error")
            }
        })
    }
    fun loadNextAd(adPlacement: String) {
        if (!AdsLoader.isAdsEnabled()) return

        if (adPlacement == AdsPlacementHolder.INTER_SPLASH || adPlacement == AdsPlacementHolder.INTER_SPLASH_2)loadAd(AdsPlacementHolder.NATIVE_LANGUAGE_1_1)

        if (adPlacement == AdsPlacementHolder.NATIVE_FULL_SELECT) {
            loadAd(AdsPlacementHolder.NATIVE_OB_1)
            loadAd(AdsPlacementHolder.NATIVE_RELOAD_OB)
            loadAd(AdsPlacementHolder.NATIVE_FULL_1)
            loadAd(AdsPlacementHolder.NATIVE_FULL_2)
            loadAd(AdsPlacementHolder.NATIVE_OB_3)
        }
    }

    fun preloadAfterShowNativeAd(adPlacement: String) {
        if (!AdsLoader.isAdsEnabled()) return

        if (adPlacement == AdsPlacementHolder.NATIVE_LANGUAGE_1_1) loadAd(AdsPlacementHolder.NATIVE_LANGUAGE_RELOAD)
        if (adPlacement == AdsPlacementHolder.NATIVE_LANGUAGE_RELOAD) loadAd(AdsPlacementHolder.NATIVE_BEFORE_SELECT_STYLE)
        if (adPlacement == AdsPlacementHolder.NATIVE_BEFORE_SELECT_STYLE) loadAd(AdsPlacementHolder.NATIVE_SELECT_STYLE)
        if (adPlacement == AdsPlacementHolder.NATIVE_SELECT_STYLE) loadAd(AdsPlacementHolder.NATIVE_FULL_SELECT)
    }
}
