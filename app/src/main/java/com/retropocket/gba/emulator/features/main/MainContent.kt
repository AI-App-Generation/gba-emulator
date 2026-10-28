package com.retropocket.gba.emulator.features.main

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.retropocket.gba.emulator.R
import com.retropocket.gba.emulator.ui.components.ExitConfirmDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retropocket.gba.emulator.core.Analytics
import com.retropocket.gba.emulator.data.database.repo.GameRepo
import com.retropocket.gba.emulator.ads.AdsPlacementHolder
import com.retropocket.gba.emulator.ads.AdsPolicy
import com.retropocket.gba.emulator.features.category.CategoryScreen
import com.retropocket.gba.emulator.features.common.PlayGateHost
import com.retropocket.gba.emulator.features.common.rememberPlayGate
import com.retropocket.gba.emulator.features.downloads.DownloadsScreen
import com.retropocket.gba.emulator.features.home.HomeScreen
import com.retropocket.gba.emulator.features.mydownloads.MyDownloadsScreen
import com.retropocket.gba.emulator.ui.components.ads.AdBanner
import com.retropocket.gba.emulator.ui.components.RadiusSmall
import com.retropocket.gba.emulator.ui.components.neonGlow
import com.retropocket.gba.emulator.ui.theme.*

enum class MainTab(val label: String, val icon: ImageVector) {
    Home("HOME", Icons.Filled.Home),
    Store("STORE", Icons.Filled.Storefront),
    Downloads("DOWNLOADS", Icons.Filled.Download),
    Category("LIBRARY", Icons.Filled.GridView),
}

const val NAV_BAR_HEIGHT_DP = 64

/**
 * Khung chứa 4 tab chính. Tự dựng thay vì dùng Scaffold.
 *
 * Content tràn hết màn hình và nav bar vẽ đè lên trên — mỗi tab tự chịu trách nhiệm chừa đáy
 * (màn cuộn dùng spacer cuối danh sách, màn Downloads có WebView phải chừa padding thủ công).
 */
@Composable
fun MainContent(
    onOpenGame: (String) -> Unit,
    onOpenIap: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenSettings: () -> Unit,
    /** Tab mà một màn khác (ví dụ Import) yêu cầu mở khi quay lại; `null` là không yêu cầu gì. */
    requestedTab: MainTab? = null,
    onRequestedTabConsumed: () -> Unit = {},
) {
    // Cổng "phải sở hữu mới chơi" đặt tập trung ở đây: mọi thẻ game ở Home, Library và
    // Downloads đều đi qua openGame bên dưới, khỏi rải logic ở từng màn.
    val activity = LocalActivity.current
    val games by GameRepo.games.collectAsStateWithLifecycle()
    val playGate = rememberPlayGate()
    val openGame: (String) -> Unit = { id ->
        // Đo Ý ĐỊNH chơi nên bắn trước PlayGate: bấm vào game rồi bị chặn vẫn là một lượt bấm,
        // và chênh lệch giữa game_click với game_view chính là số lượt bị cổng chặn giữ lại.
        val game = games.firstOrNull { it.id == id }
        Analytics.log(
            "game_click",
            "game" to (game?.title ?: id),
            "system" to (game?.systemId ?: "unknown"),
        )
        if (game == null) onOpenGame(id) else playGate.request(game) { onOpenGame(id) }
    }

    var tabOrdinal by rememberSaveable { mutableIntStateOf(MainTab.Home.ordinal) }
    var showExitConfirm by remember { mutableStateOf(false) }

    // inter_home: quay về Home từ BẤT KỲ tab nào cũng tính, không riêng đường thoát game.
    // Không force nên vẫn chịu khoảng cách 10 giây trong AdsPolicy — chuyển tab qua lại liên
    // tục sẽ không ăn quảng cáo liên tục.
    var previousTabOrdinal by remember { mutableIntStateOf(tabOrdinal) }
    LaunchedEffect(tabOrdinal) {
        val cameFromAnotherTab = previousTabOrdinal != tabOrdinal
        previousTabOrdinal = tabOrdinal
        if (cameFromAnotherTab && tabOrdinal == MainTab.Home.ordinal && activity != null) {
            AdsPolicy.showInterAd(
                activity = activity,
                adPlacement = AdsPlacementHolder.INTER_HOME,
                onContinue = {},
            )
        }
    }
    val currentTab = MainTab.entries[tabOrdinal]

    LaunchedEffect(requestedTab) {
        requestedTab?.let {
            tabOrdinal = it.ordinal
            onRequestedTabConsumed()
        }
    }

    // Đang ở tab khác thì Back đưa về Home trước; ở Home mới hỏi thoát app.
    BackHandler {
        when {
            showExitConfirm -> showExitConfirm = false
            currentTab != MainTab.Home -> tabOrdinal = MainTab.Home.ordinal
            else -> showExitConfirm = true
        }
    }

    Box(Modifier.fillMaxSize().background(BgRoot)) {
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
            label = "main_tab",
        ) { tab ->
            when (tab) {
                MainTab.Home -> HomeScreen(
                    onOpenGame = openGame,
                    onOpenIap = onOpenIap,
                    onOpenSettings = onOpenSettings,
                    onBrowseStore = { tabOrdinal = MainTab.Store.ordinal },
                )

                MainTab.Store -> DownloadsScreen(
                    onOpenImport = onOpenImport,
                    onImported = { tabOrdinal = MainTab.Category.ordinal },
                )

                MainTab.Downloads -> MyDownloadsScreen(
                    onOpenImport = onOpenImport,
                    onOpenGame = openGame,
                )

                MainTab.Category -> CategoryScreen(
                    onOpenGame = openGame,
                    onOpenIap = onOpenIap,
                    onBrowseStore = { tabOrdinal = MainTab.Store.ordinal },
                )
            }
        }

        PlayGateHost(gate = playGate, activity = activity, onOpenPremium = onOpenIap)

        if (showExitConfirm) {
            ExitConfirmDialog(
                title = stringResource(R.string.exit_app_title),
                description = stringResource(R.string.exit_app_desc),
                confirmLabel = stringResource(R.string.exit_app_confirm),
                onConfirm = {
                    showExitConfirm = false
                    activity?.finish()
                },
                onDismiss = { showExitConfirm = false },
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(BgSurface)
                .navigationBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth().height(NAV_BAR_HEIGHT_DP.dp)) {
                MainTab.entries.forEach { tab ->
                    BottomNavItem(
                        tab = tab,
                        selected = tab == currentTab,
                        modifier = Modifier.weight(1f),
                        onClick = { tabOrdinal = tab.ordinal },
                    )
                }
            }

            // Banner nằm DƯỚI thanh điều hướng, trên vùng thanh hệ thống. Ở tab Store thì bỏ hẳn
            // khỏi cây giao diện chứ không chỉ ẩn, để WebView không bị chừa một dải trống.
            if (currentTab != MainTab.Store) {
                AdBanner(placement = AdsPlacementHolder.BANNER_HOME)
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    tab: MainTab,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RadiusSmall)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .then(if (selected) Modifier.neonGlow(NeonCyan, radius = 10.dp, alpha = 0.5f) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                tab.icon,
                contentDescription = tab.label,
                tint = if (selected) NeonCyan else TextTertiary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        // 4 tab chia đều bề ngang: "DOWNLOADS" là nhãn dài nhất, không cho xuống dòng để cả
        // hàng nav luôn thẳng.
        Text(
            tab.label,
            style = LabelPixel,
            color = if (selected) NeonCyan else TextTertiary,
            maxLines = 1,
            softWrap = false,
        )
    }
}
