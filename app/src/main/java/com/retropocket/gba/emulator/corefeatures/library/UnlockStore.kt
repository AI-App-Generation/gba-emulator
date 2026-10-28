package com.retropocket.gba.emulator.corefeatures.library

import android.content.Context
import android.util.Log
import com.retropocket.gba.emulator.core.AppCapability
import com.retropocket.gba.emulator.data.database.entity.GameEntity
import com.retropocket.gba.emulator.features.common.PremiumState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Ghi nhớ những game user đã "sở hữu" bằng cách xem quảng cáo thưởng.
 *
 * Cố ý KHÔNG thêm cột vào Room: bảng game đang chạy version 1 với
 * `fallbackToDestructiveMigration(dropAllTables = true)`, nâng version mà quên viết migration là
 * xoá sạch thư viện của user. Trạng thái mở khoá cũng không liên quan tới dữ liệu ROM, nên tách
 * ra một file JSON nhỏ vừa an toàn vừa dễ sửa tay khi cần.
 *
 * File này CHỈ lưu những game user tự kiếm được bằng quảng cáo thưởng. Trạng thái trả phí không
 * nằm ở đây và không nằm ở bất kỳ đâu trên máy — xem [PremiumState].
 */
object UnlockStore {

    private const val FILE_NAME = "unlocked_games.json"
    private const val KEY_VERSION = "version"
    private const val KEY_IDS = "ids"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var file: File

    private val _unlockedIds = MutableStateFlow<Set<String>>(emptySet())
    val unlockedIds: StateFlow<Set<String>> = _unlockedIds.asStateFlow()

    fun init(context: Context) {
        file = File(context.applicationContext.filesDir, FILE_NAME)
        scope.launch { load() }
    }

    /**
     * Game coi như đã sở hữu khi: là game mẫu, user đang trả phí, hoặc đã tự mở khoá trước đó.
     *
     * Dựa vào [PremiumState] chứ không dựa vào "quảng cáo có đang bật không": lúc app vừa khởi
     * động, cờ quảng cáo còn là false nên suy ra từ nó sẽ khiến mọi game trông như đã mở khoá.
     */
    fun isUnlocked(game: GameEntity): Boolean =
        game.isSample ||
            PremiumState.isPremium.value ||
            game.id in _unlockedIds.value

    /**
     * Mở khoá một game, trả về false khi đã hết slot của bản miễn phí.
     *
     * Chặn ngay TẠI ĐÂY chứ không ở nút bấm: mở khoá là điểm hội tụ của mọi đường (quảng cáo
     * thưởng, inter dự phòng, và bất cứ cơ chế nào thêm sau này), nên canh ở đây là bịt hết một
     * lượt. Canh ở từng nút là kiểu vá từng lỗ, sót một cái là thủng.
     */
    fun unlock(gameId: String): Boolean {
        if (gameId in _unlockedIds.value) return true
        if (!AppCapability.canUnlockMore(usedSlots())) return false
        _unlockedIds.value = _unlockedIds.value + gameId
        persist()
        return true
    }

    /** Số slot đã dùng. Game mẫu không bao giờ nằm trong tập này nên không tốn slot. */
    fun usedSlots(): Int = _unlockedIds.value.size

    /**
     * Thu hồi slot của những game không còn trong thư viện.
     *
     * Gọi từ [com.retropocket.gba.emulator.data.database.repo.GameRepo] mỗi khi xoá, nếu không id
     * của game đã xoá sẽ nằm lại trong file và giữ slot vĩnh viễn — user xoá game mà không lấy
     * lại được chỗ.
     */
    fun releaseSlots(gameIds: Collection<String>) {
        val remaining = _unlockedIds.value - gameIds.toSet()
        if (remaining.size == _unlockedIds.value.size) return
        _unlockedIds.value = remaining
        persist()
    }

    private fun load() {
        runCatching {
            if (!file.exists()) return@runCatching
            val json = JSONObject(file.readText())
            val array = json.optJSONArray(KEY_IDS) ?: JSONArray()
            _unlockedIds.value = buildSet {
                for (i in 0 until array.length()) array.optString(i)?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }.onFailure {
            // File hỏng thì coi như chưa mở khoá gì, không để văng app vì một file phụ trợ.
            Log.w("UnlockStore", "Cannot read $FILE_NAME", it)
        }
    }

    private fun persist() {
        val snapshotIds = _unlockedIds.value
        scope.launch {
            runCatching {
                val json = JSONObject()
                    .put(KEY_VERSION, 1)
                    .put(KEY_IDS, JSONArray().apply { snapshotIds.forEach { put(it) } })
                // Ghi qua file tạm rồi đổi tên: mất điện giữa chừng không để lại JSON cụt.
                val temp = File(file.parentFile, "$FILE_NAME.tmp")
                temp.writeText(json.toString())
                if (!temp.renameTo(file)) {
                    temp.copyTo(file, overwrite = true)
                    temp.delete()
                }
            }.onFailure { Log.w("UnlockStore", "Cannot write $FILE_NAME", it) }
        }
    }
}
