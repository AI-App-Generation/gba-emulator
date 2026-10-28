package com.retropocket.gba.emulator.corefeatures.library

import com.retropocket.gba.emulator.core.file.FileManager
import com.retropocket.gba.emulator.data.database.entity.GameEntity
import com.retropocket.gba.emulator.data.database.repo.GameRepo
import com.retropocket.gba.emulator.data.domain.GameSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Quét thư mục roms và đồng bộ vào Room.
 *
 * Chỉ có DUY NHẤT hàm này ghi game vào thư viện — mọi nguồn nhập (WebView, import thủ công, game
 * mẫu) đều chỉ có nhiệm vụ đặt file ROM vào đúng thư mục rồi gọi [scan].
 */
object LibraryScanner {

    suspend fun scan(): Int = withContext(Dispatchers.IO) {
        GameRepo.setBusy(true)
        try {
            val allFiles = FileManager.romsDir().listFiles()?.filter { it.isFile } ?: emptyList()
            val filesOnDisk = allFiles
                .filter { it.extension.lowercase() in GameSystem.ALL_EXTENSIONS }
                .filterNot { it.isDiscTrackOf(allFiles) }

            val known = GameRepo.getAll()
            val knownPaths = known.associateBy { it.filePath }

            // 1. Dọn record trỏ tới file không còn tồn tại (user xoá file, khôi phục máy...).
            val orphanIds = known.filter { !File(it.filePath).exists() }.map { it.id }
            if (orphanIds.isNotEmpty()) GameRepo.deleteByIds(orphanIds)

            // 2. Vá lại ảnh bìa của bản ghi cũ: các bản build trước dựng URL từ tiêu đề đã bỏ
            // ngoặc nên 404 toàn bộ. Chỉ là phép tính trên chuỗi, không đụng tới CRC nên rẻ.
            known.forEach { entity ->
                val file = File(entity.filePath)
                if (!file.exists()) return@forEach
                val system = GameSystem.byIdName(entity.systemId)
                val fresh = CoverUrls.forRom(system, file)
                if (fresh != null && fresh != entity.coverUrl) {
                    GameRepo.updateCoverUrl(entity.id, fresh)
                }
            }

            // 3. Chỉ xử lý file mới — quét chênh lệch, không tính lại CRC toàn bộ mỗi lần mở app.
            val newFiles = filesOnDisk.filter { it.absolutePath !in knownPaths }
            if (newFiles.isEmpty()) return@withContext 0

            val entities = newFiles.mapNotNull { file -> file.toGameEntity() }
            // Đếm số bản ghi thật sự vào DB: cùng một ROM đổi tên file sẽ trùng CRC và bị bỏ qua,
            // báo "đã thêm" trong trường hợp đó là nói sai với user.
            GameRepo.insertAll(entities)
        } finally {
            GameRepo.setBusy(false)
        }
    }

    /** Nhận diện game mẫu ngay khi cài để đánh dấu isSample. */
    suspend fun scanAsSamples(files: List<File>) = withContext(Dispatchers.IO) {
        val entities = files.mapNotNull { it.toGameEntity(isSample = true) }
        GameRepo.insertAll(entities)
    }

    /**
     * Đĩa PS1 thường là cặp `game.cue` + `game.bin` (đôi khi nhiều track `game (Track 1).bin`).
     * File `.bin` chỉ là dữ liệu track, không nạp trực tiếp được, và đuôi `.bin` còn trùng với ROM
     * Genesis — nếu index cả hai thì thư viện có 2 mục cho cùng một game và mục `.bin` luôn lỗi
     * khi bấm Play. Vì vậy bỏ qua `.bin` khi có file điều khiển đĩa cùng tên đứng cạnh.
     */
    private fun File.isDiscTrackOf(siblings: List<File>): Boolean {
        if (extension.lowercase() != "bin") return false
        val base = nameWithoutExtension.substringBefore(" (Track").trim().lowercase()
        return siblings.any {
            it.extension.lowercase() in setOf("cue", "m3u") &&
                it.nameWithoutExtension.trim().lowercase() == base
        }
    }

    private fun File.toGameEntity(isSample: Boolean = false): GameEntity? {
        val system = GameSystem.byExtension(extension) ?: return null
        val title = nameWithoutExtension.cleanRomTitle()
        return GameEntity(
            id = FileManager.crc32(this),
            title = title,
            systemId = system.id.name,
            filePath = absolutePath,
            fileSize = length(),
            coverUrl = CoverUrls.forRom(system, this),
            addedAt = System.currentTimeMillis(),
            isSample = isSample,
        )
    }

    /**
     * Dọn tên file ROM cho dễ đọc: bỏ mã vùng/revision trong ngoặc mà scene hay gắn kèm.
     * "Super Mario Advance (USA, Europe) [!]" -> "Super Mario Advance"
     */
    private fun String.cleanRomTitle(): String =
        replace(Regex("[(\\[][^)\\]]*[)\\]]"), "")
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { this }
}
