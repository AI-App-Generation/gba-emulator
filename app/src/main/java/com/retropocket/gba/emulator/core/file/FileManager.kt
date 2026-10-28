package com.retropocket.gba.emulator.core.file

import android.content.Context
import java.io.File
import java.io.InputStream
import java.util.zip.CRC32

/**
 * Quản lý thư mục riêng của app và các thao tác file cơ bản.
 *
 * Mọi ROM đều nằm trong thư mục nội bộ của app (không phải bộ nhớ chung) nên không cần xin quyền
 * lưu trữ nào để đọc/ghi, và bị xoá sạch khi user gỡ app.
 */
object FileManager {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        listOf(romsDir(), statesDir(), systemDir(), savesDir(), coversDir()).forEach { it.mkdirs() }
    }

    /** Nơi chứa toàn bộ ROM đã nhập, bất kể nguồn nào. */
    fun romsDir(): File = File(appContext.filesDir, "roms")

    /** Save state do user tạo trong game. */
    fun statesDir(): File = File(appContext.filesDir, "states")

    /** Thư mục "system" mà core libretro tìm BIOS/asset trong đó. */
    fun systemDir(): File = File(appContext.filesDir, "system")

    /**
     * Ảnh bìa cục bộ: file cùng tên gốc với ROM (vd. `Tobu Tobu Girl.jpg`) sẽ được ưu tiên hơn
     * ảnh tải từ kho thumbnail libretro — dùng cho game mẫu và game homebrew kho đó không có.
     */
    fun coversDir(): File = File(appContext.filesDir, "covers")

    /** SRAM/save trong game do chính core ghi ra. */
    fun savesDir(): File = File(appContext.filesDir, "saves")

    /** Thư mục chứa core .so đã được hệ thống giải nén khi cài app. */
    fun nativeLibDir(): File = File(appContext.applicationInfo.nativeLibraryDir)

    fun crc32(file: File): String {
        val crc = CRC32()
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                crc.update(buffer, 0, read)
            }
        }
        return crc.value.toString(16).padStart(8, '0')
    }

    fun copyToRoms(input: InputStream, fileName: String): File {
        val target = File(romsDir(), fileName)
        target.parentFile?.mkdirs()
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        return target
    }

    fun hasEnoughSpace(requiredBytes: Long): Boolean =
        appContext.filesDir.usableSpace > requiredBytes * 2  // chừa gấp đôi cho an toàn

    fun readableSize(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
