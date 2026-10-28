package com.retropocket.gba.emulator.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.retropocket.gba.emulator.data.database.entity.GameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {

    @Query("SELECT * FROM games ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games")
    suspend fun getAll(): List<GameEntity>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getById(id: String): GameEntity?

    /**
     * Trả về rowId của từng bản ghi, `-1` nghĩa là bị bỏ qua do trùng khoá (cùng CRC32 — user
     * import lại đúng ROM cũ). Nhờ đó lớp trên đếm được số game THỰC SỰ thêm mới.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(games: List<GameEntity>): List<Long>

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM games WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("UPDATE games SET isFavourite = :favourite WHERE id = :id")
    suspend fun setFavourite(id: String, favourite: Boolean)

    @Query("UPDATE games SET coverUrl = :url WHERE id = :id")
    suspend fun updateCoverUrl(id: String, url: String)

    @Query("UPDATE games SET lastPlayedAt = :timestamp WHERE id = :id")
    suspend fun setLastPlayed(id: String, timestamp: Long)
}
