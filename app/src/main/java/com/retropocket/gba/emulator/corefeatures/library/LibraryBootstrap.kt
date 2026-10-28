package com.retropocket.gba.emulator.corefeatures.library

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Khởi tạo thư viện game mỗi lần process khởi động: cài game mẫu + ảnh bìa, rồi quét thư mục ROM.
 *
 * Gọi từ `GlobalApp.onCreate()` chứ KHÔNG gắn vào màn Splash: luồng màn hình có thể bị rút gọn
 * (vd. MainActivity vào thẳng MainNavHost khi test), và khi đó việc cài game mẫu sẽ không bao giờ
 * chạy nếu nằm trong một composable. Chạy ở tầng Application thì luôn chạy đúng một lần mỗi
 * process, độc lập với việc màn nào hiển thị.
 *
 * UI không cần chờ: GameRepo phát StateFlow nên Home/Library tự cập nhật khi quét xong, trong lúc
 * đó cờ `GameRepo.isBusy` cho UI hiện trạng thái "đang quét".
 */
object LibraryBootstrap {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                // Idempotent: file đã có thì bỏ qua, nên chạy mỗi lần mở app cũng gần như không
                // tốn gì. Ảnh bìa đi trước ROM để bản ghi mới nhận ngay ảnh cục bộ.
                SampleGameInstaller.installCovers(appContext)
                SampleGameInstaller.installIfNeeded(appContext)
                LibraryScanner.scan()
            }.onFailure { Log.w("LibraryBootstrap", "Library init failed", it) }
        }
    }
}
