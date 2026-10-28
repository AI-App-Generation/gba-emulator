package com.retropocket.gba.emulator.corefeatures.emulator

import com.retropocket.gba.emulator.core.file.FileManager
import com.retropocket.gba.emulator.data.domain.GameSystem
import com.retropocket.gba.emulator.data.domain.SystemId
import java.io.File

/**
 * Kiểm tra core libretro có THỰC SỰ tồn tại trong bản build hay không.
 *
 * Đây là bài học rút từ project cũ: nơi đó khai báo 20 hệ máy trong code nhưng 16 file .so thực tế
 * không có trong APK, dẫn tới user bấm Play rồi lỗi khó hiểu. Ở đây mọi màn hình đều hỏi
 * [isAvailable] trước khi cho phép chơi, và hiện cảnh báo rõ ràng nếu thiếu.
 */
object CoreCatalog {

    private val cache = mutableMapOf<SystemId, Boolean>()

    fun coreFile(system: GameSystem): File =
        File(FileManager.nativeLibDir(), system.coreFileName)

    fun isAvailable(system: GameSystem): Boolean {
        if (system.id == SystemId.UNKNOWN || system.coreFileName.isBlank()) return false
        return cache.getOrPut(system.id) { coreFile(system).exists() }
    }

    /** Các hệ máy dùng được trong bản build hiện tại — dùng để lọc tab/danh sách. */
    fun availableSystems(): List<GameSystem> = GameSystem.ALL.filter { isAvailable(it) }
}
