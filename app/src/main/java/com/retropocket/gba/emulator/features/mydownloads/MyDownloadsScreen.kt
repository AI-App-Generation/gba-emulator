package com.retropocket.gba.emulator.features.mydownloads

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retropocket.gba.emulator.R
import com.retropocket.gba.emulator.core.AppConfig
import com.retropocket.gba.emulator.core.file.FileManager
import com.retropocket.gba.emulator.data.database.entity.GameEntity
import com.retropocket.gba.emulator.data.database.repo.GameRepo
import com.retropocket.gba.emulator.ui.components.*
import com.retropocket.gba.emulator.ui.theme.*
import androidx.compose.material.icons.filled.Lock
import com.retropocket.gba.emulator.core.Analytics
import com.retropocket.gba.emulator.corefeatures.library.UnlockStore
import com.retropocket.gba.emulator.features.common.PremiumState
import com.retropocket.gba.emulator.ui.theme.NeonAmber
import com.retropocket.gba.emulator.ui.theme.ScrimHeavy

/**
 * Tab Downloads: 2 lối nhận game (duyệt web bên ngoài / nhập file từ máy) đặt ngay trên danh
 * sách những game đã được thêm vào máy, mới nhất lên đầu.
 *
 * Game mẫu không tính là "đã tải" nên không nằm trong danh sách này.
 */
@Composable
fun MyDownloadsScreen(
    onOpenImport: () -> Unit,
    onOpenGame: (String) -> Unit,
) {
    val context = LocalContext.current
    val games by GameRepo.games.collectAsStateWithLifecycle()
    val added = remember(games) {
        games.filterNot { it.isSample }.sortedByDescending { it.addedAt }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgRoot)
            .statusBarsPadding(),
    ) {
        AppTopBar(title = "DOWNLOADS", titleColor = NeonCyan)

        // 2 widget nằm cạnh nhau, dùng weight(1f) cả hai để luôn bằng nhau dù chữ dịch dài ngắn
        // khác nhau.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SourceWidget(
                kicker = stringResource(R.string.downloads_web_kicker),
                title = stringResource(R.string.downloads_web_title),
                caption = AppConfig.EXTERNAL_STORE_LABEL,
                icon = Icons.Filled.Public,
                cornerIcon = Icons.AutoMirrored.Filled.OpenInNew,
                accent = NeonMagenta,
                modifier = Modifier.weight(1f),
                onClick = {
                    Analytics.log("online_game")
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.EXTERNAL_STORE_URL)),
                        )
                    }
                },
            )
            SourceWidget(
                kicker = stringResource(R.string.downloads_device_kicker),
                title = stringResource(R.string.downloads_device_title),
                caption = stringResource(R.string.downloads_device_caption),
                icon = Icons.Filled.FolderZip,
                cornerIcon = Icons.AutoMirrored.Filled.ArrowForward,
                accent = NeonCyan,
                modifier = Modifier.weight(1f),
                onClick = {
                    Analytics.log("import_game")
                    onOpenImport()
                },
            )
        }

        if (added.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.Download,
                    title = stringResource(R.string.downloads_empty_title),
                    description = stringResource(R.string.downloads_empty_body),
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "header") {
                    SectionHeader(
                        title = stringResource(R.string.downloads_recent_header),
                        trailingText = "${added.size}",
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(added, key = { it.id }) { game ->
                    AddedGameRow(game) { onOpenGame(game.id) }
                }
                item(key = "tail") { ScrollTailSpacer() }
            }
        }
    }
}

/**
 * Widget nguồn game — cùng ngôn ngữ thị giác với thẻ "Continue" ở Home: thẻ bo 16.dp, nền
 * gradient ngả màu accent, nhãn pixel nhỏ phía trên tiêu đề chữ thường đậm.
 */
@Composable
private fun SourceWidget(
    kicker: String,
    title: String,
    caption: String,
    icon: ImageVector,
    cornerIcon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(128.dp)
            .pressScale(onClick = onClick)
            .clip(RadiusCard)
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.22f), BgSurface, BgSurface),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.35f), RadiusCard),
    ) {
        // Icon lớn mờ tràn ra góc dưới — hoạ tiết nền cho thẻ bớt phẳng, bị clip theo bo góc.
        Icon(
            icon,
            contentDescription = null,
            tint = accent.copy(alpha = 0.08f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(96.dp)
                .offset(x = 22.dp, y = 22.dp),
        )

        Box(
            Modifier
                .padding(14.dp)
                .size(36.dp)
                .neonGlow(accent, radius = 10.dp, alpha = 0.35f)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }

        Icon(
            cornerIcon,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
                .size(16.dp),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(kicker, style = LabelPixel, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(title, style = TitleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(caption, style = Caption, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AddedGameRow(game: GameEntity, onClick: () -> Unit) {
    // Đọc cùng nguồn với huy hiệu khoá trên GameCard. Hàng này trước đây không hiện khoá nên
    // game bị khoá trông y hệt game chơi được — user bấm vào mới biết, đúng kiểu gài bẫy.
    val unlockedIds by UnlockStore.unlockedIds.collectAsStateWithLifecycle()
    val isPremium by PremiumState.isPremium.collectAsStateWithLifecycle()
    val locked = remember(game.id, unlockedIds, isPremium) { !UnlockStore.isUnlocked(game) }

    // Tự bản địa hoá theo ngôn ngữ máy ("2 hours ago", "2 giờ trước"...) nên không cần chuỗi dịch.
    val addedAgo = remember(game.addedAt) {
        DateUtils.getRelativeTimeSpanString(
            game.addedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }
    ListRow(
        title = game.title,
        subtitle = "${game.system.shortName} · ${FileManager.readableSize(game.fileSize)} · $addedAgo",
        height = 64.dp,
        onClick = onClick,
        leading = {
            Box(Modifier.size(44.dp)) {
                GameCover(
                    game,
                    Modifier
                        .fillMaxSize()
                        .clip(RadiusSmall)
                        .border(1.dp, BorderSubtle, RadiusSmall),
                )
                if (locked) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RadiusSmall)
                            .background(ScrimHeavy),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = stringResource(R.string.badge_locked),
                            tint = NeonAmber,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
    )
}
