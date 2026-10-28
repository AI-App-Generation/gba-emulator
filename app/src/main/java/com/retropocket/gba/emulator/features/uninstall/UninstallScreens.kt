package com.retropocket.gba.emulator.features.uninstall

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retropocket.gba.emulator.R
import com.retropocket.gba.emulator.ads.AdsPlacementHolder
import com.retropocket.gba.emulator.core.file.FileManager
import com.retropocket.gba.emulator.data.database.repo.GameRepo
import com.retropocket.gba.emulator.ui.components.*
import com.retropocket.gba.emulator.ui.components.ads.ComposeNativeAdFrame
import com.retropocket.gba.emulator.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bước 1 — native `native_delete_app_1`.
 *
 * Giữ giọng điệu nhẹ nhàng, chưa doạ: chỉ nhắc rằng thư viện game nằm trong app.
 */
@Composable
fun UninstallNoticeScreen(
    onKeepApp: () -> Unit,
    onContinue: () -> Unit,
) {
    // Bấm Back ở đây = đổi ý, không phải thoát ra màn hình chờ.
    BackHandler(onBack = onKeepApp)

    val games by GameRepo.games.collectAsStateWithLifecycle()

    UninstallScaffold(
        icon = Icons.Filled.SportsEsports,
        accent = NeonCyan,
        title = stringResource(R.string.uninstall_notice_title),
        description = if (games.isEmpty()) {
            stringResource(R.string.uninstall_notice_desc_empty)
        } else {
            stringResource(R.string.uninstall_notice_desc, games.size)
        },
        primaryLabel = stringResource(R.string.uninstall_keep_app),
        onPrimary = onKeepApp,
        secondaryLabel = stringResource(R.string.uninstall_continue),
        secondaryColor = TextTertiary,
        onSecondary = onContinue,
        adPlacement = AdsPlacementHolder.NATIVE_DELETE_APP_1,
    )
}

/**
 * Bước 2 — native `native_delete_app_2`.
 *
 * Đây mới là màn cảnh báo thật: nói thẳng gỡ app là mất ROM, save state và bản lưu trong game,
 * kèm số liệu đếm được trên máy. Toàn bộ dữ liệu nằm trong vùng riêng của app nên gỡ là mất
 * sạch, không có bản sao nào ở nơi khác.
 */
@Composable
fun UninstallWarningScreen(
    onKeepApp: () -> Unit,
    onUninstallAnyway: () -> Unit,
) {
    BackHandler(onBack = onKeepApp)

    val games by GameRepo.games.collectAsStateWithLifecycle()
    // Đếm file trên đĩa ở luồng IO, đếm xong mới hiện — không chặn khung hình đầu.
    val stateCount by produceState(initialValue = 0) {
        value = withContext(Dispatchers.IO) {
            runCatching { FileManager.statesDir().listFiles()?.count { it.isFile } ?: 0 }
                .getOrDefault(0)
        }
    }
    val saveCount by produceState(initialValue = 0) {
        value = withContext(Dispatchers.IO) {
            runCatching { FileManager.savesDir().listFiles()?.count { it.isFile } ?: 0 }
                .getOrDefault(0)
        }
    }

    UninstallScaffold(
        icon = Icons.Filled.Warning,
        accent = NeonRed,
        title = stringResource(R.string.uninstall_warning_title),
        description = stringResource(R.string.uninstall_warning_desc),
        primaryLabel = stringResource(R.string.uninstall_keep_games),
        onPrimary = onKeepApp,
        secondaryLabel = stringResource(R.string.uninstall_confirm),
        secondaryColor = NeonRed,
        onSecondary = onUninstallAnyway,
        adPlacement = AdsPlacementHolder.NATIVE_DELETE_APP_2,
    ) {
        Spacer(Modifier.height(20.dp))
        LossRow(
            icon = Icons.Filled.SportsEsports,
            text = stringResource(R.string.uninstall_loss_games, games.size),
        )
        Spacer(Modifier.height(8.dp))
        LossRow(
            icon = Icons.Filled.SaveAlt,
            text = stringResource(R.string.uninstall_loss_saves, stateCount + saveCount),
        )
    }
}

/**
 * Khung chung của 2 bước: cùng bố cục, cùng khoảng cách, chỉ khác nội dung — để 2 màn nối nhau
 * mà không bị nhảy layout.
 */
@Composable
private fun UninstallScaffold(
    icon: ImageVector,
    accent: Color,
    title: String,
    description: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    secondaryColor: Color,
    onSecondary: () -> Unit,
    adPlacement: String,
    extraContent: @Composable (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize().background(BgRoot)) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(76.dp)
                        .neonGlow(accent, radius = 18.dp, alpha = 0.35f)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.15f))
                        .border(1.dp, accent.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(36.dp))
                }

                Spacer(Modifier.height(22.dp))
                Text(title, style = TitleLarge, color = TextPrimary, textAlign = TextAlign.Center)

                Spacer(Modifier.height(10.dp))
                Text(
                    description,
                    style = BodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )

                extraContent?.invoke()

                Spacer(Modifier.height(32.dp))
                PrimaryPixelButton(
                    text = primaryLabel,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onPrimary,
                )

                Spacer(Modifier.height(6.dp))
                // Lối rời đi để dạng chữ, không dùng nút đặc — hành động phụ không nên tranh
                // sự chú ý với nút giữ app.
                Text(
                    secondaryLabel,
                    style = LabelPixel,
                    color = secondaryColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RadiusPill)
                        .pressScale(onClick = onSecondary)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }

            ComposeNativeAdFrame(
                adPlacement = adPlacement,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun LossRow(icon: ImageVector, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RadiusCard)
            .background(BgSurface)
            .border(1.dp, BorderSubtle, RadiusCard)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = NeonRed, modifier = Modifier.size(18.dp))
        Text(text, style = BodySmall, color = TextPrimary)
    }
}
