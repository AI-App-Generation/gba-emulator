package com.retropocket.gba.emulator.features.importer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.retropocket.gba.emulator.core.file.FileManager
import com.retropocket.gba.emulator.corefeatures.importer.ArchiveFile
import com.retropocket.gba.emulator.corefeatures.importer.DownloadsIndexer
import com.retropocket.gba.emulator.corefeatures.importer.IndexResult
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

sealed interface ImportUiState {
    data object Loading : ImportUiState
    data class NeedPermission(val allFilesAccess: Boolean) : ImportUiState
    data class Ready(val files: List<ArchiveFile>) : ImportUiState
}

sealed interface ImportProgress {
    data object None : ImportProgress
    data class Running(val fileName: String, val done: Int, val total: Int, val percent: Int) : ImportProgress
    data class Finished(val addedCount: Int, val failedCount: Int) : ImportProgress
}

class ImportViewModel(app: Application) : AndroidViewModel(app) {

    // Có cache của phiên thì hiện danh sách ngay từ khung hình đầu, không qua trạng thái Loading.
    private val _uiState = MutableStateFlow<ImportUiState>(
        DownloadsIndexer.cachedFiles(app)?.let { ImportUiState.Ready(it) } ?: ImportUiState.Loading,
    )
    val uiState = _uiState.asStateFlow()

    private val _progress = MutableStateFlow<ImportProgress>(ImportProgress.None)
    val progress = _progress.asStateFlow()

    private val _selected = MutableStateFlow<Set<Uri>>(emptySet())
    val selected = _selected.asStateFlow()

    /** Mở màn / vừa cấp quyền: dùng cache của phiên nếu có, KHÔNG quét lại. */
    fun load() = index(forceRefresh = false)

    /** User chủ động bấm làm mới (vd. vừa tải thêm ROM bằng trình duyệt). */
    fun refresh() = index(forceRefresh = true)

    private fun index(forceRefresh: Boolean) {
        viewModelScope.launch {
            // Mở màn mà đã có danh sách thì giữ nguyên, không bật Loading cho khỏi chớp.
            if (_uiState.value !is ImportUiState.Ready || forceRefresh) {
                _uiState.value = ImportUiState.Loading
            }
            _uiState.value = when (
                val result = DownloadsIndexer.index(getApplication(), forceRefresh)
            ) {
                is IndexResult.Ready -> ImportUiState.Ready(result.files)
                is IndexResult.NeedsPermission ->
                    ImportUiState.NeedPermission(result.allFilesAccess)
            }
        }
    }

    /** Bỏ những dòng vừa phát hiện không còn tồn tại khỏi danh sách đang hiện. */
    private fun dropMissing(missing: List<Uri>) {
        if (missing.isEmpty()) return
        missing.forEach(DownloadsIndexer::forget)
        val current = _uiState.value as? ImportUiState.Ready ?: return
        _uiState.value = ImportUiState.Ready(current.files.filterNot { it.uri in missing })
    }

    fun toggle(uri: Uri) {
        _selected.value = _selected.value.toMutableSet().apply {
            if (!add(uri)) remove(uri)
        }
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    fun dismissProgress() {
        _progress.value = ImportProgress.None
        _selected.value = emptySet()
    }

    fun importSelected() {
        val state = _uiState.value as? ImportUiState.Ready ?: return
        val targets = state.files.filter { it.uri in _selected.value }
        if (targets.isEmpty()) return

        viewModelScope.launch {
            var failed = 0
            val missing = mutableListOf<Uri>()

            targets.forEachIndexed { index, archive ->
                _progress.value = ImportProgress.Running(
                    fileName = archive.name,
                    done = index,
                    total = targets.size,
                    percent = index * 100 / targets.size,
                )

                // Zip nén được ~2-3 lần với ROM nên chỗ trống phải tính theo kích thước sau giải
                // nén, không phải kích thước file nén.
                if (!FileManager.hasEnoughSpace(archive.size * 3)) {
                    failed++
                    return@forEachIndexed
                }

                // Danh sách có thể lấy từ cache của phiên: file đã bị xoá/di chuyển từ lúc quét
                // thì tính dòng đó là lỗi và gỡ khỏi danh sách, không làm hỏng cả lượt import.
                val available = withContext(Dispatchers.IO) {
                    DownloadsIndexer.isStillAvailable(getApplication(), archive.uri)
                }
                if (!available) {
                    failed++
                    missing += archive.uri
                    return@forEachIndexed
                }

                val extension = archive.name.substringAfterLast('.', "").lowercase()
                val ok = if (extension in GameSystem.ALL_EXTENSIONS) {
                    copyPlainRom(archive)
                } else {
                    extractArchive(archive)
                }
                if (!ok) failed++
            }

            val newGames = LibraryScanner.scan()
            _progress.value = ImportProgress.Finished(addedCount = newGames, failedCount = failed)
            // Import không làm thay đổi thư mục Downloads nên không quét lại — chỉ gỡ những file
            // vừa phát hiện đã mất.
            dropMissing(missing)
        }
    }

    private suspend fun copyPlainRom(archive: ArchiveFile): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            DownloadsIndexer.openStream(getApplication(), archive.uri).use { input ->
                File(FileManager.romsDir(), archive.name).outputStream().use { input.copyTo(it) }
            }
            true
        }.getOrDefault(false)
    }

    private suspend fun extractArchive(archive: ArchiveFile): Boolean = try {
        extractArchiveUnsafe(archive)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        // File bị xoá / mất quyền đúng lúc đang đọc: tính là một dòng lỗi, không làm văng app.
        false
    }

    private suspend fun extractArchiveUnsafe(archive: ArchiveFile): Boolean {
        val context = getApplication<Application>()
        val total = RomExtractor.countRomEntries { DownloadsIndexer.openStream(context, archive.uri) }
        val result = RomExtractor.extractZip(
            openStream = { DownloadsIndexer.openStream(context, archive.uri) },
            archiveName = archive.name,
            totalEntriesHint = total,
            onProgress = { p ->
                _progress.value = ImportProgress.Running(
                    fileName = "${archive.name} · ${p.currentFileName}",
                    done = p.doneFiles,
                    total = maxOf(p.totalFiles, 1),
                    percent = p.percent,
                )
            },
        )
        return result is ExtractResult.Success
    }
}
