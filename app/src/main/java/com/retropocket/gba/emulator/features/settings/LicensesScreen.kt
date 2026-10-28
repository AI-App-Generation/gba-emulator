package com.retropocket.gba.emulator.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.retropocket.gba.emulator.corefeatures.library.SampleGameLicense
import com.retropocket.gba.emulator.corefeatures.library.SampleGames
import com.retropocket.gba.emulator.ui.components.*
import com.retropocket.gba.emulator.ui.theme.*

/**
 * Màn attribution bắt buộc.
 *
 * Game mẫu dùng license CC BY / CC BY-SA nên việc ghi rõ tên tác giả + license không phải tuỳ chọn
 * mà là điều kiện để được phép phân phối kèm app.
 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(BgRoot)
            .statusBarsPadding(),
    ) {
        AppTopBar(title = "LICENSES", onBack = onBack)

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Every bundled game is homebrew released under a license that allows " +
                        "redistribution. Credit stays with the original authors.",
                    style = BodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            item { SectionHeader("BUNDLED SAMPLE GAMES") }
            items(SampleGames.ALL) { LicenseCard(it) }

            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("CORES & LIBRARIES", accent = NeonMagenta)
            }
            items(SampleGames.LIBRARIES) { LicenseCard(it, accent = NeonMagenta) }

            item { ScrollTailSpacer() }
        }
    }
}

@Composable
private fun LicenseCard(item: SampleGameLicense, accent: androidx.compose.ui.graphics.Color = NeonCyanDim) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RadiusCard)
            .background(BgSurface)
            .border(1.dp, BorderSubtle, RadiusCard)
            .padding(14.dp),
    ) {
        Text(item.title, style = TitleMedium, color = TextPrimary)
        Spacer(Modifier.height(2.dp))
        Text("by ${item.author}", style = Caption, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Text(item.license, style = BodySmall, color = accent)
        Spacer(Modifier.height(4.dp))
        Text(item.url, style = Caption, color = TextTertiary)
    }
}
