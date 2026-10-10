package io.github.wgd753.naturecollect.data

import io.github.wgd753.naturecollect.data.db.BoardDao
import io.github.wgd753.naturecollect.data.db.BoardEntity
import io.github.wgd753.naturecollect.data.db.BoardItemEntity
import io.github.wgd753.naturecollect.data.db.BoardItemType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class BoardRepository(private val dao: BoardDao) {
    fun observeBoards(): Flow<List<BoardEntity>> = dao.observeBoards()

    fun observeItems(boardId: String): Flow<List<BoardItemEntity>> = dao.observeItems(boardId)

    suspend fun ensureDefaultBoard() {
        if (dao.getBoards().isEmpty()) createBoard("我的画板")
    }

    suspend fun createBoard(name: String): BoardEntity {
        val now = System.currentTimeMillis()
        val board = BoardEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            backgroundColor = 0xFFF8F6F1,
            pattern = 1,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertBoard(board)
        return board
    }

    suspend fun updateBoard(board: BoardEntity) = dao.upsertBoard(board.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteBoard(board: BoardEntity) = dao.deleteBoard(board)

    suspend fun addSticker(boardId: String, captureId: String, x: Float, y: Float, rotation: Float): BoardItemEntity =
        add(boardId, BoardItemType.STICKER, captureId = captureId, x = x, y = y, rotation = rotation)

    /** [font] is stored in pathData (unused for text): "hand", "round" or "serif". */
    suspend fun addText(boardId: String, text: String, color: Long, font: String, scale: Float): BoardItemEntity =
        add(boardId, BoardItemType.TEXT, text = text, color = color, pathData = font, x = 0.5f, y = 0.5f, scale = scale)

    suspend fun updateItem(item: BoardItemEntity) = dao.upsertItem(item)

    suspend fun addImage(boardId: String, fileName: String, width: Int, height: Int): BoardItemEntity =
        add(
            boardId,
            BoardItemType.IMAGE,
            text = fileName,
            pathData = "$width,$height",
            x = 0.5f,
            y = 0.5f,
            rotation = (Math.random() * 10 - 5).toFloat(),
        )

    suspend fun addDrawing(boardId: String, pathData: String, color: Long): BoardItemEntity =
        add(boardId, BoardItemType.DRAWING, pathData = pathData, color = color, x = 0f, y = 0f)

    suspend fun setColor(item: BoardItemEntity, color: Long) = dao.upsertItem(item.copy(color = color))

    /** Saves only position/size/angle so it can't clobber a concurrent z-order change. */
    suspend fun updateTransform(item: BoardItemEntity) =
        dao.updateTransform(item.id, item.x, item.y, item.scale, item.rotation)

    suspend fun bringToFront(item: BoardItemEntity) {
        val top = dao.maxZ(item.boardId)
        if (item.z < top) dao.updateZ(item.id, top + 1)
    }

    suspend fun deleteItem(id: String) = dao.deleteItem(id)

    private suspend fun add(
        boardId: String,
        type: BoardItemType,
        captureId: String? = null,
        text: String? = null,
        pathData: String? = null,
        color: Long = 0xFF1F2FC8,
        x: Float,
        y: Float,
        rotation: Float = 0f,
        scale: Float = 1f,
    ): BoardItemEntity {
        val item = BoardItemEntity(
            id = UUID.randomUUID().toString(),
            boardId = boardId,
            type = type,
            captureId = captureId,
            text = text,
            pathData = pathData,
            color = color,
            x = x,
            y = y,
            scale = scale,
            rotation = rotation,
            z = dao.maxZ(boardId) + 1,
        )
        dao.upsertItem(item)
        return item
    }
}
