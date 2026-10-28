package com.retropocket.gba.emulator.corefeatures.importer

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Trạng thái quyền đọc Downloads, dùng chung cho màn Store và màn Import.
 *
 * Hai cơ chế hoàn toàn khác nhau tuỳ phiên bản Android:
 * - Android 10 trở xuống: dialog runtime `READ_EXTERNAL_STORAGE` bình thường.
 * - Android 11+: "All files access" — không có dialog, hệ thống mở một màn Settings có công tắc.
 *   Xem [DownloadsIndexer] để biết vì sao không còn quyền nào nhẹ hơn dùng được cho file .zip.
 */
@Stable
class StorageAccessState internal constructor(initiallyGranted: Boolean) {

    var isGranted by mutableStateOf(initiallyGranted)
        internal set

    /** true -> bấm cấp quyền sẽ mở màn Settings (Android 11+), false -> hiện dialog runtime. */
    val opensSettings: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    internal var launch: () -> Unit = {}

    fun request() = launch()
}

@Composable
fun rememberStorageAccess(): StorageAccessState {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val state = remember { StorageAccessState(DownloadsIndexer.hasAccess(context)) }

    fun recheck() {
        state.isGranted = DownloadsIndexer.hasAccess(context)
    }

    // Android <= 10 sau khi user chọn "Không hỏi lại" thì hệ thống từ chối ngay mà không hiện
    // dialog nữa — bấm nút sẽ im lặng không có gì xảy ra. Nhớ trạng thái này để lần sau đưa
    // thẳng vào trang thông tin ứng dụng, nơi duy nhất còn bật lại được quyền.
    var permanentlyDenied by remember { mutableStateOf(false) }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        recheck()
        if (!granted && activity != null) {
            permanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            )
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { recheck() }

    // User hay gạt công tắc xong rồi thoát bằng nút Home / Recent thay vì Back, khi đó launcher
    // không nhận được kết quả. ON_RESUME là chốt chặn chắc chắn nhất để cập nhật lại.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) recheck()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val appDetails = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )

    state.launch = {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val direct = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.fromParts("package", context.packageName, null),
                )
                // Không dò bằng resolveActivity(): quy tắc package visibility của Android 11+ có
                // thể trả null cho cả Activity có thật. Cứ phóng rồi bắt lỗi, hỏng thì lui dần về
                // danh sách chung của hệ thống, cuối cùng là trang thông tin ứng dụng.
                runCatching { settingsLauncher.launch(direct) }
                    .recoverCatching {
                        settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                    .recoverCatching { settingsLauncher.launch(appDetails) }
            }

            permanentlyDenied -> runCatching { settingsLauncher.launch(appDetails) }

            else -> runtimeLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    return state
}
