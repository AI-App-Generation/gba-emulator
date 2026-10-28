package com.retropocket.gba.emulator.corefeatures.library

import android.net.Uri
import com.retropocket.gba.emulator.core.file.FileManager
import com.retropocket.gba.emulator.data.domain.GameSystem
import java.io.File
import java.net.URLEncoder

/**
 * Dựng URL ảnh bìa theo quy ước kho thumbnail của libretro.
 *
 * ĐIỂM QUAN TRỌNG: kho này đặt tên file theo đúng chuẩn No-Intro, **kèm cả mã vùng**. Ví dụ
 * `Pokemon - Emerald Version (USA, Europe).png` có ảnh, còn `Pokemon - Emerald Version.png`
 * trả 404. Vì vậy URL phải dựng từ TÊN FILE ROM gốc, không phải từ tiêu đề đã làm sạch để
 * hiển thị — làm sạch xong là hỏng ảnh của gần như mọi game.
 *
 * Tên file thực tế không phải lúc nào cũng chuẩn No-Intro (ROM tải từ kho lạ, ROM hack), nên
 * [forTitle] là phương án dự phòng cho UI thử tiếp khi link chính 404.
 */
object CoverUrls {

    private val LOCAL_COVER_EXTENSIONS = listOf("jpg", "png", "webp")

    /**
     * Ảnh bìa cho một file ROM: có ảnh cục bộ cùng tên trong [FileManager.coversDir] thì dùng
     * ảnh đó, không thì dựng link kho libretro theo tên file.
     */
    fun forRom(system: GameSystem, rom: File): String? {
        val base = rom.nameWithoutExtension
        LOCAL_COVER_EXTENSIONS
            .map { File(FileManager.coversDir(), "$base.$it") }
            .firstOrNull { it.exists() }
            ?.let { return Uri.fromFile(it).toString() }
        return forFileName(system, base)
    }

    /** Ưu tiên 1: khớp chính xác tên file ROM với kho No-Intro. */
    fun forFileName(system: GameSystem, fileNameWithoutExtension: String): String? =
        build(system, fileNameWithoutExtension)

    /** Ưu tiên 2: tiêu đề đã bỏ ngoặc — cứu được ROM có tên file thêm hậu tố lạ. */
    fun forTitle(system: GameSystem, title: String): String? = build(system, title)

    private fun build(system: GameSystem, name: String): String? {
        val folder = system.thumbnailFolder ?: return null
        val cleaned = name.trim().ifBlank { return null }
        val encoded = URLEncoder.encode(cleaned.replace("&", "_"), "UTF-8").replace("+", "%20")
        return "https://thumbnails.libretro.com/$folder/Named_Boxarts/$encoded.png"
    }

    /** Tên thư mục hệ máy trên thumbnails.libretro.com — khác với tên hiển thị trong app. */
    private val GameSystem.thumbnailFolder: String?
        get() = when (id.name) {
            "GBA" -> "Nintendo - Game Boy Advance"
            "GB" -> "Nintendo - Game Boy"
            "GBC" -> "Nintendo - Game Boy Color"
            "NES" -> "Nintendo - Nintendo Entertainment System"
            "SNES" -> "Nintendo - Super Nintendo Entertainment System"
            "N64" -> "Nintendo - Nintendo 64"
            "NDS" -> "Nintendo - Nintendo DS"
            "GENESIS" -> "Sega - Mega Drive - Genesis"
            "SMS" -> "Sega - Master System - Mark III"
            "GG" -> "Sega - Game Gear"
            "PSX" -> "Sony - PlayStation"
            "ATARI2600" -> "Atari - 2600"
            else -> null
        }
}
