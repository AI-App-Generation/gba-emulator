package com.retropocket.gba.emulator.features.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.retropocket.gba.emulator.R
import com.retropocket.gba.emulator.features.main.NAV_BAR_HEIGHT_DP
import com.retropocket.gba.emulator.ui.components.PrimaryPixelButton
import com.retropocket.gba.emulator.ui.components.SecondaryPixelButton

/**
 * Màn Store TẠM cho bản phát hành đầu: chỉ 2 lối vào, không nhúng kho ROM nào trong app để hạn
 * chế rủi ro chính sách Google Play. Kho WebView vẫn còn nguyên trong [DownloadsScreen], chỉ tạm
 * không có đường vào.
 */
@Composable
fun StoreHub(
    onGetGames: () -> Unit,
    onImportGames: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp)
            // Bottom nav đè lên nội dung: chừa đúng phần bị che để 2 nút nằm giữa vùng NHÌN THẤY,
            // không phải giữa cả màn hình (sẽ trông lệch xuống dưới).
            .navigationBarsPadding()
            .padding(bottom = NAV_BAR_HEIGHT_DP.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PrimaryPixelButton(
            text = stringResource(R.string.store_get_games),
            modifier = Modifier.fillMaxWidth(),
            onClick = onGetGames,
        )
        Spacer(Modifier.height(16.dp))
        // Cùng chiều cao 52.dp với nút chính để 2 nút xếp chồng thẳng hàng.
        SecondaryPixelButton(
            text = stringResource(R.string.store_import_games),
            modifier = Modifier.fillMaxWidth(),
            height = 52.dp,
            onClick = onImportGames,
        )
    }
}
