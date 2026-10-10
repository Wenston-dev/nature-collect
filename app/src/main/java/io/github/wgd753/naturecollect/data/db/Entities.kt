package io.github.wgd753.naturecollect.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where a capture is in the recognition pipeline. */
enum class RecognitionStatus { PENDING, RUNNING, DONE, FAILED, MANUAL }

/** One thing collected: the photo, its sticker, where and when, and what it is. */
@Entity(tableName = "captures", indices = [Index("createdAt"), Index("nameZh")])
data class CaptureEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    val placeName: String?,
    /** File names inside the app's media directory. */
    val photoFile: String,
    val stickerFile: String,
    val stickerWidth: Int,
    val stickerHeight: Int,
    val nameZh: String?,
    val nameSci: String?,
    val category: String,
    val confidence: Float?,
    val description: String?,
    /** Other candidate names from recognition, separated by '|'. */
    val alternatives: String?,
    val status: RecognitionStatus,
    val errorMessage: String?,
    val note: String?,
) {
    val displayName: String get() = nameZh?.takeIf { it.isNotBlank() } ?: "未知物种"
}

@Entity(tableName = "boards")
data class BoardEntity(
    @PrimaryKey val id: String,
    val name: String,
    val backgroundColor: Long,
    /** 0 = plain, 1 = dots, 2 = grid. */
    val pattern: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class BoardItemType { STICKER, TEXT, DRAWING }

/**
 * An element placed on a board. Positions are in board units: x/y are the
 * centre of the item as a fraction of board width/height so boards look the
 * same on any screen size.
 */
@Entity(
    tableName = "board_items",
    foreignKeys = [
        ForeignKey(
            entity = BoardEntity::class,
            parentColumns = ["id"],
            childColumns = ["boardId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("boardId")],
)
data class BoardItemEntity(
    @PrimaryKey val id: String,
    val boardId: String,
    val type: BoardItemType,
    val captureId: String?,
    val text: String?,
    /** For DRAWING: points "x,y;x,y;..." as fractions of the board size. */
    val pathData: String?,
    val color: Long,
    val x: Float,
    val y: Float,
    val scale: Float,
    val rotation: Float,
    val z: Int,
)
