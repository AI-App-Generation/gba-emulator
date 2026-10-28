package com.retropocket.gba.emulator.features.importer

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retropocket.gba.emulator.ads.AdsPlacementHolder
import com.retropocket.gba.emulator.R
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.retropocket.gba.emulator.data.database.repo.GameRepo
import com.retropocket.gba.emulator.core.file.FileManager
import com.retropocket.gba.emulator.corefeatures.importer.rememberStorageAccess
import com.retropocket.gba.emulator.ui.components.*
import com.retropocket.gba.emulator.ui.components.ads.ComposeNativeAdFrame
import com.retropocket.gba.emulator.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onImported: () -> Unit,
    viewModel: ImportViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()

    // Dùng chung với màn Store: bình thường user đã cấp quyền ở màn chắn của Store rồi, nhánh
    // xin quyền ở đây chỉ còn là phương án dự phòng khi quyền bị thu hồi giữa chừng.
    val access = rememberStorageAccess()
    // load() dùng cache của phiên nên gọi lại bao nhiêu lần cũng không quét thừa. Không còn tự
    // quét lại ở ON_RESUME: trước đây mở màn là quét 2 lần (LaunchedEffect + ON_RESUME bắn ngay
    // khi gắn observer) và mỗi lần quay lại app lại chớp loading.
    LaunchedEffect(access.isGranted) { viewModel.load() }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgRoot),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            AppTopBar(
                title = "IMPORT ROMS",
                onBack = onBack,
                actions = {
                    // Làm mới là cách DUY NHẤT để quét lại Downloads trong phiên.
                    if (uiState is ImportUiState.Ready) {
                        IconCircleButton(
                            icon = Icons.Filled.Refresh,
                            contentDescription = "Rescan Downloads",
                            tint = NeonCyan,
                            onClick = viewModel::refresh,
                        )
                    }
                    // Dùng PillChip (36.dp) chứ không phải Text trần: mọi phần tử trong top bar
                    // của app đều cao 36.dp, Text trần sẽ nằm sát mép và lệch so với các màn khác.
                    if (selected.isNotEmpty()) {
                        // Nhãn ngắn (icon + số) để không đẩy tiêu đề "IMPORT ROMS" vào ellipsis
                        // trên máy màn hẹp.
                        PillChip(
                            label = "${selected.size}",
                            icon = Icons.Filled.Check,
                            selected = true,
                            onClick = viewModel::clearSelection,
                        )
                    }
                },
            )

            when (val state = uiState) {
                ImportUiState.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    CircularProgressTrack()
                }

                is ImportUiState.NeedPermission -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    PermissionPrompt(allFilesAccess = state.allFilesAccess, onGrant = access::request)
                }

                is ImportUiState.Ready -> {
                    if (state.files.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                            EmptyState(
                                icon = Icons.Filled.Inbox,
                                title = "No archives found",
                                description = "Download a ROM first, then come back here to import it.",
                                actionLabel = "CHECK AGAIN",
                                onAction = viewModel::refresh,
                            )
                        }
                    } else {
                        LazyColumn(
                            Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item(key = "count") {
                                Text(
                                    "${state.files.size} archives found",
                                    style = Caption,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                            items(state.files, key = { it.uri.toString() }) { file ->
                                ListRow(
                                    title = file.name,
                                    subtitle = listOfNotNull(
                                        FileManager.readableSize(file.size),
                                        file.lastModified.readableDate(),
                                        file.relativeFolder?.let { "in $it" },
                                    ).joinToString(" · "),
                                    height = 64.dp,
                                    selected = file.uri in selected,
                                    leading = {
                                        Box(
                                            Modifier
                                                .size(36.dp)
                                                .clip(RadiusSmall)
                                                .background(NeonCyanDim.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Filled.FolderZip,
                                                null,
                                                tint = NeonCyan,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    },
                                    onClick = { viewModel.toggle(file.uri) },
                                    trailing = { CheckMark(checked = file.uri in selected) },
                                )
                            }
                            item(key = "tail") { ScrollTailSpacer() }
                        }
                    }
                }
            }
        }

        if (selected.isNotEmpty() && progress is ImportProgress.None) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                PrimaryPixelButton(
                    text = "IMPORT ${selected.size} FILE(S)",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = viewModel::importSelected,
                )
            }
        }

        if (progress !is ImportProgress.None) {
            ImportProgressPopup(
                progress = progress,
                onDone = {
                    viewModel.dismissProgress()
                    onImported()
                },
            )
        }
    }
}

@Composable
private fun PermissionPrompt(allFilesAccess: Boolean, onGrant: () -> Unit) {
    Column(
        Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(BgSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.FolderOpen, null, tint = NeonCyanDim, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Allow access to Downloads", style = TitleMedium, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            if (allFilesAccess) {
                "Android needs the \"All files access\" switch turned on before this app can " +
                    "read ROM archives you downloaded. It opens a settings page — turn the " +
                    "switch on, then come straight back."
            } else {
                "Allow read access once so the app can check Downloads directly."
            },
            style = BodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        PrimaryPixelButton(
            if (allFilesAccess) "OPEN SETTINGS" else "GRANT ACCESS",
            onClick = onGrant,
        )
    }
}

@Composable
private fun CheckMark(checked: Boolean) {
    Box(
        Modifier
            .size(22.dp)
            .clip(RadiusSmall)
            .background(if (checked) NeonCyan else BgSurfaceHigh)
            .border(1.dp, if (checked) NeonCyan else BorderStrong, RadiusSmall),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(Icons.Filled.Check, null, tint = BgRoot, modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * Popup chặn thao tác trong lúc giải nén — cố ý không cho dismiss bằng back hay bấm nền,
 * vì bỏ dở giữa chừng sẽ để lại file hỏng trong thư viện.
 */
@Composable
private fun ImportProgressPopup(progress: ImportProgress, onDone: () -> Unit) {
    Popup(
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(ScrimHeavy),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
                    .clip(RadiusCard)
                    .background(BgSurface)
                    .border(1.dp, BorderSubtle, RadiusCard)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (progress) {
                    is ImportProgress.Running -> {
                        // native_download: dialog này là chỗ user buộc phải chờ, đặt quảng cáo
                        // nhỏ ở đây thay vì để trống.
                        ComposeNativeAdFrame(
                            adPlacement = AdsPlacementHolder.NATIVE_DOWNLOAD,
                            layoutResId = R.layout.frame_ad_small,
                            templateAdLayoutRes = R.layout.template_ad_small,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        )
                        Text("EXTRACTING", style = TitlePixel, color = NeonCyan)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            progress.fileName,
                            style = BodySmall,
                            color = TextSecondary,
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        LinearProgressTrack(
                            modifier = Modifier.fillMaxWidth(),
                            progress = progress.percent / 100f,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${progress.done} / ${progress.total} · ${progress.percent}%",
                            style = LabelPixel,
                            color = TextTertiary,
                        )
                    }

                    is ImportProgress.Finished -> {
                        val allFailed = progress.addedCount == 0 && progress.failedCount > 0
                        Icon(
                            if (allFailed) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                            null,
                            tint = when {
                                allFailed -> NeonRed
                                progress.failedCount > 0 -> NeonAmber
                                else -> NeonGreen
                            },
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            when {
                                allFailed -> "IMPORT FAILED"
                                // Cùng một ROM import lại: CRC trùng nên không có bản ghi mới —
                                // phải nói rõ, nếu không "IMPORTED 0" trông như lỗi.
                                progress.addedCount == 0 -> "ALREADY IN LIBRARY"
                                else -> "IMPORTED ${progress.addedCount}"
                            },
                            style = TitlePixel,
                            color = TextPrimary,
                            textAlign = TextAlign.Center,
                        )
                        if (progress.failedCount > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${progress.failedCount} file(s) could not be read. They may be " +
                                    "damaged, or contain no supported ROM.",
                                style = BodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        PrimaryPixelButton("DONE", onClick = onDone)
                    }

                    ImportProgress.None -> Unit
                }
            }
        }
    }
}

private fun Long.readableDate(): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(this))
