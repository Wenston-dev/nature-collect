package io.github.wgd753.naturecollect.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {
    @Query("SELECT * FROM captures ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE id = :id")
    fun observe(id: String): Flow<CaptureEntity?>

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun get(id: String): CaptureEntity?

    @Query("SELECT * FROM captures")
    suspend fun getAll(): List<CaptureEntity>

    @Query("SELECT * FROM captures WHERE status IN ('PENDING', 'FAILED', 'RUNNING') ORDER BY createdAt")
    suspend fun getUnrecognized(): List<CaptureEntity>

    @Query("SELECT * FROM captures WHERE placeName IS NULL AND latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY createdAt DESC LIMIT 30")
    suspend fun getMissingPlaces(): List<CaptureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(capture: CaptureEntity)

    @Upsert
    suspend fun upsert(capture: CaptureEntity)

    @Delete
    suspend fun delete(capture: CaptureEntity)
}

@Dao
interface BoardDao {
    @Query("SELECT * FROM boards ORDER BY createdAt")
    fun observeBoards(): Flow<List<BoardEntity>>

    @Query("SELECT * FROM boards")
    suspend fun getBoards(): List<BoardEntity>

    @Query("SELECT * FROM board_items WHERE boardId = :boardId ORDER BY z")
    fun observeItems(boardId: String): Flow<List<BoardItemEntity>>

    @Query("SELECT * FROM board_items")
    suspend fun getAllItems(): List<BoardItemEntity>

    @Query("SELECT COALESCE(MAX(z), 0) FROM board_items WHERE boardId = :boardId")
    suspend fun maxZ(boardId: String): Int

    @Upsert
    suspend fun upsertBoard(board: BoardEntity)

    @Delete
    suspend fun deleteBoard(board: BoardEntity)

    @Upsert
    suspend fun upsertItem(item: BoardItemEntity)

    @Query("UPDATE board_items SET x = :x, y = :y, scale = :scale, rotation = :rotation WHERE id = :id")
    suspend fun updateTransform(id: String, x: Float, y: Float, scale: Float, rotation: Float)

    @Query("UPDATE board_items SET z = :z WHERE id = :id")
    suspend fun updateZ(id: String, z: Int)

    @Query("DELETE FROM board_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("DELETE FROM board_items WHERE captureId = :captureId")
    suspend fun deleteItemsForCapture(captureId: String)
}
