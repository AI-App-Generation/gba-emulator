package com.retropocket.gba.emulator.corefeatures.library

import android.content.Context
import android.util.Log
import com.retropocket.gba.emulator.core.file.FileManager
import com.retropocket.gba.emulator.data.domain.GameSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Cài các game mẫu đóng gói sẵn trong APK vào thư viện ở lần chạy đầu.
 *
 * Toàn bộ game mẫu đều là homebrew có license cho phép phân phối lại kể cả thương mại
 * (MIT / zlib / GPL-3.0+ / CC BY / CC BY-SA);
 * xem [SampleGames.ALL] và màn Licenses để biết attribution bắt buộc.
 */
object SampleGameInstaller {

    private const val ASSET_DIR = "sample_roms"
    private const val COVER_ASSET_DIR = "sample_covers"

    /**
     * Chép ảnh bìa đóng gói sẵn sang [FileManager.coversDir]. Chạy MỖI lần mở app (không phụ thuộc
     * cờ "đã cài game mẫu"): bản cập nhật sau có thêm/đổi ảnh thì user cũ vẫn nhận được. File đã
     * có thì bỏ qua nên gần như không tốn gì.
     */
    suspend fun installCovers(context: Context) = withContext(Dispatchers.IO) {
        val names = runCatching { context.assets.list(COVER_ASSET_DIR)?.toList().orEmpty() }
            .getOrDefault(emptyList())
        names.forEach { name ->
            val target = File(FileManager.coversDir(), name)
            if (target.exists()) return@forEach
            runCatching {
                target.parentFile?.mkdirs()
                context.assets.open("$COVER_ASSET_DIR/$name").use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
            }.onFailure { Log.w("SampleGameInstaller", "Cannot install cover $name", it) }
        }
    }

    suspend fun installIfNeeded(context: Context): Boolean = withContext(Dispatchers.IO) {
        // Ảnh bìa phải có trên đĩa TRƯỚC khi ROM được quét, để bản ghi nhận luôn ảnh cục bộ.
        installCovers(context)

        // Chỉ lấy file ROM thật; bỏ qua README hướng dẫn nằm cùng thư mục assets.
        val assets = runCatching { context.assets.list(ASSET_DIR)?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.substringAfterLast('.', "").lowercase() in GameSystem.ALL_EXTENSIONS }
        if (assets.isEmpty()) return@withContext false

        val installed = mutableListOf<File>()
        assets.forEach { name ->
            val target = File(FileManager.romsDir(), name)
            if (target.exists()) return@forEach
            runCatching {
                context.assets.open("$ASSET_DIR/$name").use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
                installed += target
            }.onFailure { Log.w("SampleGameInstaller", "Cannot install $name", it) }
        }

        if (installed.isNotEmpty()) LibraryScanner.scanAsSamples(installed)
        installed.isNotEmpty()
    }
}

/** Thông tin license của game mẫu — hiển thị nguyên văn ở màn Licenses. */
data class SampleGameLicense(
    val title: String,
    val author: String,
    val license: String,
    val url: String,
)

object SampleGames {
    val ALL = listOf(
        SampleGameLicense(
            title = "Tobu Tobu Girl",
            author = "Tangram Games (Simon Larsen, Lukas Erekson, Sebastian Kristensen)",
            license = "Code: MIT · Assets: CC BY 4.0",
            url = "github.com/SimonLarsen/tobutobugirl",
        ),
        SampleGameLicense(
            title = "uCity",
            author = "Antonio Niño Díaz",
            license = "Code: GPL-3.0-or-later · Graphics & music: CC BY-SA 4.0",
            url = "github.com/AntonioND/ucity",
        ),
        SampleGameLicense(
            title = "Libbet and the Magic Floor",
            author = "Damian Yerrick",
            license = "zlib License",
            url = "github.com/pinobatch/libbet",
        ),
        SampleGameLicense(
            title = "Concentration Room",
            author = "Damian Yerrick",
            license = "GPL-3.0-or-later (exact ROM copies may be distributed without source)",
            url = "github.com/pinobatch/croom-nes",
        ),
        SampleGameLicense(
            title = "Attack on Voxelburg",
            author = "John Tsiombikas (Mutant Stargoat)",
            license = "GPL-3.0-or-later",
            url = "github.com/MutantStargoat/voxelburg",
        ),
    )

    val LIBRARIES = listOf(
        SampleGameLicense(
            title = "LibretroDroid",
            author = "Filippo Scognamiglio",
            license = "GPL-3.0",
            url = "github.com/Swordfish90/LibretroDroid",
        ),
        SampleGameLicense(
            title = "RadialGamePad",
            author = "Filippo Scognamiglio",
            license = "GPL-3.0",
            url = "github.com/Swordfish90/RadialGamePad",
        ),
        SampleGameLicense(
            title = "Libretro cores (mGBA, Gambatte, FCEUmm, Snes9x, Genesis Plus GX, " +
                "PCSX ReARMed, Mupen64Plus-Next, melonDS, DeSmuME, Stella)",
            author = "Libretro community",
            license = "GPL / MPL tuỳ core",
            url = "buildbot.libretro.com",
        ),
        SampleGameLicense(
            title = "Press Start 2P",
            author = "CodeMan38",
            license = "SIL Open Font License 1.1",
            url = "fonts.google.com/specimen/Press+Start+2P",
        ),
    )
}
