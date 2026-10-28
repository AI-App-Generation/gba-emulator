package com.retropocket.gba.emulator.features.onboarding

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.retropocket.gba.emulator.LogScreenView
import com.retropocket.gba.emulator.core.Analytics
import com.retropocket.gba.emulator.R
import com.retropocket.gba.emulator.ads.AdsLoader
import com.retropocket.gba.emulator.ads.AdsPlacementHolder
import com.retropocket.gba.emulator.ads.AdsPolicy
import com.retropocket.gba.emulator.data.domain.GameSystem
import com.retropocket.gba.emulator.data.domain.SystemId
import com.retropocket.gba.emulator.ui.components.*
import com.retropocket.gba.emulator.ui.components.ads.ComposeNativeAdFrame
import com.retropocket.gba.emulator.ui.components.ads.NativeAdOverlay
import com.retropocket.gba.emulator.ui.theme.*

/**
 * Màn khảo sát "bạn mê hệ máy nào".
 *
 * CỐ Ý KHÔNG LƯU lựa chọn — giống FeatureSelectionScreen của app mẫu, màn này tồn tại để tạo
 * thêm 3 điểm chạm quảng cáo giữa Language và Onboarding:
 *  - [AdsPlacementHolder.NATIVE_BEFORE_SELECT_STYLE] hiện ngay khi mở màn
 *  - [AdsPlacementHolder.NATIVE_SELECT_STYLE] nạp đè khi user chạm lựa chọn đầu tiên
 *  - [AdsPlacementHolder.NATIVE_FULL_SELECT] native full khi bấm Next, load hỏng thì đi tiếp luôn
 */
private val SURVEY_SYSTEMS = listOf(
    SystemId.GBA, SystemId.GB, SystemId.NES, SystemId.SNES,
    SystemId.GENESIS, SystemId.PSX, SystemId.N64, SystemId.NDS,
)

@Composable
fun StyleSurveyScreen(onNext: () -> Unit = {}) {
    LogScreenView("genre_view")

    val activity = LocalActivity.current
    val isAdActive by AdsLoader.isActive.collectAsState()

    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }
    var hasReloadedAd by remember { mutableStateOf(false) }
    var reloadNativeAd by remember { mutableStateOf<(String?) -> Unit>({}) }
    var showNativeFull by remember { mutableStateOf(false) }

    val systems = remember { SURVEY_SYSTEMS.mapNotNull { id -> GameSystem.ALL.firstOrNull { it.id == id } } }

    fun goNext() {
        showNativeFull = false
        onNext()
    }

    /**
     * Bắn ngay khi user bấm Next, TRƯỚC khi chen native full — nếu đặt sau, lần nào quảng cáo
     * nuốt luồng là mất luôn số liệu khảo sát.
     *
     * Khảo sát cho chọn nhiều nên gửi kèm `count`: chuỗi nối không tách được trong Firebase, chỉ
     * có `systems` thì đếm được tổ hợp chứ không đếm được mức độ chọn nhiều.
     */
    fun logContinue() {
        val picked = systems.filter { it.id.name in selected }.map { it.id.name.lowercase() }
        Analytics.log(
            "genre_continue",
            "systems" to picked.joinToString(","),
            "count" to picked.size,
        )
    }

    Box(Modifier.fillMaxSize().background(BgRoot)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.style_title),
                style = DisplayPixel,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.style_subtitle),
                style = BodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(systems, key = { it.id.name }) { system ->
                    SystemChoiceCard(
                        system = system,
                        isSelected = system.id.name in selected,
                        onClick = {
                            selected = if (system.id.name in selected) {
                                selected - system.id.name
                            } else {
                                selected + system.id.name
                            }
                            // Chỉ nạp đè một lần, ở lần chạm đầu tiên.
                            if (!hasReloadedAd && selected.isNotEmpty()) {
                                hasReloadedAd = true
                                reloadNativeAd(AdsPlacementHolder.NATIVE_SELECT_STYLE)
                            }
                        },
                    )
                }
            }

            PrimaryPixelButton(
                text = stringResource(R.string.style_next),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                enabled = selected.isNotEmpty(),
                onClick = {
                    logContinue()
                    if (isAdActive) showNativeFull = true else goNext()
                },
            )
            Spacer(Modifier.height(12.dp))

            ComposeNativeAdFrame(
                adPlacement = AdsPlacementHolder.NATIVE_BEFORE_SELECT_STYLE,
                modifier = Modifier.fillMaxWidth(),
                onReloadReady = { reloadNativeAd = it },
            )
        }

        if (showNativeFull && activity != null) {
            NativeAdOverlay(
                layoutResId = R.layout.frame_ad_full,
                closeDelaySeconds = 0,
                onClose = ::goNext,
            ) { container, shimmer ->
                AdsPolicy.bindNativeAd(
                    activity = activity as Activity,
                    adPlacement = AdsPlacementHolder.NATIVE_FULL_SELECT,
                    container = container,
                    shimmer = shimmer,
                    templateAdLayoutRes = R.layout.template_ad_full,
                    // Không load được thì sang màn kế tiếp luôn, không bắt user chờ.
                    onAdFailed = ::goNext,
                )
            }
        }
    }
}

@Composable
private fun SystemChoiceCard(
    system: GameSystem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.35f)
            .pressScale(onClick = onClick)
            .clip(RadiusCard)
            .background(
                Brush.linearGradient(
                    listOf(system.accent.copy(alpha = if (isSelected) 0.35f else 0.16f), BgSurface),
                ),
            )
            .border(
                if (isSelected) 2.dp else 1.dp,
                if (isSelected) system.accent else BorderSubtle,
                RadiusCard,
            ),
    ) {
        Column(
            Modifier.align(Alignment.CenterStart).padding(14.dp),
        ) {
            Text(
                system.shortName,
                style = TitlePixel,
                color = if (isSelected) system.accent else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                system.displayName,
                style = Caption,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isSelected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(system.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = BgRoot, modifier = Modifier.size(14.dp))
            }
        }
    }
}
