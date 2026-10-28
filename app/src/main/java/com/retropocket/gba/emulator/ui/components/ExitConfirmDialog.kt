package com.retropocket.gba.emulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.retropocket.gba.emulator.R
import com.retropocket.gba.emulator.ads.AdsPlacementHolder
import com.retropocket.gba.emulator.ads.AdsLoader
import com.retropocket.gba.emulator.ui.components.ads.ComposeNativeAdFrame
import com.retropocket.gba.emulator.ui.theme.*

/**
 * Hộp thoại xác nhận thoát, dùng chung cho 2 chỗ: bấm nút đóng trong màn chơi game, và bấm Back
 * ở tab Home để thoát app.
 *
 * Ngoài việc hỏi lại cho chắc, nó còn là chỗ chứa ô quảng cáo nhỏ
 * [AdsPlacementHolder.NATIVE_EXIT] — nên thân dialog để rộng hơn dialog thường một chút.
 */
@Composable
fun ExitConfirmDialog(
    title: String,
    description: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(ScrimHeavy)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RadiusCard)
                    .background(BgSurface)
                    .border(1.dp, BorderSubtle, RadiusCard)
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = TitleMedium, color = TextPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    description,
                    style = BodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )

                // Bọc cả hai Spacer cùng khối quảng cáo: NativeAdFrame tự thu về 0dp khi user đã
                // trả phí, để riêng hai Spacer ở ngoài thì dialog hở đúng 28dp không lý do.
                if (AdsLoader.isAdsEnabled()) {
                    Spacer(Modifier.height(14.dp))
                    ComposeNativeAdFrame(
                        adPlacement = AdsPlacementHolder.NATIVE_EXIT,
                        layoutResId = R.layout.frame_ad_small,
                        templateAdLayoutRes = R.layout.template_ad_small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Ở lại là hành động chính: người dùng bấm Back nhầm nhiều hơn là muốn thoát.
                    PrimaryPixelButton(
                        text = stringResource(R.string.exit_stay),
                        modifier = Modifier.weight(1f),
                        height = 46.dp,
                        glow = false,
                        onClick = onDismiss,
                    )
                    SecondaryPixelButton(
                        text = confirmLabel,
                        modifier = Modifier.weight(1f),
                        accent = NeonRed,
                        height = 46.dp,
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}
