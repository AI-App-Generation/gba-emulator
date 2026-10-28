package com.retropocket.gba.emulator.data.database.repo

import android.content.Context
import com.retropocket.gba.emulator.data.database.AppDatabase
import com.retropocket.gba.emulator.data.database.entity.GameEntity
import com.retropocket.gba.emulator.corefeatures.library.UnlockStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Kho game dùng chung toàn app.
 *
 * Không dùng DI framework (theo APP_STRUCTURE.md) — khởi tạo một lần trong GlobalApp.onCreate()
 * rồi truy cập trực tiếp qua object.
 */
object GameRepo {

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dao by lazy { AppDatabase.get(appContext).gameDao() }

    /** Danh sách game realtime — mọi màn hình quan sát chung một nguồn này. */
    lateinit var games: StateFlow<List<GameEntity>>
        private set

    /** Bật lên khi đang quét/import để UI hiện trạng thái chờ. */
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        games = dao.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())
    }

    fun setBusy(busy: Boolean) {
        _isBusy.value = busy
    }

    suspend fun getAll(): List<GameEntity> = dao.getAll()

    suspend fun getById(id: String): GameEntity? = dao.getById(id)

    /** Số bản ghi thực sự được thêm (bỏ qua những ROM đã có trong thư viện). */
    suspend fun insertAll(games: List<GameEntity>): Int =
        dao.insertAll(games).count { it >= 0 }

    suspend fun deleteByIds(ids: List<String>) {
        dao.deleteByIds(ids)
        // Trả slot mở khoá ngay tại chỗ xoá: đây là nơi DUY NHẤT mọi đường xoá đi qua (xoá thủ
        // công, và scanner dọn bản ghi mất file), nên không có đường nào giữ slot lại được.
        UnlockStore.releaseSlots(ids)
    }

    suspend fun updateCoverUrl(id: String, url: String) = dao.updateCoverUrl(id, url)

    fun toggleFavourite(game: GameEntity) {
        scope.launch { dao.setFavourite(game.id, !game.isFavourite) }
    }

    fun markPlayed(id: String) {
        scope.launch { dao.setLastPlayed(id, System.currentTimeMillis()) }
    }

    /** Xoá game khỏi thư viện kèm xoá file ROM. Game mẫu không được xoá. */
    fun delete(game: GameEntity) {
        if (game.isSample) return
        scope.launch {
            runCatching { java.io.File(game.filePath).delete() }
            dao.deleteById(game.id)
            UnlockStore.releaseSlots(listOf(game.id))
        }
    }
}
