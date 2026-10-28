package com.retropocket.gba.emulator.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.retropocket.gba.emulator.data.domain.GameSystem

/**
 * Một game trong thư viện.
 *
 * Cố ý KHÔNG lưu nguồn gốc file (WebView / import thủ công / game mẫu): mọi luồng nhập đều kết thúc
 * bằng "ROM nằm trong filesDir/roms", sau đó cùng một hàm quét chịu trách nhiệm index. Nhờ vậy thêm
 * nguồn mới sau này không phải đụng tới tầng dữ liệu.
 */
@Entity(tableName = "games")
data class GameEntity(
    /** CRC32 của file dạng hex — id tự nhiên, tự khử trùng lặp khi user import lại cùng một ROM. */
    @PrimaryKey val id: String,
    val title: String,
    val systemId: String,
    val filePath: String,
    val fileSize: Long,
    val coverUrl: String?,
    val isFavourite: Boolean = false,
    val lastPlayedAt: Long? = null,
    val addedAt: Long,
    /** Game mẫu đóng gói sẵn trong APK — không cho user xoá. */
    val isSample: Boolean = false,
) {
    val system: GameSystem get() = GameSystem.byIdName(systemId)
}
