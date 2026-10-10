package io.github.wgd753.naturecollect.data

import android.graphics.Bitmap
import io.github.wgd753.naturecollect.data.db.BoardDao
import io.github.wgd753.naturecollect.data.db.CaptureDao
import io.github.wgd753.naturecollect.data.db.CaptureEntity
import io.github.wgd753.naturecollect.data.db.RecognitionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

/** A species as shown in the collection: all captures that share a name. */
data class SpeciesGroup(
    val key: String,
    val name: String,
    val nameSci: String?,
    val category: Category,
    val captures: List<CaptureEntity>,
) {
    val cover: CaptureEntity get() = captures.first()
    val firstSeen: Long get() = captures.minOf { it.createdAt }
}

fun List<CaptureEntity>.groupBySpecies(): List<SpeciesGroup> =
    groupBy { speciesKey(it) }
        .map { (key, items) ->
            val sorted = items.sortedByDescending { it.createdAt }
            SpeciesGroup(
                key = key,
                name = sorted.first().displayName,
                nameSci = sorted.firstNotNullOfOrNull { it.nameSci },
                category = Category.fromKey(sorted.first().category),
                captures = sorted,
            )
        }
        .sortedByDescending { group -> group.captures.maxOf { it.createdAt } }

/** Recognised captures group by name; unknown ones each stand alone. */
fun speciesKey(capture: CaptureEntity): String =
    capture.nameZh?.takeIf { it.isNotBlank() }?.let { "name:$it" } ?: "id:${capture.id}"

class CaptureRepository(
    private val captureDao: CaptureDao,
    private val boardDao: BoardDao,
    private val media: MediaFiles,
) {
    fun observeAll(): Flow<List<CaptureEntity>> = captureDao.observeAll()

    fun observe(id: String): Flow<CaptureEntity?> = captureDao.observe(id)

    suspend fun get(id: String): CaptureEntity? = captureDao.get(id)

    suspend fun unrecognized(): List<CaptureEntity> = captureDao.getUnrecognized()

    suspend fun missingPlaces(): List<CaptureEntity> = captureDao.getMissingPlaces()

    /** Saves the photo and sticker files and records a new capture awaiting recognition. */
    suspend fun create(
        photo: Bitmap,
        sticker: Bitmap,
        latitude: Double?,
        longitude: Double?,
        takenAt: Long,
    ): CaptureEntity = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val photoFile = "$id-photo.jpg"
        val stickerFile = "$id-sticker.png"
        media.saveJpeg(photoFile, photo)
        media.savePng(stickerFile, sticker)
        val now = System.currentTimeMillis()
        val capture = CaptureEntity(
            id = id,
            createdAt = takenAt,
            updatedAt = now,
            latitude = latitude,
            longitude = longitude,
            placeName = null,
            photoFile = photoFile,
            stickerFile = stickerFile,
            stickerWidth = sticker.width,
            stickerHeight = sticker.height,
            nameZh = null,
            nameSci = null,
            category = Category.OTHER.name,
            confidence = null,
            description = null,
            alternatives = null,
            status = RecognitionStatus.PENDING,
            errorMessage = null,
            note = null,
        )
        captureDao.insert(capture)
        capture
    }

    suspend fun update(capture: CaptureEntity) {
        captureDao.upsert(capture.copy(updatedAt = System.currentTimeMillis()))
    }

    /** User-entered name; marks the capture as manually identified. */
    suspend fun rename(id: String, nameZh: String, category: Category) {
        val capture = captureDao.get(id) ?: return
        update(
            capture.copy(
                nameZh = nameZh.trim(),
                category = category.name,
                status = RecognitionStatus.MANUAL,
                errorMessage = null,
            ),
        )
    }

    suspend fun updateNotes(id: String, placeName: String?, note: String?) {
        val capture = captureDao.get(id) ?: return
        update(capture.copy(placeName = placeName?.trim()?.ifEmpty { null }, note = note?.trim()?.ifEmpty { null }))
    }

    suspend fun markPending(id: String) {
        val capture = captureDao.get(id) ?: return
        update(capture.copy(status = RecognitionStatus.PENDING, errorMessage = null))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val capture = captureDao.get(id) ?: return@withContext
        boardDao.deleteItemsForCapture(id)
        captureDao.delete(capture)
        media.delete(capture.photoFile)
        media.delete(capture.stickerFile)
    }
}
