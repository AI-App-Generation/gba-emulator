package com.retropocket.gba.emulator

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.bbl.ads.modules.AdsManager
import com.bbl.ads.modules.application.AdsMultiDexApplication
import com.bbl.ads.modules.billing.AppPurchase
import com.bbl.ads.modules.configs.GlobalConfig
import com.retropocket.gba.emulator.ads.AdsLoader
import com.retropocket.gba.emulator.ads.AdsPolicy
import com.retropocket.gba.emulator.core.Analytics
import com.retropocket.gba.emulator.core.SettingManager
import com.retropocket.gba.emulator.core.file.FileManager
import com.retropocket.gba.emulator.corefeatures.library.LibraryBootstrap
import com.retropocket.gba.emulator.corefeatures.library.UnlockStore
import com.retropocket.gba.emulator.data.database.repo.GameRepo
import com.retropocket.gba.emulator.features.iap.Iap
import com.retropocket.gba.emulator.features.uninstall.UninstallActivity
import com.retropocket.gba.emulator.features.uninstall.createUninstallShortcut
import com.retropocket.gba.emulator.features.iap.IapController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class GlobalApp : AdsMultiDexApplication() {

    private val _shouldShowWelcomeBack = MutableStateFlow(false)
    val shouldShowWelcomeBack = _shouldShowWelcomeBack.asStateFlow()

    override fun createDefaultGlobalConfig(): GlobalConfig {
        return GlobalConfig().apply {
            appId = BuildConfig.ADMOB_APP_ID
            isConsentRequiredBeforeInit = false
            isEnableRemoteConfig = true
            adLoggingType = "realtime_database"
            isVariantDev = BuildConfig.DEBUG
            loadStrategy = GlobalConfig.LOAD_STRATEGY_WATERFALL
            // Khoảng cách tối thiểu giữa 2 inter trong app (inter_home ra vào game liên tục).
            // Các inter bắt buộc như splash/gỡ app đều gọi với force = true nên không bị chặn.
            interAdInterval = 20_000
            // Adjust: token app + token event dùng để đo doanh thu quảng cáo.
            adjustToken = "cjozul6n3h8g"
            adjustEventToken = "pzugdo"
            appsflyerToken = ""
            // Meta / Facebook SDK (trùng với @string/facebook_app_id + facebook_client_token
            // đang khai báo ở AndroidManifest cho meta-data của SDK).
            metaFacebookAppId = "1363982395360937"
            facebookClientToken = "7d6fcd3add2c8250f5beb262579b9954"
            // Bắn doanh thu quảng cáo sang Facebook dưới dạng event "purchase" để Meta có tín hiệu
            // tối ưu chiến dịch (tương đương AppEventsLogger.logPurchase ở bản cũ).
            isEnableFacebookAdRevenuePurchaseLogging = true
            // Cả 3 gói đều tắt quảng cáo. Module-ads đối chiếu id mua được với 2 mảng này để tự
            // nhận diện user đã trả tiền, nên phải khai đúng loại: tuần/tháng là subscription,
            // vĩnh viễn là sản phẩm mua đứt.
            adFreeSubscriptionProductIds = Iap.subscriptions.toTypedArray()
            adFreeInAppProductIds = Iap.inAppProducts.toTypedArray()
            testDevices = arrayOf("727854F084C6F0CDC793A5A192B72FCC", "8D9B9371B8704790A1C041FAF7C14071", "B7DE3A60025A6EBCAC171F608A5EB87B", "EB52EFE4C726897953885CB827CA2EFC")
        }
    }

    override fun onCreate() {
        super.onCreate()

        SettingManager.init(this)

        // Không dùng DI framework (theo APP_STRUCTURE.md): khởi tạo singleton hạ tầng một lần
        // tại đây với applicationContext, các tầng khác truy cập trực tiếp qua object.
        FileManager.init(this)
        GameRepo.init(this)
        UnlockStore.init(this)
        // Cài game mẫu + quét thư viện ở tầng Application, không phụ thuộc màn Splash có được
        // hiển thị hay không.
        LibraryBootstrap.start(this)

        AdsManager.getInstance().apply {
            disableAppResumeWithActivity(com.google.android.libraries.ads.mobile.sdk.common.AdActivity::class.java)
            // Xem xong quảng cáo Vungle, quay lại app sẽ bị tính là "mở app" và ăn thêm một
            // quảng cáo app-open nếu không chặn Activity này.
            disableAppResumeWithActivity(com.vungle.ads.internal.ui.VungleActivity::class.java)
            disableAppResumeWithActivity(MainActivity::class.java)
            // Luồng gỡ app là màn giữ chân, chen thêm quảng cáo mở app vào đó thì phản tác dụng.
            disableAppResumeWithActivity(UninstallActivity::class.java)
        }

        // Shortcut "Uninstall" ở menu giữ icon launcher — lối vào duy nhất khả thi, vì Android
        // không báo cho app biết lúc nó sắp bị gỡ.
        createUninstallShortcut(this)

        Analytics.init(this)

        AdsLoader.initEarly(this)

        observeAppBackground()
        initBilling()
    }

    private fun observeAppBackground() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (!AdsPolicy.isShowingInterAd && AdsLoader.isAdsEnabled()) {
                    _shouldShowWelcomeBack.value = true
                }
            }
        })
    }

    fun consumeWelcomeBack() {
        _shouldShowWelcomeBack.value = false
    }

    private fun initBilling() {
        val listIAPId: ArrayList<String> = ArrayList()
        val listSubsId: ArrayList<String> = ArrayList()
        listSubsId.add(Iap.weeklyId)
        listSubsId.add(Iap.monthlyId)
        listIAPId.add(Iap.lifetimeId)
        AppPurchase.getInstance().initBilling(this, listIAPId, listSubsId, this.globalConfig)
        IapController.registerPurchaseListener()
        // Giá chỉ tồn tại sau khi billing client bắt tay xong với Play. Không có listener này thì
        // paywall mở ra trước thời điểm đó sẽ trống giá cho tới khi user tự mở lại.
        IapController.registerBillingListener()
    }
}
