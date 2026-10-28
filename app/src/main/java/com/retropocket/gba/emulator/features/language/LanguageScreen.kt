package com.retropocket.gba.emulator.features.language

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retropocket.gba.emulator.R
import com.retropocket.gba.emulator.core.SettingManager
import com.retropocket.gba.emulator.FadeSlideHorizontal
import com.retropocket.gba.emulator.LogScreenView
import com.retropocket.gba.emulator.ads.AdsPlacementHolder
import com.retropocket.gba.emulator.ui.components.AppTopBar
import com.retropocket.gba.emulator.ui.components.ListRow
import com.retropocket.gba.emulator.ui.components.PillChip
import com.retropocket.gba.emulator.ui.components.ads.ComposeNativeAdFrame
import com.retropocket.gba.emulator.ui.theme.BgRoot
import com.retropocket.gba.emulator.ui.theme.BgSurfacePress
import com.retropocket.gba.emulator.ui.theme.NeonCyan
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class LanguageStep {
    Language,
    Region,
}

@Composable
fun LanguageScreen(
    selectRegion: Boolean = true,
    /** Chỉ có khi mở từ Settings — luồng khởi động lần đầu không cho quay lui. */
    onBack: (() -> Unit)? = null,
    onNext: () -> Unit = {},
) {
    // _1 cho lần mở app đầu tiên, _2 cho mọi lần sau — cùng quy ước với inter_splash / _2.
    val isFirstTime by SettingManager.isFirstTime.collectAsStateWithLifecycle()
    // Chỉ log khi màn nằm trong luồng khởi động (onBack == null). Mở từ Settings để đổi ngôn
    // ngữ không phải là "lần 2 vào app" nên không được tính vào language_2_view.
    if (onBack == null) {
        LogScreenView(if (isFirstTime) "language_1_view" else "language_2_view")
    }

    var currentStep by rememberSaveable {
        mutableStateOf(LanguageStep.Language)
    }

    var selectedLanguageLocaleId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var selectedRegionIndex by rememberSaveable {
        mutableIntStateOf(-1)
    }

    val selectedLanguage = remember(selectedLanguageLocaleId) {
        supportedLanguages.find { it.localeId == selectedLanguageLocaleId }
    }

    BackHandler(currentStep == LanguageStep.Region,) {
        selectedRegionIndex = -1
        currentStep = LanguageStep.Language
    }

    AnimatedContent(
        targetState = currentStep,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            when {
                initialState == LanguageStep.Language && targetState == LanguageStep.Region ->
                    FadeSlideHorizontal.first togetherWith ExitTransition.KeepUntilTransitionsFinished
                else -> EnterTransition.None togetherWith ExitTransition.None
            }
        },
        label = "LanguageStepAnimation",
    ) { step ->

        when (step) {
            LanguageStep.Language -> {
                LanguageSelectionContent(
                    selectedLocaleId = selectedLanguageLocaleId,
                    onBack = onBack,
                    onLanguageSelected = { localeId ->
                        selectedLanguageLocaleId = localeId
                        SettingManager.setAppLocale(localeId)
                    },
                    onNext = {
                        val language = selectedLanguage

                        if (
                            selectRegion &&
                            language != null &&
                            language.regions.isNotEmpty()
                        ) {
                            currentStep = LanguageStep.Region
                        } else {
                            onNext()
                        }
                    },
                )
            }

            LanguageStep.Region -> {
                val language = selectedLanguage
                    ?: return@AnimatedContent

                RegionSelectionContent(
                    regions = language.regions,
                    selectedRegionIndex = selectedRegionIndex,
                    onRegionSelected = {
                        selectedRegionIndex = it
                    },
                    onNext = {
                        val region =
                            language.regions.getOrNull(selectedRegionIndex)

                        SettingManager.setAppLocale(
                            region?.localeId ?: language.localeId
                        )

                        onNext()
                    },
                )
            }
        }
    }
}

@Composable
private fun LanguageSelectionContent(
    selectedLocaleId: String?,
    onLanguageSelected: (String) -> Unit,
    onNext: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var showNextButton by remember { mutableStateOf(false) }
    var hasReloadedAd by remember { mutableStateOf(false) }
    var reloadNativeAd by remember { mutableStateOf<(String?) -> Unit>({}) }

    LaunchedEffect(selectedLocaleId) {
        if (selectedLocaleId != null) {
            showNextButton = false
            delay(500)
            showNextButton = true
        } else {
            showNextButton = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgRoot)
            .navigationBarsPadding()
            .statusBarsPadding(),
    ) {
        AppTopBar(
            title = stringResource(R.string.language).uppercase(),
            titleColor = NeonCyan,
            onBack = onBack,
            actions = {
                AnimatedVisibility(
                    visible = showNextButton,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    PillChip(
                        label = stringResource(R.string.next).uppercase(),
                        selected = true,
                        onClick = onNext,
                    )
                }
            },
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(supportedLanguages, key = { it.localeId }) { language ->
                LanguageItemCard(
                    language = language,
                    isSelected = selectedLocaleId == language.localeId,
                    onClick = {
                        onLanguageSelected(language.localeId)
                        // Sample: swap in a fresh impression of the same placement the first
                        // time the user picks something. Apps forked from this template can
                        // point this at a distinct placement (e.g. a "2nd" native slot).
                        if (!hasReloadedAd) {
                            hasReloadedAd = true
                            reloadNativeAd(AdsPlacementHolder.NATIVE_LANGUAGE_RELOAD)
                        }
                    },
                )
            }
            item {
                Spacer(modifier = Modifier.height(240.dp))
            }
        }
        ComposeNativeAdFrame(
            modifier = Modifier.fillMaxWidth(),
            // Lần đầu cài app và các lần mở sau dùng 2 ô khác nhau; rời app rồi quay lại
            // thì nạp ô _reload.
            adPlacement = if (SettingManager.isFirstTime.value) {
                AdsPlacementHolder.NATIVE_LANGUAGE_1_1
            } else {
                AdsPlacementHolder.NATIVE_LANGUAGE_1_2
            },
            resumePlacement = AdsPlacementHolder.NATIVE_LANGUAGE_RELOAD,
            onReloadReady = { reloadNativeAd = it },
        )
    }
}

@Composable
private fun RegionSelectionContent(
    regions: List<Region>,
    selectedRegionIndex: Int,
    onRegionSelected: (Int) -> Unit,
    onNext: () -> Unit,
) {
    var showNextButton by remember { mutableStateOf(false) }
    var hasReloadedAd by remember { mutableStateOf(false) }
    var reloadNativeAd by remember { mutableStateOf<(String?) -> Unit>({}) }

    LaunchedEffect(selectedRegionIndex) {
        if (selectedRegionIndex >= 0) {
            showNextButton = false
            delay(500)
            showNextButton = true
        } else {
            showNextButton = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgRoot)
            .navigationBarsPadding()
            .statusBarsPadding(),
    ) {
        AppTopBar(
            title = stringResource(R.string.select_region).uppercase(),
            titleColor = NeonCyan,
            actions = {
                AnimatedVisibility(
                    visible = showNextButton,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    PillChip(
                        label = stringResource(R.string.next).uppercase(),
                        selected = true,
                        onClick = onNext,
                    )
                }
            },
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(regions, key = { index, region -> index }) { index, region ->
                RegionItemCard(
                    region = region,
                    isSelected = selectedRegionIndex == index,
                    onClick = {
                        onRegionSelected(index)
                        // Sample: swap in a fresh impression of the same placement the first
                        // time the user picks something. Apps forked from this template can
                        // point this at a distinct placement (e.g. a "2nd" native slot).
                        if (!hasReloadedAd) {
                            hasReloadedAd = true
                            reloadNativeAd(AdsPlacementHolder.NATIVE)
                        }
                    },
                )
            }
            item {
                Spacer(modifier = Modifier.height(240.dp))
            }
        }
        ComposeNativeAdFrame(
            modifier = Modifier.fillMaxWidth(),
            adPlacement = AdsPlacementHolder.NATIVE,
            reloadOnResume = false,
            onReloadReady = { reloadNativeAd = it },
        )
    }
}

/**
 * Dùng chung [ListRow] như mọi hàng danh sách khác trong app (Import/Settings/Menu) thay vì tự
 * dựng Row riêng — giữ đúng một chiều cao, một bo góc, một kiểu viền chọn trên toàn app.
 */
@Composable
private fun LanguageItemCard(
    language: Language,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    ListRow(
        title = language.text,
        selected = isSelected,
        onClick = onClick,
        leading = { FlagAvatar(language.resId, language.text) },
        trailing = { DonutRadioButton(selected = isSelected) },
    )
}

@Composable
private fun RegionItemCard(
    region: Region,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    ListRow(
        title = region.text,
        selected = isSelected,
        onClick = onClick,
        leading = { FlagAvatar(region.resId, region.text) },
        trailing = { DonutRadioButton(selected = isSelected) },
    )
}

@Composable
private fun FlagAvatar(resId: Int, description: String) {
    Image(
        painter = painterResource(resId),
        contentDescription = description,
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun DonutRadioButton(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) NeonCyan else BgSurfacePress),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(BgRoot),
            )
        }
    }
}
