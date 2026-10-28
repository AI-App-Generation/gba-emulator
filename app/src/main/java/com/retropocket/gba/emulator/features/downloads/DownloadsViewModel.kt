package com.retropocket.gba.emulator.features.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.retropocket.gba.emulator.core.file.FileManager
import com.retropocket.gba.emulator.corefeatures.library.ExtractResult
import com.retropocket.gba.emulator.corefeatures.library.LibraryScanner
import com.retropocket.gba.emulator.corefeatures.library.RomExtractor
import com.retropocket.gba.emulator.data.domain.GameSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val fileName: String, val percent: Int) : DownloadState
    data class Extracting(val fileName: String) : DownloadState
    data class Done(val addedCount: Int) : DownloadState
    data class Failed(val reason: String) : DownloadState
}

/**
 * Tải file từ WebView rồi đưa thẳng vào thư viện.
 *
 * Tải vào thư mục cache của app (không đụng bộ nhớ chung nên không cần quyền gì), giải nén nếu là
 * archive, rồi gọi cùng một hàm quét mà mọi nguồn nhập khác dùng.
 */
class DownloadsViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state = _state.asStateFlow()

    /**
     * Trang web đang xem. ViewModel sống lâu hơn composable của tab nên user chuyển sang tab khác
     * rồi quay lại vẫn ở đúng trang đang duyệt thay vì bị đá về trang chủ.
     */
    var lastUrl: String = com.retropocket.gba.emulator.core.AppConfig.STORE_URL
        private set

    fun rememberUrl(url: String) {
        if (url.isNotBlank() && !url.startsWith("about:")) lastUrl = url
    }

    fun dismissState() {
        _state.value = DownloadState.Idle
    }

    fun download(url: String, suggestedName: String) {
        viewModelScope.launch {
            val fileName = suggestedName.substringAfterLast('/').ifBlank { "download.zip" }
            _state.value = DownloadState.Downloading(fileName, 0)

            val temp = runCatching { fetchToCache(url, fileName) }
                .getOrElse {
                    _state.value = DownloadState.Failed(it.message ?: "Download failed")
                    return@launch
                }

            val extension = fileName.substringAfterLast('.', "").lowercase()
            _state.value = DownloadState.Extracting(fileName)

            val added = when {
                // ROM trần: chỉ cần chuyển vào thư mục thư viện.
                extension in GameSystem.ALL_EXTENSIONS -> {
                    withContext(Dispatchers.IO) {
                        temp.copyTo(File(FileManager.romsDir(), fileName), overwrite = true)
                        temp.delete()
                    }
                    LibraryScanner.scan()
                }

                extension == "zip" -> {
                    val total = RomExtractor.countRomEntries { temp.inputStream() }
                    val result = RomExtractor.extractZip(
                        openStream = { temp.inputStream() },
                        archiveName = fileName,
                        totalEntriesHint = total,
                    )
                    withContext(Dispatchers.IO) { temp.delete() }
                    when (result) {
                        is ExtractResult.Failed -> {
                            _state.value = DownloadState.Failed(result.reason)
                            return@launch
                        }

                        is ExtractResult.Success -> LibraryScanner.scan()
                    }
                }

                else -> {
                    withContext(Dispatchers.IO) { temp.delete() }
                    _state.value = DownloadState.Failed("Unsupported file: $fileName")
                    return@launch
                }
            }

            _state.value = DownloadState.Done(added)
        }
    }

    private suspend fun fetchToCache(url: String, fileName: String): File = withContext(Dispatchers.IO) {
        val target = File(getApplication<Application>().cacheDir, fileName)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 30_000
        }
        // Nhiều site ROM trả về trang HTML lỗi kèm mã 404/403 — nếu không kiểm tra, app sẽ lưu
        // trang HTML thành file .zip rồi báo "không tìm thấy ROM" khiến user tưởng file hỏng.
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            error("Server returned ${connection.responseCode}")
        }
        val declaredSize = connection.contentLengthLong
        if (declaredSize > 0 && !FileManager.hasEnoughSpace(declaredSize)) {
            connection.disconnect()
            error("Not enough free space for this download")
        }
        connection.inputStream.use { input ->
            val total = declaredSize
            var downloaded = 0L
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (total > 0) {
                        val percent = (downloaded * 100 / total).toInt().coerceIn(0, 100)
                        _state.value = DownloadState.Downloading(fileName, percent)
                    }
                }
            }
        }
        connection.disconnect()
        target
    }
}
