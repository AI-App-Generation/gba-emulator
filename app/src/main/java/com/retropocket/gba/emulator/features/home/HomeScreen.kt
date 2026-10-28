package com.retropocket.gba.emulator.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retropocket.gba.emulator.data.database.entity.GameEntity
import androidx.compose.material.icons.filled.WorkspacePremium
import com.retropocket.gba.emulator.features.common.PremiumState
import com.retropocket.gba.emulator.LogScreenView
import com.retropocket.gba.emulator.core.Analytics
import com.retropocket.gba.emulator.data.database.repo.GameRepo
import com.retropocket.gba.emulator.ui.components.*
import com.retropocket.gba.emulator.ui.theme.*

@Composable
fun HomeScreen(
    onOpenGame: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenIap: () -> Unit = {},
    onBrowseStore: () -> Unit,
) {
    val games by GameRepo.games.collectAsStateWithLifecycle()
    val isPremium by PremiumState.isPremium.collectAsStateWithLifecycle()
    val isScanning by GameRepo.isBusy.collectAsStateWithLifecycle()

    val samples = games.filter { it.isSample }
    LogScreenView("home_view")

    val favourites = games.filter { it.isFavourite }
    val recents = games.filter { it.lastPlayedAt != null }.sortedByDescending { it.lastPlayedAt }
    val continueGame = recents.firstOrNull()
    val hasOnlySamples = games.isNotEmpty() && games.all { it.isSample }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgRoot)
            .statusBarsPadding(),
    ) {
        AppTopBar(
            title = "GAME EMULATOR",
            titleColor = NeonCyan,
            subtitle = when {
                isScanning -> "Scanning library…"
                games.isEmpty() -> "Library is empty"
                else -> "${games.size} games in library"
            },
            actions = {
                // Ẩn hẳn khi đã trả phí — mời mua lần nữa là phản cảm.
                if (!isPremium) {
                    IconCircleButton(
                        icon = Icons.Filled.WorkspacePremium,
                        contentDescription = "Premium",
                        tint = NeonAmber,
                        borderColor = NeonAmber,
                        glowColor = NeonAmber,
                        onClick = {
                            Analytics.log("plus_click")
                            onOpenIap()
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                IconCircleButton(
                    icon = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    onClick = onOpenSettings,
                )
            },
        )

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (continueGame != null) {
                item(key = "continue") {
                    ContinuePlayingHero(continueGame) { onOpenGame(continueGame.id) }
                }
            }

            if (samples.isNotEmpty()) {
                item(key = "samples") {
                    GameRowSection("SAMPLE GAMES", samples, onOpenGame)
                }
            }

            if (favourites.isNotEmpty()) {
                item(key = "favourites") {
                    GameRowSection("FAVOURITES", favourites, onOpenGame)
                }
            }

            if (recents.isNotEmpty()) {
                item(key = "recents") {
                    GameRowSection("RECENT", recents.take(10), onOpenGame)
                }
            }

            // Đang quét mà đã hiện "thư viện của bạn trống" thì user tưởng mất hết game — chỉ
            // hiện lời mời sau khi biết chắc thư viện thật sự thiếu.
            if (!isScanning && (games.isEmpty() || hasOnlySamples)) {
                item(key = "callout") {
                    EmptyLibraryCallout(isCompletelyEmpty = games.isEmpty(), onBrowseStore = onBrowseStore)
                }
            }

            item(key = "tail") { ScrollTailSpacer() }
        }
    }
}

/** Ô lớn nhắc tiếp tục game chơi gần nhất — điểm nhấn đầu màn Home. */
@Composable
private fun ContinuePlayingHero(game: GameEntity, onPlay: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(180.dp)
            .clip(RadiusCard)
            .border(1.dp, BorderSubtle, RadiusCard),
    ) {
        GameCover(game, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(ScrimGradient))

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .fillMaxWidth(0.7f),
        ) {
            Text("CONTINUE", style = LabelPixel, color = NeonCyan)
            Spacer(Modifier.height(6.dp))
            Text(
                game.title,
                style = TitleLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(48.dp)
                .pressScale(onClick = onPlay)
                .neonGlow(NeonCyan, radius = 14.dp, alpha = 0.5f)
                .clip(CircleShape)
                .background(NeonCyan),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, "Play", tint = BgRoot, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun GameRowSection(
    title: String,
    games: List<GameEntity>,
    onOpenGame: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(
            title = title,
            trailingText = "${games.size}",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            Modifier.padding(top = 12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(games, key = { it.id }) { game ->
                GameCard(
                    game = game,
                    modifier = Modifier.width(128.dp),
                    onClick = { onOpenGame(game.id) },
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryCallout(isCompletelyEmpty: Boolean, onBrowseStore: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RadiusCard)
            .background(BgSurface)
            .border(1.dp, BorderSubtle, RadiusCard)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.CloudDownload,
            contentDescription = null,
            tint = NeonCyanDim,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (isCompletelyEmpty) "Your library is empty" else "Add more games",
            style = TitleMedium,
            color = TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Browse the built-in store or import ROM archives you already downloaded.",
            style = BodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryPixelButton(
            "BROWSE GAMES",
            onClick = {
                Analytics.log("click_browse_games")
                onBrowseStore()
            },
        )
    }
}
