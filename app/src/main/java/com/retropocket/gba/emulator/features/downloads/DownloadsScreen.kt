package com.retropocket.gba.emulator.features.downloads

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retropocket.gba.emulator.R
import androidx.compose.ui.res.stringResource
import com.retropocket.gba.emulator.data.database.repo.GameRepo
import com.retropocket.gba.emulator.core.Analytics
import com.retropocket.gba.emulator.core.AppConfig
import com.retropocket.gba.emulator.corefeatures.importer.rememberStorageAccess
import com.retropocket.gba.emulator.features.main.NAV_BAR_HEIGHT_DP
import com.retropocket.gba.emulator.ui.components.*
import com.retropocket.gba.emulator.ui.theme.*

/**
 * Công tắc giao diện trang Store sau khi đã cấp quyền:
 * - false: vào thẳng kho game WebView (giao diện đầy đủ).
 * - true : màn tạm 2 nút GET GAMES / IMPORT GAMES, khoá lối vào WebView — dùng cho bản phát
 *          hành cần hạn chế rủi ro chính sách Google Play (xem [StoreHub]).
 */
private const val USE_STORE_HUB = false

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DownloadsScreen(
    onOpenImport: () -> Unit,
    onImported: () -> Unit,
    viewModel: DownloadsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Chưa đồng ý + cấp quyền thì WebView KHÔNG được tạo, chỉ hiện màn chắn StoreAccessGate.
    val access = rememberStorageAccess()

    // Kho WebView chỉ hiện khi user bấm GET GAMES trên màn hub. Bản đầu tạm khoá lối vào này
    // (xem onGetGames bên dưới) nên nhánh WebView hiện chưa bao giờ được dựng.
    var showWebStore by rememberSaveable { mutableStateOf(!USE_STORE_HUB) }

    var canGoBack by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Nút back của máy phải lùi trong lịch sử web trước, không thoát thẳng khỏi app khi user
    // mới bấm vào vài trang con của kho game.
    // Lùi trong lịch sử web trước. Hết lịch sử thì: có màn hub -> quay về hub; không có hub ->
    // để hệ thống xử lý Back như bình thường.
    BackHandler(enabled = showWebStore && (canGoBack || USE_STORE_HUB)) {
        if (canGoBack) webView?.goBack() else showWebStore = false
    }

    // Import xong thì chuyển sang thư viện để user thấy ngay kết quả.
    LaunchedEffect(state) {
        if (state is DownloadState.Done) {
            kotlinx.coroutines.delay(1400)
            viewModel.dismissState()
            onImported()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgRoot)
            .statusBarsPadding(),
    ) {
        AppTopBar(
            title = "STORE",
            titleColor = NeonCyan,
            actions = {
                // Các nút này chỉ có nghĩa khi kho WebView đang mở.
                if (access.isGranted && showWebStore) {
                    // Nút mở web ngoài và Import đã chuyển sang tab Downloads.
                    IconCircleButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = "Reload",
                        tint = NeonCyan,
                        onClick = { webView?.reload() },
                    )
                }
            },
        )

        if (!access.isGranted) {
            StoreAccessGate(
                opensSettings = access.opensSettings,
                onGrant = access::request,
                modifier = Modifier.weight(1f),
            )
        } else if (!showWebStore) {
            StoreHub(
                onGetGames = {
                    // Kho game dạng WebView — TẠM TẮT ở bản đầu để hạn chế rủi ro chính sách
                    // Google Play. Muốn bật lại: bỏ comment dòng dưới và xoá dòng Toast.
                    // showWebStore = true
                    Toast.makeText(
                        context,
                        context.getString(R.string.store_coming_soon),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onImportGames = onOpenImport,
                modifier = Modifier.weight(1f),
            )
        } else {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // WebView không có spacer cuối như danh sách cuộn nên phải tự chừa chỗ nav bar,
                    // nếu không phần đáy trang web bị che vĩnh viễn.
                    .padding(bottom = NAV_BAR_HEIGHT_DP.dp)
                    .navigationBarsPadding(),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            // Mặc định WebView nền TRẮNG cho tới khi trang vẽ xong khung đầu tiên -> mỗi
                            // lần quay lại tab Store (WebView được dựng lại) là chớp trắng một nhịp.
                            setBackgroundColor(BgRoot.toArgb())
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            // Chặn trang tự mở cửa sổ mới (site ROM hay bật pop-up quảng cáo).
                            settings.setSupportMultipleWindows(false)
                            settings.javaScriptCanOpenWindowsAutomatically = false

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                    loadError = null
                                    url?.let { viewModel.rememberUrl(it) }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    canGoBack = view?.canGoBack() == true
                                    url?.let(viewModel::rememberUrl)
                                }

                                // Chỉ báo lỗi của KHUNG CHÍNH: ảnh/script phụ lỗi là chuyện thường,
                                // hiện màn lỗi vì chúng sẽ che mất trang vẫn đang dùng được.
                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: android.webkit.WebResourceError?,
                                ) {
                                    if (request?.isForMainFrame != true) return
                                    isLoading = false
                                    loadError = "Could not reach the store. Check your connection and try again."
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val url = request?.url?.toString().orEmpty()
                                    // File ROM/archive: tự tải trong app thay vì để trình duyệt xử lý.
                                    return if (url.isDownloadable()) {
                                        viewModel.download(url, url.substringAfterLast('/'))
                                        true
                                    } else {
                                        false
                                    }
                                }
                            }

                            setDownloadListener { url, _, contentDisposition, _, _ ->
                                val name = contentDisposition
                                    .substringAfter("filename=", "")
                                    .trim('"', ' ')
                                    .ifBlank { url.substringAfterLast('/') }
                                Analytics.log("download_game")
                                viewModel.download(url, name)
                            }

                            loadUrl(viewModel.lastUrl)
                            webView = this
                        }
                    },
                )

                loadError?.let { message ->
                    // Che hẳn WebView: trang lỗi mặc định của hệ thống là nền trắng chữ đen, đặt trong
                    // app nền tối trông như lỗi hiển thị.
                    Box(Modifier.fillMaxSize().background(BgRoot), Alignment.Center) {
                        EmptyState(
                            icon = Icons.Filled.ErrorOutline,
                            title = "Store unavailable",
                            description = message,
                            actionLabel = "RETRY",
                            onAction = {
                                loadError = null
                                isLoading = true
                                webView?.loadUrl(viewModel.lastUrl)
                            },
                        )
                    }
                }

                if (isLoading) {
                    LinearProgressTrack(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                        height = 2.dp,
                    )
                }

                DownloadStatusToast(
                    state = state,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    onDismiss = viewModel::dismissState,
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { webView?.destroy() }
    }
}

@Composable
private fun DownloadStatusToast(
    state: DownloadState,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
) {
    if (state is DownloadState.Idle) return

    Row(
        modifier
            .clip(RadiusPill)
            .background(BgSurfaceHigh)
            .border(1.dp, BorderSubtle, RadiusPill)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (state) {
            is DownloadState.Downloading -> {
                CircularProgressTrack(size = 16.dp)
                Text(
                    "Downloading ${state.fileName} · ${state.percent}%",
                    style = BodySmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            is DownloadState.Extracting -> {
                CircularProgressTrack(size = 16.dp)
                Text("Extracting ${state.fileName}…", style = BodySmall, color = TextPrimary, maxLines = 1)
            }

            is DownloadState.Done -> {
                Icon(Icons.Filled.CheckCircle, null, tint = NeonGreen, modifier = Modifier.size(18.dp))
                Text(
                    if (state.addedCount > 0) "Added ${state.addedCount} game(s)" else "Already in library",
                    style = BodySmall,
                    color = TextPrimary,
                )
            }

            is DownloadState.Failed -> {
                Icon(Icons.Filled.ErrorOutline, null, tint = NeonRed, modifier = Modifier.size(18.dp))
                Text(state.reason, style = BodySmall, color = TextPrimary, maxLines = 2)
                Spacer(Modifier.width(4.dp))
                Text(
                    "DISMISS",
                    style = LabelPixel,
                    color = NeonCyan,
                    modifier = Modifier.clip(RadiusSmall).pressScale(onClick = onDismiss).padding(4.dp),
                )
            }

            DownloadState.Idle -> Unit
        }
    }
}

private fun String.isDownloadable(): Boolean {
    val path = substringBefore('?').lowercase()
    return path.endsWith(".zip") ||
        com.retropocket.gba.emulator.data.domain.GameSystem.ALL_EXTENSIONS.any { path.endsWith(".$it") }
}
