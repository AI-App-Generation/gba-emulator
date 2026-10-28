package com.retropocket.gba.emulator.corefeatures.library

import com.retropocket.gba.emulator.core.file.FileManager
import com.retropocket.gba.emulator.data.domain.GameSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

data class ExtractProgress(
    val currentFileName: String,
    val doneFiles: Int,
    val totalFiles: Int,
    val percent: Int,
)

sealed interface ExtractResult {
    data class Success(val extracted: List<File>) : ExtractResult
    data class Failed(val reason: String) : ExtractResult
}

/**
 * Giải nén archive ROM vào thư mục roms của app.
 *
 * Chỉ lấy ra entry có đuôi nằm trong danh sách hệ máy hỗ trợ; bỏ qua file rác (readme, ảnh bìa,
 * thư mục rác của macOS). Duyệt đệ quy mọi entry chứ không giả định "1 zip = 1 ROM".
 */
object RomExtractor {

    private val JUNK_PREFIXES = listOf("__MACOSX/", "._")

    suspend fun extractZip(
        openStream: () -> InputStream,
        archiveName: String,
        totalEntriesHint: Int = 0,
        onProgress: (ExtractProgress) -> Unit = {},
    ): ExtractResult = withContext(Dispatchers.IO) {
        val extracted = mutableListOf<File>()
        var index = 0

        runCatching {
            ZipInputStream(openStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val entryName = entry.name

                    if (entry.isDirectory || JUNK_PREFIXES.any { entryName.startsWith(it) }) {
                        zip.closeEntry()
                        continue
                    }

                    val simpleName = entryName.substringAfterLast('/')
                    val extension = simpleName.substringAfterLast('.', "").lowercase()

                    if (extension !in GameSystem.ALL_EXTENSIONS) {
                        zip.closeEntry()
                        continue
                    }

                    val target = File(FileManager.romsDir(), simpleName)

                    // Chống zip-slip: entry tên "../../" có thể ghi đè file ngoài thư mục đích.
                    if (!target.canonicalPath.startsWith(FileManager.romsDir().canonicalPath)) {
                        zip.closeEntry()
                        continue
                    }

                    index++
                    onProgress(
                        ExtractProgress(
                            currentFileName = simpleName,
                            doneFiles = index,
                            totalFiles = maxOf(totalEntriesHint, index),
                            percent = if (totalEntriesHint > 0) {
                                (index * 100 / totalEntriesHint).coerceIn(0, 100)
                            } else 0,
                        )
                    )

                    target.outputStream().use { out -> zip.copyTo(out) }
                    extracted += target
                    zip.closeEntry()
                }
            }
        }.onFailure { error ->
            return@withContext ExtractResult.Failed(error.message ?: "Extract failed")
        }

        if (extracted.isEmpty()) {
            ExtractResult.Failed("No supported ROM found inside $archiveName")
        } else {
            ExtractResult.Success(extracted)
        }
    }

    /** Đếm trước số ROM trong archive để hiện tiến trình có phần trăm thật. */
    suspend fun countRomEntries(openStream: () -> InputStream): Int = withContext(Dispatchers.IO) {
        var count = 0
        runCatching {
            ZipInputStream(openStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val extension = entry.name.substringAfterLast('.', "").lowercase()
                    if (!entry.isDirectory && extension in GameSystem.ALL_EXTENSIONS) count++
                    zip.closeEntry()
                }
            }
        }
        count
    }
}
