package com.retropocket.gba.emulator.features.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retropocket.gba.emulator.BuildConfig
import com.retropocket.gba.emulator.core.AppConfig
import androidx.compose.ui.res.stringResource
import com.retropocket.gba.emulator.R
import com.retropocket.gba.emulator.features.common.PremiumState
import com.retropocket.gba.emulator.core.SettingManager
import com.retropocket.gba.emulator.corefeatures.emulator.CoreCatalog
import com.retropocket.gba.emulator.corefeatures.library.LibraryScanner
import com.retropocket.gba.emulator.data.database.repo.GameRepo
import com.retropocket.gba.emulator.ui.components.*
import com.retropocket.gba.emulator.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenIap: () -> Unit = {},
    onOpenLicenses: () -> Unit,
    onOpenLanguage: () -> Unit,
) {
    val games by GameRepo.games.collectAsStateWithLifecycle()
    val hapticEnabled by SettingManager.hapticEnabled.collectAsStateWithLifecycle()
    val isPremium by PremiumState.isPremium.collectAsStateWithLifecycle()
    val currentLocale by SettingManager.appLocale.collectAsStateWithLifecycle()
    val isScanning by GameRepo.isBusy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanMessage by remember { mutableStateOf<String?>(null) }

    val availableCores = remember { CoreCatalog.availableSystems() }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgRoot)
            .statusBarsPadding(),
    ) {
        AppTopBar(title = "SETTINGS", onBack = onBack)

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Đã trả phí thì không mời mua nữa.
            if (!isPremium) {
                item { SectionHeader("PREMIUM", accent = NeonAmber, modifier = Modifier.padding(vertical = 8.dp)) }
                item {
                    ListRow(
                        title = stringResource(R.string.premium_row_title),
                        subtitle = stringResource(R.string.premium_row_subtitle),
                        icon = Icons.Filled.WorkspacePremium,
                        iconTint = NeonAmber,
                        accent = NeonAmber,
                        height = 64.dp,
                        onClick = onOpenIap,
                        trailing = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                null,
                                tint = TextTertiary,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                }
            }

            item { SectionHeader("GAMEPLAY", modifier = Modifier.padding(vertical = 8.dp)) }

            item {
                ListRow(
                    title = "Haptic feedback",
                    subtitle = "Short vibration when you press a virtual button",
                    height = 64.dp,
                    icon = Icons.Filled.Vibration,
                    onClick = { SettingManager.setHapticEnabled(!hapticEnabled) },
                    trailing = { ToggleSwitch(checked = hapticEnabled) },
                )
            }

            item { SectionHeader("LIBRARY", modifier = Modifier.padding(vertical = 8.dp)) }

            item {
                ListRow(
                    title = "Rescan library",
                    subtitle = scanMessage ?: "${games.size} games indexed",
                    height = 64.dp,
                    icon = Icons.Filled.Refresh,
                    // Chặn bấm lại khi đang quét: gọi chồng scan() sẽ đếm trùng và nhấp nháy phụ đề.
                    onClick = if (isScanning) null else {
                        {
                            scope.launch {
                                scanMessage = "Scanning…"
                                val added = LibraryScanner.scan()
                                scanMessage =
                                    if (added > 0) "Added $added new game(s)" else "Library up to date"
                            }
                        }
                    },
                    trailing = {
                        if (isScanning) CircularProgressTrack(size = 18.dp, strokeWidth = 2.dp)
                    },
                )
            }

            item {
                ListRow(
                    title = "Emulator cores",
                    subtitle = "${availableCores.size} systems available in this build",
                    height = 64.dp,
                    icon = Icons.Filled.WorkspacePremium,
                )
            }

            item {
                ListRow(
                    title = "Language",
                    icon = Icons.Filled.Language,
                    onClick = onOpenLanguage,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                currentLocale.uppercase(),
                                style = LabelPixel,
                                color = TextSecondary,
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                null,
                                tint = TextTertiary,
                                modifier = Modifier.padding(start = 4.dp).size(20.dp),
                            )
                        }
                    },
                )
            }

            item { SectionHeader("ABOUT", modifier = Modifier.padding(vertical = 8.dp)) }

            item {
                ListRow(
                    title = "Privacy policy",
                    subtitle = "Opens in your browser",
                    icon = Icons.Filled.PrivacyTip,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.PRIVACY_POLICY_URL)),
                            )
                        }
                    },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            null,
                            tint = TextTertiary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }

            item {
                ListRow(
                    title = "Open source licenses",
                    subtitle = "Games, cores and libraries used by this app",
                    height = 64.dp,
                    icon = Icons.Filled.Info,
                    onClick = onOpenLicenses,
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            null,
                            tint = TextTertiary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }

            item {
                ListRow(
                    title = "Version",
                    icon = Icons.Filled.Info,
                    trailing = {
                        Text(BuildConfig.VERSION_NAME, style = LabelPixel, color = TextSecondary)
                    },
                )
            }

            item { ScrollTailSpacer() }
        }
    }
}

/** Công tắc tự dựng — không dùng Switch của Material để giữ đúng ngôn ngữ thị giác neon. */
@Composable
private fun ToggleSwitch(checked: Boolean) {
    Box(
        Modifier
            .width(44.dp)
            .height(24.dp)
            .clip(RadiusPill)
            .background(if (checked) NeonCyan.copy(alpha = 0.25f) else BgSurfacePress)
            .border(1.dp, if (checked) NeonCyan else BorderStrong, RadiusPill),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(if (checked) NeonCyan else TextTertiary),
        )
    }
}
