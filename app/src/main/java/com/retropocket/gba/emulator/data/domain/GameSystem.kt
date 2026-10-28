package com.retropocket.gba.emulator.data.domain

import androidx.compose.ui.graphics.Color
import com.retropocket.gba.emulator.ui.theme.SystemColors

/**
 * Tỉ lệ khung hình cấp cho GLRetroView.
 *
 * GLRetroView KHÔNG tự quyết kích thước (nó không override onMeasure) — nó chỉ letterbox nội dung
 * bên trong khung mà layout cấp cho. Vì vậy tỉ lệ khung là trách nhiệm của app.
 */
enum class ScreenRatio(val value: Float) {
    /** Mặc định cho mọi hệ máy 1 màn hình. */
    STANDARD(3f / 2f),

    /** Riêng NDS: core ghép 2 màn hình vật lý thành 1 ảnh xếp dọc nên khung phải cao hơn rộng. */
    DUAL_SCREEN(2f / 3f),
}

enum class SystemId {
    GBA, GB, GBC, NES, SNES, GENESIS, SMS, GG, PSX, N64, NDS, ATARI2600, UNKNOWN
}

/**
 * Nguồn sự thật duy nhất cho mọi thứ liên quan tới hệ máy: UI (màu, tên), scanner (đuôi file),
 * emulator (core nào, tỉ lệ khung), gamepad (layout nút nào).
 */
data class GameSystem(
    val id: SystemId,
    val displayName: String,
    val shortName: String,
    val extensions: List<String>,
    val coreFileName: String,
    val ratio: ScreenRatio,
    val accent: Color,
    val padLayout: PadLayout,
    /** Hệ máy cần file BIOS mà app không thể đóng gói (bản quyền hãng) — cảnh báo trước cho user. */
    val requiresBios: Boolean = false,
) {
    companion object {
        val ALL: List<GameSystem> = listOf(
            GameSystem(
                id = SystemId.GBA,
                displayName = "Game Boy Advance",
                shortName = "GBA",
                extensions = listOf("gba"),
                coreFileName = "libmgba_libretro_android.so",
                ratio = ScreenRatio.STANDARD,
                accent = SystemColors.GBA,
                padLayout = PadLayout.GBA,
            ),
            GameSystem(
                id = SystemId.GB,
                displayName = "Game Boy",
                shortName = "GB",
                extensions = listOf("gb"),
                coreFileName = "libgambatte_libretro_android.so",
                ratio = ScreenRatio.STANDARD,
                accent = SystemColors.GB,
                padLayout = PadLayout.TWO_BUTTONS,
            ),
            GameSystem(
                id = SystemId.GBC,
                displayName = "Game Boy Color",
                shortName = "GBC",
                extensions = listOf("gbc"),
                coreFileName = "libgambatte_libretro_android.so",
                ratio = ScreenRatio.STANDARD,
                accent = SystemColors.GBC,
                padLayout = PadLayout.TWO_BUTTONS,
            ),
            GameSystem(
                id = SystemId.NES,
                displayName = "Nintendo Entertainment System",
                shortName = "NES",
                extensions = listOf("nes", "fds", "unf"),
                coreFileName = "libfceumm_libretro_android.so",
                ratio = ScreenRatio.STANDARD,
                accent = SystemColors.NES,
                padLayout = PadLayout.TWO_BUTTONS,
            ),
            GameSystem(
                id = SystemId.SNES,
                displayName = "Super Nintendo",
                shortName = "SNES",
                extensions = listOf("sfc", "smc", "snes"),
                coreFileName = "libsnes9x_libretro_android.so",
                ratio = ScreenRatio.STANDARD,
                accent = SystemColors.SNES,
                padLayout = PadLayout.FOUR_BUTTONS_SHOULDER,
            ),
            GameSystem(
                id = SystemId.GENESIS,
                displayName = "SEGA Genesis",
                shortName = "MD",
                extensions = listOf("md", "gen", "smd", "bin"),
                coreFileName = "libgenesis_plus_gx_libretro_android.so",
                ratio = ScreenRatio.STANDARD,
                accent = SystemColors.GENESIS,
                padLayout = PadLayout.SIX_BUTTONS,
            ),
            GameSystem(
                id = SystemId.SMS,
                displayName = "SEGA Master System",
                shortName = "SMS",
                extensions = listOf("sms"),
                coreFileName = "libgenesis_plus_gx_libretro_android.so",
                ratio = ScreenRatio.STANDARD,
                accent = SystemColors.SMS,
                padLayout = PadLayout.TWO_BUTTONS,
            ),
            GameSystem(
                id = SystemId.GG,
                displayName = "SEGA Game Gear",
                shortName = "GG",
                extensions = listOf("gg"),
                coreFileName = "libgenesis_plus_gx_libretro_android.so",
                ratio = ScreenRatio.STANDARD,
                accent = SystemColors.GG,
                padLayout = PadLayout.TWO_BUTTONS,
            ),
            GameSystem(
                id = SystemId.PSX,
                displayName = "PlayStation",
                shortName = "PS1",
                extensions = listOf("pbp", "chd", "cue", "iso", "m3u"),
                coreFileName = "libpcsx_rearmed_libretro_android.so",
                ratio = ScreenRatio.STANDARD,
                accent = SystemColors.PSX,
                padLayout = PadLayout.PSX,
            ),
            GameSystem(
                id = SystemId.N64,
                displayName = "Nintendo 64",
                shortName = "N64",
                extensions = listOf("n64", "z64", "v64"),
                coreFileName = "libmupen64plus_next_gles3_libretro_android.so",
                ratio = ScreenRatio.STANDARD,
                accent = SystemColors.N64,
                padLayout = PadLayout.N64,
            ),
            GameSystem(
                id = SystemId.NDS,
                displayName = "Nintendo DS",
                shortName = "NDS",
                extensions = listOf("nds"),
                coreFileName = "libmelonds_libretro_android.so",
                ratio = ScreenRatio.DUAL_SCREEN,
                accent = SystemColors.NDS,
                padLayout = PadLayout.FOUR_BUTTONS_SHOULDER,
            ),
            GameSystem(
                id = SystemId.ATARI2600,
                displayName = "Atari 2600",
                shortName = "A2600",
                extensions = listOf("a26"),
                coreFileName = "libstella_libretro_android.so",
                ratio = ScreenRatio.STANDARD,
                accent = SystemColors.ATARI,
                padLayout = PadLayout.ONE_BUTTON,
            ),
        )

        val UNKNOWN = GameSystem(
            id = SystemId.UNKNOWN,
            displayName = "Unknown system",
            shortName = "?",
            extensions = emptyList(),
            coreFileName = "",
            ratio = ScreenRatio.STANDARD,
            accent = SystemColors.UNKNOWN,
            padLayout = PadLayout.TWO_BUTTONS,
        )

        fun byId(id: SystemId): GameSystem = ALL.firstOrNull { it.id == id } ?: UNKNOWN

        fun byIdName(name: String): GameSystem =
            ALL.firstOrNull { it.id.name == name } ?: UNKNOWN

        /**
         * Suy hệ máy từ đuôi file, theo thứ tự khai báo trong [ALL].
         *
         * Đuôi `.bin` cố ý gán cho Genesis chứ không phải PS1: ảnh đĩa PS1 luôn đi kèm file điều
         * khiển `.cue`/`.m3u` và LibraryScanner đã bỏ qua track `.bin` khi thấy file đó, nên `.bin`
         * còn lại gần như chắc chắn là ROM Genesis.
         */
        fun byExtension(extension: String): GameSystem? {
            val ext = extension.lowercase()
            return ALL.firstOrNull { ext in it.extensions }
        }

        /** Mọi đuôi file được coi là ROM khi giải nén / quét thư mục. */
        val ALL_EXTENSIONS: Set<String> = ALL.flatMap { it.extensions }.toSet()
    }
}

/** Bố cục nút cho gamepad ảo — nhóm theo số nút thật của phần cứng, không phải theo từng hệ máy. */
enum class PadLayout {
    ONE_BUTTON,             // Atari 2600: 1 nút bắn
    TWO_BUTTONS,            // NES/GB/GBC/SMS/GG: A + B
    FOUR_BUTTONS_SHOULDER,  // SNES/NDS/GBA: A B X Y + L R
    SIX_BUTTONS,            // Genesis: A B C X Y Z
    PSX,                    // PS1: 4 nút mặt + L1 R1 L2 R2
    N64,                    // N64: A B + analog + C buttons + Z
    GBA,                    // GBA: A B + L R (không có X Y)
}
