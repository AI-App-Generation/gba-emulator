package com.retropocket.gba.emulator.features.downloads

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.retropocket.gba.emulator.R
import com.retropocket.gba.emulator.features.main.NAV_BAR_HEIGHT_DP
import com.retropocket.gba.emulator.ui.components.*
import com.retropocket.gba.emulator.ui.theme.*

/**
 * Màn chắn trước kho game: WebView CHƯA được tạo khi màn này còn hiện.
 *
 * Gộp 2 việc vào một lần chạm: user đọc miễn trừ trách nhiệm về nội dung tự nhập vào app, và
 * cấp quyền đọc Downloads để import không phải chọn file thủ công. Với Android 11+ màn này cũng
 * đóng vai trò "prominent disclosure" mà Google Play yêu cầu trước khi xin All files access.
 */
@Composable
fun StoreAccessGate(
    opensSettings: Boolean,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .size(72.dp)
                .neonGlow(NeonCyan, radius = 16.dp, alpha = 0.3f)
                .clip(CircleShape)
                .background(BgSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Shield, null, tint = NeonCyan, modifier = Modifier.size(32.dp))
        }

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.store_gate_title),
            style = TitleLarge,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.store_gate_subtitle),
            style = BodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))
        GateCard(
            icon = Icons.Filled.Gavel,
            accent = NeonAmber,
            title = stringResource(R.string.store_gate_disclaimer_title),
            body = stringResource(R.string.store_gate_disclaimer_body),
        )

        Spacer(Modifier.height(12.dp))
        GateCard(
            icon = Icons.Filled.FolderOpen,
            accent = NeonCyan,
            title = stringResource(R.string.store_gate_permission_title),
            body = stringResource(R.string.store_gate_permission_body),
            // Android 11+ không có dialog mà nhảy sang Settings — báo trước để user không tưởng
            // app bị văng ra ngoài.
            footnote = if (opensSettings) stringResource(R.string.store_gate_settings_hint) else null,
        )

        Spacer(Modifier.height(24.dp))
        PrimaryPixelButton(
            text = stringResource(R.string.store_gate_grant),
            modifier = Modifier.fillMaxWidth(),
            onClick = onGrant,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.store_gate_footer),
            style = Caption,
            color = TextTertiary,
            textAlign = TextAlign.Center,
        )

        // Bottom nav của app đè lên nội dung nên phải chừa đúng chiều cao của nó, nếu không
        // nút cấp quyền bị che trên máy màn thấp hoặc khi chữ dịch dài.
        Spacer(
            Modifier
                .navigationBarsPadding()
                .height(NAV_BAR_HEIGHT_DP.dp + 24.dp),
        )
    }
}

@Composable
private fun GateCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    body: String,
    footnote: String? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RadiusCard)
            .background(BgSurface)
            .border(1.dp, accent.copy(alpha = 0.35f), RadiusCard)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RadiusSmall)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Text(title, style = TitleMedium, color = TextPrimary)
        }
        Spacer(Modifier.height(10.dp))
        Text(body, style = BodySmall, color = TextSecondary)
        if (footnote != null) {
            Spacer(Modifier.height(8.dp))
            Text(footnote, style = Caption, color = accent)
        }
    }
}
