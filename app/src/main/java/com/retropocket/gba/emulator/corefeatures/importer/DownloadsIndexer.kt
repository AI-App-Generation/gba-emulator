package com.retropocket.gba.emulator.corefeatures.importer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

data class ArchiveFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val lastModified: Long,
    /** Thư mục con bên trong Downloads, null nếu nằm ngay thư mục gốc. */
    val relativeFolder: String? = null,
)

sealed interface IndexResult {
    data class Ready(val files: List<ArchiveFile>) : IndexResult

    /**
     * Cần quyền trước khi đọc được Downloads.
     * [allFilesAccess] = true -> Android 11+, phải bật công tắc "All files access" trong Settings.
     * = false -> Android 10 trở xuống, chỉ cần dialog runtime READ_EXTERNAL_STORAGE.
     */
    data class NeedsPermission(val allFilesAccess: Boolean) : IndexResult
}

/**
 * Liệt kê archive ROM trong thư mục Downloads của máy, để app tự dựng UI chọn file thay vì
 * dùng hộp thoại chọn file của hệ thống.
 *
 * Vì sao phải dùng "All files access" trên Android 11+:
 * - `READ_EXTERNAL_STORAGE` bị hệ thống bỏ qua từ API 33.
 * - `READ_MEDIA_IMAGES/VIDEO/AUDIO` chỉ cấp quyền cho file media, `.zip` không thuộc nhóm này.
 * - `MediaStore.Downloads` khi không có quyền chỉ trả về file do CHÍNH app tạo ra, nên file
 *   Chrome tải về là vô hình.
 * - `ACTION_OPEN_DOCUMENT_TREE` không cho chọn thư mục Download (nằm trong danh sách cấm).
 * Nên đây là cách duy nhất còn lại để giữ đúng yêu cầu "đi thẳng tới Downloads bằng UI của app".
 */
object DownloadsIndexer {

    /**
     * CHỈ .zip: [com.retropocket.gba.emulator.corefeatures.library.RomExtractor] giải nén bằng
     * `ZipInputStream`, nên liệt kê thêm .7z/.rar chỉ tạo ra những dòng mà user bấm vào là chắc
     * chắn thất bại. ROM trần (.gba, .nes...) thì copy thẳng nên vẫn nhận.
     */
    private val ARCHIVE_EXTENSIONS = setOf("zip")

    /** Chặn quét sâu vô hạn: Downloads của máy dùng lâu có thể lồng rất nhiều tầng. */
    private const val MAX_DEPTH = 3
    private const val MAX_FILES = 500

    /**
     * Danh sách đã quét trong phiên chạy app hiện tại.
     *
     * Quét Downloads đệ quy tốn thời gian và làm màn Import chớp loading mỗi lần mở, trong khi
     * nội dung thư mục hiếm khi đổi giữa hai lần mở. Nên chỉ quét một lần mỗi phiên; file nào bị
     * xoá sau đó sẽ được phát hiện lúc import thật (xem [isStillAvailable]) và gỡ khỏi cache.
     * Muốn thấy file mới tải thì user bấm làm mới -> forceRefresh = true.
     *
     * Chỉ lưu kết quả khi ĐÃ có quyền đầy đủ: danh sách lúc chưa có quyền chỉ là một phần
     * (MediaStore trả về file do chính app tạo), cache nó thì cấp quyền xong vẫn thấy thiếu.
     */
    @Volatile
    private var sessionCache: List<ArchiveFile>? = null

    /** Cache còn dùng được không — quyền có thể bị thu hồi giữa phiên. */
    fun cachedFiles(context: Context): List<ArchiveFile>? =
        sessionCache?.takeIf { hasAccess(context) }

    /** Gỡ một file khỏi cache khi phát hiện nó không còn trên máy. */
    fun forget(uri: Uri) {
        sessionCache = sessionCache?.filterNot { it.uri == uri }
    }

    /**
     * Kiểm tra ngay trước khi import: file có thể đã bị xoá/di chuyển kể từ lúc quét, nhất là khi
     * danh sách lấy từ cache của phiên.
     */
    fun isStillAvailable(context: Context, uri: Uri): Boolean = try {
        openStream(context, uri).use { it.read() }
        true
    } catch (e: Exception) {
        false
    }

    fun hasAccess(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        else -> ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun index(
        context: Context,
        forceRefresh: Boolean = false,
    ): IndexResult = withContext(Dispatchers.IO) {
        if (!forceRefresh) cachedFiles(context)?.let { return@withContext IndexResult.Ready(it) }

        if (!hasAccess(context)) {
            // Chưa có quyền vẫn cố hiển thị được gì đó: MediaStore trả về file do chính app tải
            // về qua WebView, đủ để user import mà không cần bật quyền nào.
            val own = runCatching { listFromMediaStore(context) }.getOrDefault(emptyList())
            if (own.isNotEmpty()) return@withContext IndexResult.Ready(own)
            return@withContext IndexResult.NeedsPermission(
                allFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
            )
        }

        val files = listFromDownloadsFolder()
        sessionCache = files
        IndexResult.Ready(files)
    }

    /** Quét trực tiếp bằng File API — chỉ chạy được khi đã có quyền đọc toàn bộ bộ nhớ. */
    private fun listFromDownloadsFolder(): List<ArchiveFile> {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val results = mutableListOf<ArchiveFile>()

        fun walk(dir: File, depth: Int) {
            if (depth > MAX_DEPTH || results.size >= MAX_FILES) return
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (results.size >= MAX_FILES) return
                when {
                    child.isDirectory -> walk(child, depth + 1)
                    child.isFile && child.name.isSupportedArchive() -> results += ArchiveFile(
                        uri = Uri.fromFile(child),
                        name = child.name,
                        size = child.length(),
                        lastModified = child.lastModified(),
                        relativeFolder = child.parentFile
                            ?.takeIf { it.absolutePath != root.absolutePath }
                            ?.name,
                    )
                }
            }
        }

        walk(root, 0)
        return results.sortedByDescending { it.lastModified }
    }

    private fun listFromMediaStore(context: Context): List<ArchiveFile> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()

        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_MODIFIED,
        )

        val results = mutableListOf<ArchiveFile>()
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Downloads.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                if (!name.isSupportedArchive()) continue
                results += ArchiveFile(
                    uri = android.content.ContentUris.withAppendedId(collection, cursor.getLong(idCol)),
                    name = name,
                    size = cursor.getLong(sizeCol),
                    lastModified = cursor.getLong(dateCol) * 1000,
                )
            }
        }
        return results
    }

    fun openStream(context: Context, uri: Uri): InputStream = when (uri.scheme) {
        "file" -> File(requireNotNull(uri.path)).inputStream()
        else -> context.contentResolver.openInputStream(uri) ?: error("Cannot open $uri")
    }

    private fun String.isSupportedArchive(): Boolean {
        val ext = substringAfterLast('.', "").lowercase()
        return ext in ARCHIVE_EXTENSIONS ||
            ext in com.retropocket.gba.emulator.data.domain.GameSystem.ALL_EXTENSIONS
    }
}
