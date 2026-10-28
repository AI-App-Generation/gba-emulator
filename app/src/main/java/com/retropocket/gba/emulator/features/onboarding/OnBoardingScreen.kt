package com.retropocket.gba.emulator.features.onboarding

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.retropocket.gba.emulator.LogScreenView
import com.retropocket.gba.emulator.core.Analytics
import com.retropocket.gba.emulator.R
import com.retropocket.gba.emulator.ads.AdsLoader
import com.retropocket.gba.emulator.ads.AdsPlacementHolder
import com.retropocket.gba.emulator.ads.AdsPolicy
import com.retropocket.gba.emulator.ui.components.PrimaryPixelButton
import com.retropocket.gba.emulator.ui.components.RadiusPill
import com.retropocket.gba.emulator.ui.components.ads.ComposeNativeAdFrame
import com.retropocket.gba.emulator.ui.components.ads.NativeAdOverlay
import com.retropocket.gba.emulator.ui.theme.BgRoot
import com.retropocket.gba.emulator.ui.theme.BodyMedium
import com.retropocket.gba.emulator.ui.theme.BorderStrong
import com.retropocket.gba.emulator.ui.theme.DisplayPixel
import com.retropocket.gba.emulator.ui.theme.NeonCyan
import com.retropocket.gba.emulator.ui.theme.TextPrimary
import com.retropocket.gba.emulator.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Trang nào bấm Next sẽ chen native full. Theo kịch bản, native full tính theo CÚ CHUYỂN TRANG:
 * `native_full_1` là lúc đi từ trang 1 sang 2, `native_full_2` là từ trang 2 sang 3. Đi từ trang
 * 3 sang 4 không có quảng cáo.
 */
private val PAGES_WITH_NATIVE_FULL = setOf(0, 1)

/** Nội dung 4 trang giới thiệu, mỗi trang một ảnh nền riêng. */
private data class OnboardingContent(
    val title: String,
    val description: String,
    @DrawableRes val background: Int,
)

private val ONBOARDING_PAGES = listOf(
    OnboardingContent(
        title = "ONE SHELF,\nEVERY CONSOLE",
        description = "Game Boy, NES, SNES, Genesis, PlayStation and more — all in a single " +
            "library, sorted by system.",
        background = R.drawable.bg_onb1,
    ),
    OnboardingContent(
        title = "DOWNLOAD\nOR IMPORT",
        description = "Browse the built-in store, or pick ROM archives you already downloaded. " +
            "Both end up in the same library.",
        background = R.drawable.bg_onb2,
    ),
    OnboardingContent(
        title = "CONTROLS\nTHAT FIT",
        description = "Touch controls are laid out per console — d-pad, sticks and shoulder " +
            "buttons appear only where that machine had them.",
        background = R.drawable.bg_onb3,
    ),
    OnboardingContent(
        title = "PLAY\nRIGHT AWAY",
        description = "Five licensed homebrew games are bundled and ready. No setup, no account, " +
            "no waiting.",
        background = R.drawable.bg_onb4,
    ),
)

/** Ô native thường của từng trang; trang nào không có thì theo kịch bản là không có quảng cáo. */
private fun nativePlacementFor(page: Int): String? = when (page) {
    0 -> AdsPlacementHolder.NATIVE_OB_1
    2 -> AdsPlacementHolder.NATIVE_OB_3
    else -> null
}

/** Native full chen giữa 2 trang, đặt tên theo trang XUẤT PHÁT. */
private fun nativeFullPlacementFor(page: Int): String = when (page) {
    0 -> AdsPlacementHolder.NATIVE_FULL_1
    else -> AdsPlacementHolder.NATIVE_FULL_2
}

@Composable
fun OnboardingScreen(onNext: () -> Unit = {}) {
    val pageCount = ONBOARDING_PAGES.size
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current ?: return
    var showNativeFull by remember { mutableStateOf(false) }
    val isAdActive by AdsLoader.isActive.collectAsState()

    LogScreenView("onboarding_${pagerState.settledPage + 1}_view")

    LaunchedEffect(showNativeFull, pagerState.settledPage) {
        val blockWelcome = showNativeFull || pagerState.settledPage in setOf(0, 1)
        AdsPolicy.allowShowWelcomeBack = !blockWelcome
    }
    DisposableEffect(Unit) {
        onDispose { AdsPolicy.allowShowWelcomeBack = true }
    }

    fun navigateToNextReal() {
        showNativeFull = false
        scope.launch {
            delay(200)
            val nextPage = pagerState.currentPage + 1
            if (nextPage < pagerState.pageCount) {
                pagerState.animateScrollToPage(nextPage)
            } else {
                onNext()
            }
        }
    }

    fun logNext() {
        val page = pagerState.currentPage + 1
        // Trang cuối là "start" chứ không phải "next": đó là nhát bấm đưa user vào app, chỉ số
        // hoàn thành onboarding đọc từ chính nó.
        val suffix = if (page == pageCount) "start" else "next"
        Analytics.log("onboarding_${page}_$suffix")
    }

    fun navigateToNext() {
        if (isAdActive && pagerState.currentPage in PAGES_WITH_NATIVE_FULL) {
            showNativeFull = true
        } else {
            navigateToNextReal()
        }
    }

    BackHandler {
        if (showNativeFull) {
            showNativeFull = false
        } else {
            scope.launch {
                pagerState.animateScrollToPage(
                    (pagerState.currentPage - 1).coerceIn(0, pagerState.pageCount - 1)
                )
            }
        }
    }

    Box(Modifier.fillMaxSize().background(BgRoot)) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val isInViewport = pagerState.settledPage == page
            OnboardingPage(
                content = ONBOARDING_PAGES[page],
                pageIndex = page,
                pageCount = pageCount,
                adPlacement = nativePlacementFor(page),
                isInViewport = isInViewport,
                onNext = {
                    // Bắn trước khi chen quảng cáo, để lượt bấm không bị mất khi native full
                    // nuốt luồng điều hướng.
                    logNext()
                    if (page in PAGES_WITH_NATIVE_FULL) navigateToNext() else navigateToNextReal()
                }
            )
        }

        if (showNativeFull) {
            val placement = nativeFullPlacementFor(pagerState.currentPage)
            NativeAdOverlay(
                layoutResId = R.layout.frame_ad_full,
                closeDelaySeconds = 0,
                onClose = { navigateToNextReal() }
            ) { container, shimmer ->
                AdsPolicy.bindNativeAd(
                    activity = activity as Activity,
                    adPlacement = placement,
                    container = container,
                    shimmer = shimmer,
                    templateAdLayoutRes = R.layout.template_ad_full,
                    onAdFailed = { navigateToNextReal() }
                )
            }
        }
    }
}

/**
 * Một trang giới thiệu: ảnh minh hoạ chiếm phần trên, chữ và nút ở phần dưới, ô quảng cáo cuối
 * cùng. Ảnh dùng placeholder có phủ gradient tối để chữ luôn đọc được khi thay bằng artwork thật.
 */
@Composable
private fun OnboardingPage(
    content: OnboardingContent,
    pageIndex: Int,
    pageCount: Int,
    adPlacement: String? = null,
    isInViewport: Boolean = true,
    onNext: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize().background(BgRoot)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(content.background)
                        .size(coil.size.Size.ORIGINAL)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Chữ nằm ngay dưới ảnh nên chân ảnh phải chìm dần vào nền, tránh đường cắt cứng.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(BgRoot.copy(alpha = 0.35f), BgRoot.copy(alpha = 0.85f), BgRoot)
                        )
                    )
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = content.title,
                    style = DisplayPixel,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = content.description,
                    style = BodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(pageCount) { index ->
                        val active = index == pageIndex
                        Box(
                            Modifier
                                .width(if (active) 24.dp else 8.dp)
                                .height(8.dp)
                                .clip(RadiusPill)
                                .background(if (active) NeonCyan else BorderStrong)
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))

                PrimaryPixelButton(
                    text = if (pageIndex == pageCount - 1) "START" else stringResource(R.string.next).uppercase(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNext,
                )
                Spacer(Modifier.height(16.dp))
            }

            if (adPlacement != null && isInViewport) {
                ComposeNativeAdFrame(
                    adPlacement = adPlacement,
                    // Rời app rồi quay lại thì nạp ô _reload dùng chung cho mọi trang onboarding.
                    resumePlacement = AdsPlacementHolder.NATIVE_RELOAD_OB,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
