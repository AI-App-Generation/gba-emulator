package com.retropocket.gba.emulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.compose.SubcomposeAsyncImage
import com.retropocket.gba.emulator.R
import com.retropocket.gba.emulator.corefeatures.library.CoverUrls
import com.retropocket.gba.emulator.corefeatures.library.UnlockStore
import com.retropocket.gba.emulator.features.common.PremiumState
import com.retropocket.gba.emulator.data.database.entity.GameEntity
import com.retropocket.gba.emulator.ui.theme.*

/**
 * Card game dùng chung cho hàng ngang ở Home và lưới ở Category.
 *
 * Chiều cao phần tên cố định để lưới không xô lệch khi tên game dài ngắn khác nhau —
 * tên ROM hack thường rất dài nên đây là trường hợp thật, không phải phòng xa.
 */
@Composable
fun GameCard(
    game: GameEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    // Theo dõi cả 2 nguồn để thẻ tự bỏ khoá ngay khi user xem xong quảng cáo hoặc mua gói.
    val unlockedIds by UnlockStore.unlockedIds.collectAsState()
    val isPremium by PremiumState.isPremium.collectAsState()
    val locked = remember(game.id, unlockedIds, isPremium) { !UnlockStore.isUnlocked(game) }

    Column(modifier.pressScaleCombined(onClick = onClick, onLongClick = onLongClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RadiusCard)
                .border(1.dp, BorderSubtle, RadiusCard),
        ) {
            GameCover(game, Modifier.fillMaxSize())

            // Phủ tối chân ảnh để badge/biểu tượng luôn đọc được trên mọi ảnh bìa.
            Box(Modifier.fillMaxSize().background(ScrimGradient))

            PillChip(
                label = game.system.shortName,
                accent = game.system.accent,
                selected = true,
                height = 22.dp,
                horizontalPadding = 8.dp,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            )

            // Đặt ở đáy trái (không phải đỉnh phải) vì trên lưới 3 cột, card chỉ rộng ~100.dp —
            // badge hệ máy ở đỉnh trái và badge SAMPLE ở đỉnh phải sẽ chồng lên nhau.
            if (game.isSample) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RadiusPill)
                        .background(BgRoot.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text("SAMPLE", style = LabelPixel, color = NeonMagenta, maxLines = 1)
                }
            }

            // Phải cho thấy game đang khoá TRƯỚC khi bấm, không thì user tưởng bị lừa.
            if (locked) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RadiusPill)
                        .background(BgRoot.copy(alpha = 0.78f))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = stringResource(R.string.badge_locked),
                        tint = NeonAmber,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }

            if (game.isFavourite) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Favourite",
                    tint = NeonAmber,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(16.dp),
                )
            }
        }

        Text(
            text = game.title,
            style = TitleMedium,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 8.dp)
                .heightIn(min = 40.dp),
        )
    }
}

/**
 * Ảnh bìa game. Ảnh 404 là trạng thái BÌNH THƯỜNG (game homebrew/ROM hack không có trong kho ảnh
 * của libretro) nên không hiện icon lỗi — rơi thẳng về placeholder gradient theo màu hệ máy.
 */
@Composable
fun GameCover(game: GameEntity, modifier: Modifier = Modifier) {
    if (game.coverUrl.isNullOrBlank()) {
        GameCoverPlaceholder(game, modifier)
        return
    }
    // Kho libretro đặt tên theo chuẩn No-Intro nên link chính dựng từ tên file ROM. ROM tải ở
    // kho lạ thường lệch tên -> thử tiếp link dựng từ tiêu đề trước khi bỏ cuộc về placeholder.
    val fallbackUrl = remember(game.id) {
        CoverUrls.forTitle(game.system, game.title)?.takeIf { it != game.coverUrl }
    }

    SubcomposeAsyncImage(
        model = game.coverUrl,
        contentDescription = game.title,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = { GameCoverPlaceholder(game, Modifier.fillMaxSize()) },
        error = {
            if (fallbackUrl == null) {
                GameCoverPlaceholder(game, Modifier.fillMaxSize())
            } else {
                SubcomposeAsyncImage(
                    model = fallbackUrl,
                    contentDescription = game.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = { GameCoverPlaceholder(game, Modifier.fillMaxSize()) },
                    error = { GameCoverPlaceholder(game, Modifier.fillMaxSize()) },
                )
            }
        },
    )
}

/** Không dùng ô xám: gradient theo màu hệ máy + chữ viết tắt tên game. */
@Composable
fun GameCoverPlaceholder(game: GameEntity, modifier: Modifier = Modifier) {
    val accent = game.system.accent
    Box(
        modifier.background(
            Brush.linearGradient(
                listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.28f)),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = game.title.initials(),
            style = DisplayPixel,
            color = BgRoot.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(8.dp),
        )
    }
}

/**
 * Menu hành động khi nhấn giữ một card game.
 *
 * Cố ý neo giữa màn thay vì neo tại điểm chạm: card ở mép lưới sẽ khiến popup neo theo điểm chạm
 * bị tràn/cắt ở cạnh màn hình. Nền tối chặn tương tác phía sau để không bấm nhầm card khác.
 */
@Composable
fun GameActionsPopup(
    game: GameEntity,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavourite: () -> Unit,
    onDelete: () -> Unit,
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
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .padding(32.dp)
                    .width(260.dp)
                    .clip(RadiusCard)
                    .background(BgSurface)
                    .border(1.dp, BorderSubtle, RadiusCard)
                    .clickable(interactionSource = null, indication = null, onClick = {})
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    game.title,
                    style = TitleMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Spacer(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(BorderSubtle),
                )
                ActionRow(Icons.Filled.PlayArrow, "Play", NeonCyan, onClick = onPlay)
                ActionRow(
                    icon = if (game.isFavourite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    label = if (game.isFavourite) "Remove from favourites" else "Add to favourites",
                    tint = NeonAmber,
                    onClick = onToggleFavourite,
                )
                ActionRow(
                    icon = Icons.Filled.Delete,
                    label = if (game.isSample) "Bundled game — cannot delete" else "Delete from library",
                    tint = NeonRed,
                    enabled = !game.isSample,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            null,
            tint = if (enabled) tint else TextTertiary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            label,
            style = BodyMedium,
            color = if (enabled) TextPrimary else TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Lấy tối đa 3 ký tự đại diện: ưu tiên chữ cái đầu các từ, thiếu thì lấy đầu tên. */
private fun String.initials(): String {
    val words = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val fromWords = words.mapNotNull { it.firstOrNull { ch -> ch.isLetterOrDigit() } }
    return when {
        fromWords.size >= 2 -> fromWords.take(3).joinToString("").uppercase()
        else -> take(3).uppercase()
    }.ifBlank { "?" }
}
