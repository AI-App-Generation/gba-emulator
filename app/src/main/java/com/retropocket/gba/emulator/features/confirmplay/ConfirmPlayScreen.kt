package com.retropocket.gba.emulator.features.confirmplay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retropocket.gba.emulator.core.file.FileManager
import com.retropocket.gba.emulator.corefeatures.emulator.CoreCatalog
import com.retropocket.gba.emulator.data.database.entity.GameEntity
import com.retropocket.gba.emulator.data.database.repo.GameRepo
import com.retropocket.gba.emulator.ui.components.*
import com.retropocket.gba.emulator.ui.theme.*

/**
 * Màn xác nhận trước khi vào game. Cố ý cho cuộn được: tên game dài + hàng meta + cảnh báo thiếu
 * core + 2 nút phụ có thể tràn trên máy màn nhỏ.
 */
@Composable
fun ConfirmPlayScreen(
    gameId: String,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
) {
    val games by GameRepo.games.collectAsStateWithLifecycle()
    val game = remember(games, gameId) { games.firstOrNull { it.id == gameId } }

    if (game == null) {
        Box(Modifier.fillMaxSize().background(BgRoot), Alignment.Center) {
            EmptyState(
                icon = Icons.Filled.Delete,
                title = "Game not found",
                description = "It may have been removed from your library.",
                actionLabel = "GO BACK",
                onAction = onBack,
            )
        }
        return
    }

    val coreAvailable = remember(game.systemId) { CoreCatalog.isAvailable(game.system) }

    Box(Modifier.fillMaxSize().background(BgRoot)) {
        // Nền mờ lấy từ chính ảnh bìa để màn hình có không khí riêng theo từng game.
        // Phải giảm alpha chứ không chỉ dựa vào blur: Modifier.blur là no-op dưới Android 12,
        // nếu chỉ blur thì trên máy cũ ảnh bìa hiện rõ nét làm chữ và nút mất tương phản.
        GameCover(
            game,
            Modifier
                .fillMaxSize()
                .alpha(0.3f)
                .blur(32.dp),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(BgRoot.copy(alpha = 0.55f), BgRoot.copy(alpha = 0.94f), BgRoot),
                    ),
                ),
        )

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val minContentHeight = maxHeight

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = minContentHeight)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .width(200.dp)
                            .aspectRatio(3f / 4f)
                            .neonGlow(game.system.accent, radius = 18.dp, alpha = 0.28f)
                            .clip(RadiusCard)
                            .border(1.dp, BorderSubtle, RadiusCard),
                    ) {
                        GameCover(game, Modifier.fillMaxSize())
                    }

                    Spacer(Modifier.height(24.dp))
                    Text(
                        game.title,
                        style = TitleLarge,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )

                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PillChip(
                            label = game.system.shortName,
                            leadingDot = game.system.accent,
                            accent = game.system.accent,
                            selected = true,
                            height = 28.dp,
                        )
                        Text("·", style = Caption, color = TextTertiary)
                        Text(FileManager.readableSize(game.fileSize), style = Caption, color = TextSecondary)
                        game.lastPlayedAt?.let { played ->
                            Text("·", style = Caption, color = TextTertiary)
                            Text(relativeTime(played), style = Caption, color = TextSecondary)
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                    PrimaryPixelButton(
                        text = "▶  PLAY",
                        modifier = Modifier.width(220.dp),
                        height = 56.dp,
                        enabled = coreAvailable,
                        onClick = { onPlay(game.id) },
                    )

                    Spacer(Modifier.height(20.dp))
                    // 2 slot rộng cố định: nhãn không đổi độ dài, nút không bị ẩn -> không nhảy layout.
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconLabelAction(
                            icon = if (game.isFavourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            label = "FAVOURITE",
                            tint = if (game.isFavourite) NeonAmber else TextSecondary,
                            onClick = { GameRepo.toggleFavourite(game) },
                        )
                        IconLabelAction(
                            icon = Icons.Filled.Delete,
                            label = "DELETE",
                            tint = NeonRed,
                            enabled = !game.isSample,
                            onClick = {
                                GameRepo.delete(game)
                                onBack()
                            },
                        )
                    }

                    if (!coreAvailable) {
                        Spacer(Modifier.height(20.dp))
                        WarningBanner(
                            text = "The ${game.system.displayName} core is not bundled in this build, " +
                                "so this game cannot be played yet.",
                            color = NeonRed,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }
            }
        }

        // Một lớp duy nhất: nền + vùng chạm + ripple trùng khít nhau (trước đây là 2 hình tròn
        // lồng nhau khiến ripple nhỏ hơn nền 4.dp).
        IconCircleButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = TextPrimary,
            size = 40.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .clip(CircleShape)
                .background(BgSurface.copy(alpha = 0.6f)),
            onClick = onBack,
        )
    }
}

/** Mô tả thời điểm chơi gần nhất theo cách người đọc hiểu ngay, không phải timestamp thô. */
private fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Played just now"
        minutes < 60 -> "Played ${minutes}m ago"
        hours < 24 -> "Played ${hours}h ago"
        days < 7 -> "Played ${days}d ago"
        else -> "Played ${days / 7}w ago"
    }
}

@Composable
private fun IconLabelAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(120.dp)
            .clip(RadiusCard)
            .pressScale(enabled, onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = LabelPixel,
            color = if (enabled) TextSecondary else TextTertiary.copy(alpha = 0.5f),
        )
    }
}
