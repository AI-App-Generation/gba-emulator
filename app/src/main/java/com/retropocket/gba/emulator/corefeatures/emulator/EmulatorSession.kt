package com.retropocket.gba.emulator.corefeatures.emulator

import android.content.Context
import com.retropocket.gba.emulator.core.file.FileManager
import com.retropocket.gba.emulator.data.database.entity.GameEntity
import com.retropocket.gba.emulator.data.domain.SystemId
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.Variable
import java.io.File

/**
 * Gói toàn bộ phần cấu hình LibretroDroid để màn chơi game chỉ việc hiển thị.
 *
 * Lưu ý vòng đời: GLRetroView là GLSurfaceView giữ Context nên KHÔNG được đặt trong ViewModel.
 * ViewModel chỉ giữ dữ liệu game + mảng byte save-state; view được tạo lại khi cấu hình đổi rồi
 * nạp lại trạng thái từ byte đã lưu.
 */
object EmulatorSession {

    fun buildData(context: Context, game: GameEntity): GLRetroViewData {
        val system = game.system
        return GLRetroViewData(context).apply {
            coreFilePath = CoreCatalog.coreFile(system).absolutePath
            gameFilePath = game.filePath
            systemDirectory = FileManager.systemDir().absolutePath
            savesDirectory = FileManager.savesDir().absolutePath
            // SRAM = "pin lưu game" của chính tựa game (menu Save trong Pokémon, Zelda...).
            // Không dựa vào việc core tự ghi ra savesDirectory: core chỉ flush lúc unload, mà
            // đường unload rất dễ bị bỏ lỡ. Nạp/ghi tay qua saveRAMState + serializeSRAM chắc hơn.
            saveRAMState = sramFile(game).takeIf { it.exists() && it.length() > 0 }?.readBytes()
            variables = defaultVariables(system.id)
            shader = ShaderConfig.Default
            rumbleEventsEnabled = true
            preferLowLatencyAudio = true
        }
    }

    /**
     * Biến cấu hình riêng của từng core.
     * NDS: ép ghép 2 màn hình theo chiều dọc để khớp với tỉ lệ khung 2:3 mà UI cấp cho view,
     * đồng thời bật chế độ chạm để màn dưới nhận input từ ngón tay.
     */
    private fun defaultVariables(systemId: SystemId): Array<Variable> = when (systemId) {
        SystemId.NDS -> arrayOf(
            Variable("melonds_screen_layout", "Top/Bottom"),
            Variable("melonds_touch_mode", "Touch"),
            Variable("melonds_threaded_renderer", "enabled"),
            Variable("desmume_screens_layout", "top/bottom"),
            Variable("desmume_pointer_type", "touch"),
        )

        SystemId.N64 -> arrayOf(
            Variable("mupen64plus-43screensize", "640x480"),
        )

        else -> emptyArray()
    }

    fun stateFile(game: GameEntity): File = File(FileManager.statesDir(), "${game.id}.state")

    fun sramFile(game: GameEntity): File = File(FileManager.savesDir(), "${game.id}.srm")

    /**
     * Lưu TẤT CẢ những gì cần để chơi tiếp: save-state (ảnh chụp toàn bộ máy ảo) và SRAM (bản lưu
     * do chính game ghi). Thiếu SRAM thì user save trong game xong vẫn mất tiến trình.
     *
     * Chỉ gọi được khi GLRetroView còn sống — cả hai hàm serialize đều chặn luồng gọi để chờ
     * luồng emulation trả lời.
     */
    fun persist(game: GameEntity, retroView: GLRetroView): Boolean {
        val stateOk = saveState(game, retroView)
        saveSram(game, retroView)
        return stateOk
    }

    fun saveState(game: GameEntity, retroView: GLRetroView): Boolean = runCatching {
        writeAtomic(stateFile(game), retroView.serializeState())
    }.getOrDefault(false)

    fun saveSram(game: GameEntity, retroView: GLRetroView): Boolean = runCatching {
        writeAtomic(sramFile(game), retroView.serializeSRAM())
    }.getOrDefault(false)

    /** Ghi ra đĩa mảng byte đã serialize từ trước (view có thể đã bị huỷ khi xoay màn hình). */
    fun writeState(game: GameEntity, bytes: ByteArray): Boolean =
        runCatching { writeAtomic(stateFile(game), bytes) }.getOrDefault(false)

    /** Ghi qua file .tmp rồi rename: mất điện giữa chừng không để lại save-state hỏng. */
    private fun writeAtomic(target: File, bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        return true
    }

    /**
     * Dịch mã lỗi của LibretroDroid sang câu người dùng hiểu được.
     * Để nguyên mã số thì user chỉ thấy màn đen và không biết phải làm gì.
     */
    fun describeError(code: Int): String = when (code) {
        GLRetroView.ERROR_LOAD_LIBRARY ->
            "The emulator core for this system could not be loaded on this device."
        GLRetroView.ERROR_LOAD_GAME ->
            "This ROM could not be loaded. The file may be damaged, compressed, or need a BIOS."
        GLRetroView.ERROR_GL_NOT_COMPATIBLE ->
            "This device's graphics driver is not compatible with this core."
        GLRetroView.ERROR_SERIALIZATION ->
            "The save state could not be read for this game."
        else -> "The emulator stopped unexpectedly (error $code)."
    }

    fun loadState(game: GameEntity, retroView: GLRetroView): Boolean = runCatching {
        val file = stateFile(game)
        if (!file.exists()) return false
        retroView.unserializeState(file.readBytes())
    }.getOrDefault(false)

    fun hasState(game: GameEntity): Boolean = stateFile(game).exists()

    /**
     * Chọn loại tay cầm phù hợp: core tự báo danh sách nó hỗ trợ, app dò theo tên rồi kích hoạt.
     * Không hardcode id vì mỗi core đánh số khác nhau cho cùng một loại tay cầm.
     */
    fun applyPreferredController(retroView: GLRetroView, systemId: SystemId) {
        if (systemId != SystemId.PSX) return
        runCatching {
            val controllers = retroView.getControllers()
            val port0 = controllers.firstOrNull() ?: return
            val dualShock = port0.firstOrNull { it.description?.contains("DualShock", true) == true }
                ?: port0.firstOrNull { it.description?.contains("Analog", true) == true }
                ?: return
            retroView.setControllerType(0, dualShock.id)
        }
    }
}
