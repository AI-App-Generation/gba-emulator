package com.retropocket.gba.emulator.features.category

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retropocket.gba.emulator.data.database.repo.GameRepo
import com.retropocket.gba.emulator.R
import com.retropocket.gba.emulator.ads.AdsPlacementHolder
import com.retropocket.gba.emulator.data.domain.GameSystem
import com.retropocket.gba.emulator.ui.components.*
import com.retropocket.gba.emulator.ui.components.ads.ComposeNativeAdFrame
import com.retropocket.gba.emulator.ui.theme.*
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import com.retropocket.gba.emulator.core.AppCapability
import com.retropocket.gba.emulator.corefeatures.library.UnlockStore
import com.retropocket.gba.emulator.features.common.PremiumState

@Composable
fun CategoryScreen(
    onOpenGame: (String) -> Unit,
    onBrowseStore: () -> Unit,
    onOpenIap: () -> Unit = {},
) {
    val games by GameRepo.games.collectAsStateWithLifecycle()
    val isScanning by GameRepo.isBusy.collectAsStateWithLifecycle()

    var selectedSystem by rememberSaveable { mutableStateOf<String?>(null) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var actionsForGameId by remember { mutableStateOf<String?>(null) }

    // Chỉ hiện tab của hệ máy user THỰC SỰ có game, tránh danh sách tab dài vô nghĩa.
    val ownedSystems = remember(games) {
        games.groupingBy { it.systemId }.eachCount().toList().sortedByDescending { it.second }
    }

    // Hệ máy đang chọn có thể biến mất khi user xoá game cuối cùng của hệ đó — nếu không tự đưa
    // về "ALL" thì lưới sẽ rỗng vĩnh viễn mà không có tab nào đang sáng.
    LaunchedEffect(ownedSystems) {
        if (selectedSystem != null && ownedSystems.none { it.first == selectedSystem }) {
            selectedSystem = null
        }
    }

    val filtered = remember(games, selectedSystem, query) {
        games.filter { game ->
            (selectedSystem == null || game.systemId == selectedSystem) &&
                (query.isBlank() || game.title.contains(query, ignoreCase = true))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgRoot)
            .statusBarsPadding(),
    ) {
        AppTopBar(
            title = "LIBRARY",
            titleColor = NeonCyan,
            actions = {
                SlotCounter(onClick = onOpenIap)
                Spacer(Modifier.width(8.dp))
                IconCircleButton(
                    icon = if (searchActive) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = "Search",
                    onClick = {
                        searchActive = !searchActive
                        if (!searchActive) query = ""
                    },
                )
            },
        )

        if (searchActive) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // Thư viện rỗng thì hàng tab chỉ còn mỗi chip "ALL 0" vô nghĩa — ẩn hẳn đi.
        if (games.isNotEmpty()) LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            item(key = "all") {
                PillChip(
                    label = "ALL",
                    selected = selectedSystem == null,
                    trailingCount = games.size,
                    onClick = { selectedSystem = null },
                )
            }
            items(ownedSystems, key = { it.first }) { (systemId, count) ->
                val system = GameSystem.byIdName(systemId)
                PillChip(
                    label = system.shortName,
                    leadingDot = system.accent,
                    selected = selectedSystem == systemId,
                    accent = system.accent,
                    trailingCount = count,
                    onClick = { selectedSystem = systemId },
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (isScanning) {
                    // Đang quét mà đã báo "chưa có game nào" là nói sai trạng thái.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressTrack(size = 28.dp)
                        Spacer(Modifier.size(14.dp))
                        Text("SCANNING LIBRARY…", style = LabelPixel, color = TextSecondary)
                    }
                } else if (query.isNotBlank()) {
                    EmptyState(
                        icon = Icons.Filled.Search,
                        title = "No match for \"$query\"",
                        description = "Try another keyword, or clear the search to see everything.",
                    )
                } else {
                    EmptyState(
                        icon = Icons.Filled.SportsEsports,
                        title = "No games here yet",
                        description = "Import or download ROMs to fill this shelf.",
                        actionLabel = "GO TO STORE",
                        onAction = onBrowseStore,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                // Không có padding trên thì hàng thẻ đầu tiên dán thẳng vào hàng tab phía trên.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Chèn quảng cáo sau mỗi 2 hàng (6 thẻ), chiếm trọn một hàng. Thư viện dưới 6
                // game thì không chèn — nửa màn hình là quảng cáo trông như app rác.
                val adEvery = 6
                val showInlineAds = filtered.size >= adEvery
                filtered.forEachIndexed { index, game ->
                    item(key = game.id) {
                        GameCard(
                            game = game,
                            onClick = { onOpenGame(game.id) },
                            onLongClick = { actionsForGameId = game.id },
                        )
                    }
                    val isBlockEnd = (index + 1) % adEvery == 0
                    if (showInlineAds && isBlockEnd && index != filtered.lastIndex) {
                        item(
                            key = "ad_$index",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            ComposeNativeAdFrame(
                                adPlacement = AdsPlacementHolder.NATIVE_MY_GAME,
                                layoutResId = R.layout.frame_ad_small,
                                templateAdLayoutRes = R.layout.template_ad_small,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { ScrollTailSpacer() }
            }
        }
    }

    val actionGame = games.firstOrNull { it.id == actionsForGameId }
    if (actionGame != null) {
        GameActionsPopup(
            game = actionGame,
            onDismiss = { actionsForGameId = null },
            onPlay = {
                actionsForGameId = null
                onOpenGame(actionGame.id)
            },
            onToggleFavourite = {
                GameRepo.toggleFavourite(actionGame)
                actionsForGameId = null
            },
            onDelete = {
                GameRepo.delete(actionGame)
                actionsForGameId = null
            },
        )
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Mở ô tìm kiếm mà không có con trỏ + bàn phím thì user phải chạm thêm một lần vô nghĩa.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RadiusPill)
            .background(BgSurfaceHigh)
            .border(1.dp, BorderSubtle, RadiusPill)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text("Search games…", style = BodyMedium, color = TextTertiary)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(BodyMedium).copy(color = TextPrimary),
                cursorBrush = SolidColor(NeonCyan),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
    }
}

/**
 * Bộ đếm slot mở khoá trên top bar thư viện.
 *
 * Đặt ở đây vì thư viện là nơi user nhìn thấy ổ khoá, nên cũng là nơi câu hỏi "còn mở được mấy
 * game nữa" nảy ra. Ẩn hẳn khi đã trả phí: không còn giới hạn thì không có gì để đếm, mà nhắc lại
 * chuyện gói cước với người vừa trả tiền là thừa.
 */
@Composable
private fun SlotCounter(onClick: () -> Unit) {
    val isPremium by PremiumState.isPremium.collectAsStateWithLifecycle()
    if (isPremium) return

    val unlockedIds by UnlockStore.unlockedIds.collectAsStateWithLifecycle()
    val used = unlockedIds.size
    val isFull = used >= AppCapability.FREE_UNLOCK_SLOTS
    // Đầy thì chuyển vàng để gợi hành động, còn lại giữ lam cho hoà vào top bar.
    val accent = if (isFull) NeonAmber else NeonCyan

    Row(
        modifier = Modifier
            .height(28.dp)
            .pressScale(onClick = onClick)
            .clip(RadiusPill)
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.5f), RadiusPill)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isFull) Icons.Filled.Lock else Icons.Filled.LockOpen,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$used/${AppCapability.FREE_UNLOCK_SLOTS}",
            style = LabelPixel,
            color = accent,
        )
    }
}
