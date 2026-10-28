package com.retropocket.gba.emulator

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import com.retropocket.gba.emulator.ui.theme.BgRoot
import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.retropocket.gba.emulator.core.Analytics
import com.retropocket.gba.emulator.data.database.repo.GameRepo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bbl.ads.modules.AdsManager
import com.bbl.ads.modules.consent.ConsentCallback
import com.retropocket.gba.emulator.core.SettingManager
import com.retropocket.gba.emulator.features.onboarding.SplashScreen
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.analytics.FirebaseAnalytics
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import com.retropocket.gba.emulator.ads.AdsPlacementHolder
import com.retropocket.gba.emulator.ads.AdsPolicy
import com.retropocket.gba.emulator.features.confirmplay.ConfirmPlayScreen
import com.retropocket.gba.emulator.features.game.GameScreen
import com.retropocket.gba.emulator.features.importer.ImportScreen
import com.retropocket.gba.emulator.features.main.MainContent
import com.retropocket.gba.emulator.features.main.MainTab
import com.retropocket.gba.emulator.features.settings.LicensesScreen
import com.retropocket.gba.emulator.features.settings.SettingsScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.retropocket.gba.emulator.features.iap.IapScreen
import com.retropocket.gba.emulator.features.language.LanguageScreen
import com.retropocket.gba.emulator.features.onboarding.OnboardingScreen
import com.retropocket.gba.emulator.features.onboarding.StyleSurveyScreen
import com.retropocket.gba.emulator.features.common.PremiumState
import kotlinx.coroutines.delay

private const val ENTER_TIME = 400
private const val EXIT_TIME = 300

/*
Đây là bộ hiệu ứng do tôi soạn, bạn nên lựa chọn và tái sử dụng chúng một cách phù hợp khi kết hợp với NavHost hoặc AnimatedContent
Đối với các app có NavBar, transition cơ bản giữa các màn cùng level trên thanh đó thì dùng fade với 150ms
*/

val ZoomTransition = Pair(
    fadeIn(animationSpec = tween(ENTER_TIME)) + scaleIn(initialScale = 1.3f, animationSpec = tween(ENTER_TIME)),
    fadeOut(animationSpec = tween(EXIT_TIME)) + scaleOut(targetScale = 1.3f, animationSpec = tween(EXIT_TIME)),
)

val SlideHorizontalTransition = Pair(
    slideInHorizontally(initialOffsetX = { it }),
    slideOutHorizontally(targetOffsetX = { it }),
)

val FadeSlideHorizontal = Pair(
    fadeIn(animationSpec = tween(ENTER_TIME / 2)) + slideInHorizontally(initialOffsetX = { it / 3 }),
    fadeOut(animationSpec = tween(EXIT_TIME / 2)),
)

val FadeSlideUp = Pair(
    fadeIn(animationSpec = tween(ENTER_TIME / 2)) + slideInVertically(initialOffsetY = { it / 4 }),
    fadeOut(animationSpec = tween(EXIT_TIME / 2)),
)

val FlowTransition = Pair(
    fadeIn(animationSpec = tween(ENTER_TIME / 2)) + slideInHorizontally(
        initialOffsetX = { it / 3 },
        animationSpec = tween(ENTER_TIME)
    ),
    slideOutHorizontally(
        targetOffsetX = { -it / 3 },
        animationSpec = tween(ENTER_TIME * 2)
    ),
)

// Bắt buộc dùng trong màn Home thật sự
@Composable
fun CheckForConsent() {
    val activity = LocalActivity.current

    LaunchedEffect(Unit) {
        if (SettingManager.checkConsentDone.value) return@LaunchedEffect

        AdsManager.getInstance().runConsentFlow(activity, object : ConsentCallback {
            override fun onConsentComplete(canPersonalized: Boolean) {
                AdsManager.getInstance().logCustomEvent("consent_success", mutableMapOf<String, Any?>("result" to canPersonalized))
                AdsManager.getInstance().reloadConsent()
                SettingManager.setCheckConsentDone()
            }

            override fun onConsentError(message: String?) {
                AdsManager.getInstance().logCustomEvent("consent_error", mutableMapOf<String, Any?>("message" to (message ?: "")))
                AdsManager.getInstance().reloadConsent()
                SettingManager.setCheckConsentDone()
            }
        })
    }
}

@Composable
fun LogScreenView(screenName: String) {
    val context = LocalContext.current
    LaunchedEffect(screenName) {
        if (BuildConfig.DEBUG) return@LaunchedEffect
        FirebaseAnalytics.getInstance(context).logEvent(screenName, null)
    }
}

// Dùng ở màn nào được tôi yêu cầu vô hiệu hóa show Welcome back screen
@Composable
fun DisableWelcomeOverlayTemporarily() {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                AdsPolicy.allowShowWelcomeBack = false
            }
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                AdsPolicy.allowShowWelcomeBack = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            AdsPolicy.allowShowWelcomeBack = true
        }
    }
}



// Đây là flow chuẩn và cố định của App, bạn bắt đầu cần xây dựng từ MainContent() và thay thế vào flow bên dưới
private enum class AppScreen { Splash, Language, Style, Onboarding, Main }

@Composable
fun App() {
    var screenOrdinal by rememberSaveable { mutableStateOf(AppScreen.Splash.ordinal) }
    val screen = AppScreen.entries[screenOrdinal]
    fun goTo(s: AppScreen) { screenOrdinal = s.ordinal }

    AnimatedContent(
        targetState = screen,
        transitionSpec = { FlowTransition.first togetherWith FlowTransition.second },
        label = "app_flow"
    ) { target ->
        when (target) {
            AppScreen.Splash -> SplashScreen(
                onNext = { goTo(AppScreen.Language) },
                onPremiumReady = { goTo(AppScreen.Main) },
            )
            AppScreen.Language -> LanguageScreen(
                // App này không chia theo vùng, chỉ chọn ngôn ngữ.
                selectRegion = false,
                onNext = { goTo(AppScreen.Style) }
            )
            AppScreen.Style -> StyleSurveyScreen(
                onNext = { goTo(AppScreen.Onboarding) }
            )
            AppScreen.Onboarding -> OnboardingScreen(
                onNext = {
                    goTo(AppScreen.Main)
                }
            )
            AppScreen.Main -> {
                MainNavHost()
            }
        }
    }
}



/*
Đây là template cho một dạng App không có NavBar, từ HomeScreen đi sang một số màn chức năng khác
Các app cần có NavBar cũng nên tinh chỉnh từ dạng này sao cho phù hợp
*/

fun NavBackStackEntry.lifecycleIsResumed(): Boolean = this.lifecycle.currentState == Lifecycle.State.RESUMED

/** Để Main vẽ xong và trạng thái trả phí kịp về trước khi paywall trùm lên. */
private const val WELCOME_IAP_DELAY_MS = 600L

@Composable
fun MainNavHost() {
    val nav = rememberNavController()
    // Import xong thì phải quay về khung tab VÀ mở đúng tab Thư viện — nếu chỉ popBackStack, user
    // rơi lại vào tab Store và không thấy game vừa thêm ở đâu cả.
    var pendingTab by remember { mutableStateOf<MainTab?>(null) }

    fun goTo(route: String) {
        if (nav.currentBackStackEntry?.lifecycleIsResumed() == true) {
            nav.navigate(route) { launchSingleTop = true }
        }
    }

    fun goBack() {
        if (nav.currentBackStackEntry?.lifecycleIsResumed() == true) {
            nav.popBackStack()
        }
    }

    /**
     * Về thẳng khung tab, dọn sạch mọi màn đang chồng lên.
     *
     * Dùng sau khi mua premium: user có thể đã đi Main → Settings → IAP, mà pop từng nấc thì họ
     * rơi lại vào Settings. Mua xong là muốn vào dùng app chứ không phải quay về chỗ cũ.
     */
    fun goHome() {
        if (nav.currentBackStackEntry?.lifecycleIsResumed() == true) {
            nav.popBackStack("main", inclusive = false)
        }
    }

    // Chỉ chào hàng MỘT lần trong đời app. Cờ đặt ở đây chứ không đặt trong composable("main"),
    // vì NavHost huỷ composition của màn khi điều hướng đi — để bên trong thì đóng paywall xong
    // quay về Main là nó lại bật lên, thành vòng lặp không thoát được.
    var welcomeIapShown by rememberSaveable { mutableStateOf(false) }

    NavHost(
        navController = nav,
        modifier = Modifier.background(BgRoot),
        startDestination = "main",
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None }
    ) {
        composable("main") {
            CheckForConsent()

            // Lần đầu cài app, vào tới Main thì chào bằng paywall.
            LaunchedEffect(Unit) {
                if (welcomeIapShown) return@LaunchedEffect
                if (!SettingManager.isFirstTime.value) return@LaunchedEffect

                // Chờ một nhịp vì hai lý do: để Main kịp vẽ xong rồi paywall mới trùm lên (đỡ
                // giật), và để trạng thái trả phí từ Google Play kịp về — người cài lại máy mà
                // vẫn đang có gói thì tuyệt đối không được chào hàng lần nữa.
                delay(WELCOME_IAP_DELAY_MS)
                if (PremiumState.isPremium.value) return@LaunchedEffect

                welcomeIapShown = true
                goTo("iap")
            }

            MainContent(
                onOpenIap = { goTo("iap") },
                onOpenGame = { gameId -> goTo("confirm/$gameId") },
                onOpenImport = { goTo("import") },
                onOpenSettings = { goTo("settings") },
                requestedTab = pendingTab,
                onRequestedTabConsumed = { pendingTab = null },
            )
        }

        composable(
            route = "confirm/{gameId}",
            enterTransition = { FadeSlideUp.first },
            exitTransition = { FadeSlideUp.second }
        ) { entry ->
            ConfirmPlayScreen(
                gameId = entry.arguments?.getString("gameId").orEmpty(),
                onBack = ::goBack,
                onPlay = { gameId -> goTo("game/$gameId") },
            )
        }

        composable(
            route = "game/{gameId}",
            enterTransition = { ZoomTransition.first },
            exitTransition = { ZoomTransition.second }
        ) { entry ->
            LogScreenView("game_view")
            // game_start: game thật sự được mở (đã qua PlayGate), kèm game và hệ máy để biết
            // console nào được chơi nhiều. Khoá theo gameId nên xoay máy không bắn trùng.
            val startedGameId = entry.arguments?.getString("gameId").orEmpty()
            LaunchedEffect(startedGameId) {
                val game = GameRepo.games.value.firstOrNull { it.id == startedGameId }
                Analytics.log(
                    "game_start",
                    "game" to (game?.title ?: startedGameId),
                    "system" to (game?.systemId ?: "unknown"),
                )
            }
            // Đang chơi thì không được chèn màn Welcome back đè lên.
            DisableWelcomeOverlayTemporarily()
            ImmersiveGameMode()
            val gameActivity = LocalActivity.current
            GameScreen(
                gameId = entry.arguments?.getString("gameId").orEmpty(),
                onExit = {
                    // inter_home: quay về Home từ màn chơi game. KHÔNG force — đây là inter duy
                    // nhất chịu luật khoảng cách 10 giây trong AdsPolicy, tránh ra vào game liên
                    // tục là ăn quảng cáo liên tục.
                    if (gameActivity != null) {
                        AdsPolicy.showInterAd(
                            activity = gameActivity,
                            adPlacement = AdsPlacementHolder.INTER_HOME,
                            onContinue = { goBack() },
                        )
                    } else {
                        goBack()
                    }
                },
            )
        }

        composable(
            route = "import",
            enterTransition = { FadeSlideUp.first },
            exitTransition = { FadeSlideUp.second }
        ) {
            ImportScreen(
                onBack = ::goBack,
                onImported = {
                    pendingTab = MainTab.Category
                    goBack()
                },
            )
        }

        composable(
            route = "settings",
            enterTransition = { FadeSlideHorizontal.first },
            exitTransition = { FadeSlideHorizontal.second }
        ) {
            SettingsScreen(
                onOpenIap = { goTo("iap") },
                onBack = ::goBack,
                onOpenLicenses = { goTo("licenses") },
                onOpenLanguage = { goTo("language") },
            )
        }

        composable(
            route = "language",
            enterTransition = { FadeSlideHorizontal.first },
            exitTransition = { FadeSlideHorizontal.second }
        ) {
            // Cùng một màn chọn ngôn ngữ dùng lại từ luồng khởi động — vào từ Settings thì "next"
            // chỉ đơn giản là quay lại, không đi tiếp onboarding.
            LanguageScreen(selectRegion = false, onBack = ::goBack, onNext = ::goBack)
        }

        composable(
            route = "iap",
            enterTransition = { FadeSlideUp.first },
            exitTransition = { FadeSlideUp.second }
        ) {
            IapScreen(onClose = ::goBack, onPurchased = ::goHome)
        }

        composable(
            route = "licenses",
            enterTransition = { FadeSlideHorizontal.first },
            exitTransition = { FadeSlideHorizontal.second }
        ) {
            LicensesScreen(onBack = ::goBack)
        }
    }
}

/**
 * Ẩn thanh trạng thái + thanh điều hướng khi đang chơi, và KHÔI PHỤC lại khi rời màn.
 * Quên khôi phục sẽ khiến các màn khác mất thanh hệ thống.
 *
 * Đồng thời mở khoá xoay màn hình CHỈ ở màn chơi: app khoá dọc trong manifest, nếu không mở ở đây
 * thì bố cục ngang của GameScreen sẽ không bao giờ chạy được. Activity đã khai báo
 * `configChanges` cho orientation nên xoay không tạo lại Activity.
 */
@Composable
private fun ImmersiveGameMode() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(Unit) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        val previousOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER

        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = previousOrientation
        }
    }
}
