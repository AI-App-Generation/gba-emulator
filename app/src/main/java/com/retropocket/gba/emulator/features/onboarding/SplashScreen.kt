package com.retropocket.gba.emulator.features.onboarding

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.retropocket.gba.emulator.ui.components.LinearProgressTrack
import com.retropocket.gba.emulator.ui.components.neonGlow
import com.retropocket.gba.emulator.ui.theme.BgRoot
import com.retropocket.gba.emulator.ui.theme.BorderSubtle
import com.retropocket.gba.emulator.ui.theme.Caption
import com.retropocket.gba.emulator.ui.theme.DisplayPixel
import com.retropocket.gba.emulator.ui.theme.NeonCyan
import com.retropocket.gba.emulator.ui.theme.TextPrimary
import com.retropocket.gba.emulator.ads.AdsLoader
import com.retropocket.gba.emulator.ads.AdsPlacementHolder
import com.retropocket.gba.emulator.ads.AdsPolicy
import com.retropocket.gba.emulator.core.SettingManager
import com.retropocket.gba.emulator.features.common.PremiumState
import com.retropocket.gba.emulator.ui.components.ads.AdBanner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.retropocket.gba.emulator.LogScreenView
import com.retropocket.gba.emulator.R
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

private const val LOADING_PROGRESS_ANIMATION_MS = 1200L

@Composable
fun SplashScreen(
    onNext: () -> Unit = {},
    /** User đã trả phí: vào thẳng app, bỏ qua cả chặng Language / Style / Onboarding. */
    onPremiumReady: () -> Unit = onNext,
) {
    LogScreenView("splash_view")

    val activity = LocalActivity.current as Activity

    val ready by AdsLoader.finished.collectAsState()
    val isPremium by PremiumState.isPremium.collectAsState()
    var progressComplete by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Rời Splash đúng MỘT lần.
    //
    // Cần chốt này vì có hai đường ra chạy song song: nhánh premium ở effect bên dưới, và
    // callback của quảng cáo mở màn. Nếu quyền trả phí về ĐÚNG LÚC quảng cáo đang hiển thị thì
    // effect chạy lại, đẩy user vào Main, rồi lát sau quảng cáo đóng lại gọi onNext và ném họ
    // ngược về màn chọn ngôn ngữ. Ai tới trước thì thắng, người sau bị chặn.
    val hasLeft = remember { AtomicBoolean(false) }
    val leaveOnce: (() -> Unit) -> Unit = { go -> if (hasLeft.compareAndSet(false, true)) go() }

    // Theo dõi cả isPremium chứ không chỉ ready: quyền trả phí đến từ Google Play nên có thể
    // về muộn hơn AdsLoader một nhịp. Khoá theo nó thì lúc nó lật, effect chạy lại và user vẫn
    // kịp đi đường tắt — miễn là còn đang đứng ở Splash.
    LaunchedEffect(ready, isPremium) {
        if (!ready) return@LaunchedEffect

        progressComplete = true

        // Cả chặng Language / Style / Onboarding đều là phễu quảng cáo. Bắt người vừa trả tiền
        // để không xem quảng cáo đi hết phễu đó là chuyện vô nghĩa nhất có thể làm với họ.
        if (isPremium) {
            leaveOnce(onPremiumReady)
            return@LaunchedEffect
        }

        delay(LOADING_PROGRESS_ANIMATION_MS + 1500)

        AdsPolicy.showInterAd(
            activity = activity,
            // Lần đầu cài app dùng inter_splash; từ lần mở thứ 2 trở đi đổi sang bản _2.
            adPlacement = if (SettingManager.isFirstTime.value) {
                AdsPlacementHolder.INTER_SPLASH
            } else {
                AdsPlacementHolder.INTER_SPLASH_2
            },
            force = true,
            onContinue = { leaveOnce(onNext) },
        )
    }

    // Nhịp nhấp nháy của dòng "insert coin" — gợi màn chờ của máy arcade.
    val blink = rememberInfiniteTransition(label = "blink")
    val coinAlpha by blink.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "coin",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgRoot)
            .navigationBarsPadding()
    ) {
        ScanlineOverlay(Modifier.fillMaxSize())

        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Mượn luôn icon launcher thay vì nuôi thêm một bản logo riêng trong drawable.
                // Phải load bằng Coil: từ API 26, R.mipmap.ic_launcher_round trỏ tới file
                // adaptive-icon XML, mà painterResource không đọc được loại này (chỉ đọc bitmap
                // và vector). Coil dựng được mọi Drawable nên không kén.
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .neonGlow(NeonCyan, radius = 26.dp, alpha = 0.3f),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(R.mipmap.ic_launcher_round)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.app_name).uppercase(),
                    style = DisplayPixel,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "insert coin to start",
                    style = Caption,
                    color = NeonCyan.copy(alpha = coinAlpha),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 128.dp)
                    .padding(horizontal = 32.dp)
            ) {
                AppLoadingProgressBar(progressComplete)
            }

            if (ready) {
                AdBanner(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    placement = AdsPlacementHolder.BANNER_SPLASH
                )
            }
        }
    }
}

/** Lưới scanline rất mờ gợi màn hình CRT — chỉ là nhấn nền, không được cản trở đọc chữ. */
@Composable
private fun ScanlineOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val step = 4.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawRect(
                color = NeonCyan.copy(alpha = 0.04f),
                topLeft = Offset(0f, y),
                size = Size(size.width, 1f),
            )
            y += step
        }
    }
}

@Composable
fun CustomLoadingProgressBar(
    value: Int,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    LinearProgressTrack(
        modifier = modifier.fillMaxWidth(),
        progress = (value.coerceIn(0, 100)) / 100f,
        color = NeonCyan,
        trackColor = BorderSubtle,
        height = height,
    )
}

@Composable
fun AppLoadingProgressBar(
    isDone: Boolean,
    modifier: Modifier = Modifier
) {
    val target = remember { (60..85).random() }

    var current by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isDone) {
        val start = current
        val end = if (isDone) 100f else target.toFloat()

        val steps = 60
        val stepDelay = LOADING_PROGRESS_ANIMATION_MS / steps

        for (i in 1..steps) {
            val t = i / steps.toFloat()
            current = start + (end - start) * t
            delay(stepDelay)
        }

        current = end
    }

    CustomLoadingProgressBar(
        value = current.toInt(),
        modifier = modifier
    )
}
