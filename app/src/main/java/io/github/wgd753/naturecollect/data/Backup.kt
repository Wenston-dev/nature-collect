package io.github.wgd753.naturecollect.data

import android.content.Context
import android.net.Uri
import io.github.wgd753.naturecollect.data.db.AppDatabase
import io.github.wgd753.naturecollect.data.db.BoardEntity
import io.github.wgd753.naturecollect.data.db.BoardItemEntity
import io.github.wgd753.naturecollect.data.db.BoardItemType
import io.github.wgd753.naturecollect.data.db.CaptureEntity
import io.github.wgd753.naturecollect.data.db.RecognitionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Exports everything to a single zip (data.json plus the media files) and
 * imports it back, merging by id so importing twice is harmless.
 */
class Backup(
    private val context: Context,
    private val db: AppDatabase,
    private val media: MediaFiles,
    private val categories: CategoryStore,
) {
    suspend fun export(target: Uri): Int = withContext(Dispatchers.IO) {
        val captures = db.captureDao().getAll()
        val boards = db.boardDao().getBoards()
        val items = db.boardDao().getAllItems()
        val json = JSONObject()
            .put("version", 1)
            .put("captures", JSONArray(captures.map { it.toJson() }))
            .put("boards", JSONArray(boards.map { it.toJson() }))
            .put("boardItems", JSONArray(items.map { it.toJson() }))
            .put("categories", CategoryStore.encode(Category.entries))
        val out = context.contentResolver.openOutputStream(target) ?: error("无法写入备份文件")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(DATA_ENTRY))
            zip.write(json.toString().toByteArray())
            zip.closeEntry()
            val names = captures.flatMap { listOf(it.photoFile, it.stickerFile) } +
                items.filter { it.type == BoardItemType.IMAGE }.mapNotNull { it.text }
            for (name in names) {
                val file = media.file(name)
                if (!file.exists()) continue
                zip.putNextEntry(ZipEntry("$MEDIA_PREFIX$name"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        captures.size
    }

    suspend fun import(source: Uri): Int = withContext(Dispatchers.IO) {
        var json: JSONObject? = null
        val input = context.contentResolver.openInputStream(source) ?: error("无法读取备份文件")
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == DATA_ENTRY -> json = JSONObject(zip.readBytes().decodeToString())
                    entry.name.startsWith(MEDIA_PREFIX) -> {
                        val name = File(entry.name.removePrefix(MEDIA_PREFIX)).name
                        media.file(name).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
        val data = json ?: error("这不是自然收集的备份文件")
        val captures = data.getJSONArray("captures").objects().map { it.toCapture() }
        captures.forEach { db.captureDao().upsert(it) }
        data.optJSONArray("categories")?.let { array ->
            // Keep categories added on this phone; the backup's versions win for shared keys.
            val incoming = CategoryStore.decode(array)
            val merged = Category.entries.filter { local -> incoming.none { it.key == local.key } }
            categories.save(incoming + merged)
        }
        data.optJSONArray("boards")?.objects()?.forEach { db.boardDao().upsertBoard(it.toBoard()) }
        data.optJSONArray("boardItems")?.objects()?.forEach { db.boardDao().upsertItem(it.toBoardItem()) }
        captures.size
    }

    private companion object {
        const val DATA_ENTRY = "data.json"
        const val MEDIA_PREFIX = "media/"
    }
}

private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)
private fun JSONObject.optDoubleOrNull(key: String): Double? = if (isNull(key) || !has(key)) null else getDouble(key)

private fun CaptureEntity.toJson() = JSONObject()
    .put("id", id).put("createdAt", createdAt).put("updatedAt", updatedAt)
    .put("latitude", latitude ?: JSONObject.NULL).put("longitude", longitude ?: JSONObject.NULL)
    .put("placeName", placeName ?: JSONObject.NULL)
    .put("photoFile", photoFile).put("stickerFile", stickerFile)
    .put("stickerWidth", stickerWidth).put("stickerHeight", stickerHeight)
    .put("nameZh", nameZh ?: JSONObject.NULL).put("nameSci", nameSci ?: JSONObject.NULL)
    .put("category", category).put("confidence", confidence?.toDouble() ?: JSONObject.NULL)
    .put("description", description ?: JSONObject.NULL).put("alternatives", alternatives ?: JSONObject.NULL)
    .put("status", status.name).put("note", note ?: JSONObject.NULL)

private fun JSONObject.toCapture() = CaptureEntity(
    id = getString("id"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt"),
    latitude = optDoubleOrNull("latitude"),
    longitude = optDoubleOrNull("longitude"),
    placeName = optStringOrNull("placeName"),
    photoFile = getString("photoFile"),
    stickerFile = getString("stickerFile"),
    stickerWidth = getInt("stickerWidth"),
    stickerHeight = getInt("stickerHeight"),
    nameZh = optStringOrNull("nameZh"),
    nameSci = optStringOrNull("nameSci"),
    category = getString("category"),
    confidence = optDoubleOrNull("confidence")?.toFloat(),
    description = optStringOrNull("description"),
    alternatives = optStringOrNull("alternatives"),
    status = runCatching { RecognitionStatus.valueOf(getString("status")) }.getOrDefault(RecognitionStatus.PENDING)
        .let { if (it == RecognitionStatus.RUNNING) RecognitionStatus.PENDING else it },
    errorMessage = null,
    note = optStringOrNull("note"),
)

private fun BoardEntity.toJson() = JSONObject()
    .put("id", id).put("name", name).put("backgroundColor", backgroundColor).put("pattern", pattern)
    .put("createdAt", createdAt).put("updatedAt", updatedAt)

private fun JSONObject.toBoard() = BoardEntity(
    id = getString("id"),
    name = getString("name"),
    backgroundColor = getLong("backgroundColor"),
    pattern = getInt("pattern"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt"),
)

private fun BoardItemEntity.toJson() = JSONObject()
    .put("id", id).put("boardId", boardId).put("type", type.name)
    .put("captureId", captureId ?: JSONObject.NULL).put("text", text ?: JSONObject.NULL)
    .put("pathData", pathData ?: JSONObject.NULL).put("color", color)
    .put("x", x.toDouble()).put("y", y.toDouble()).put("scale", scale.toDouble())
    .put("rotation", rotation.toDouble()).put("z", z)

private fun JSONObject.toBoardItem() = BoardItemEntity(
    id = getString("id"),
    boardId = getString("boardId"),
    type = BoardItemType.valueOf(getString("type")),
    captureId = optStringOrNull("captureId"),
    text = optStringOrNull("text"),
    pathData = optStringOrNull("pathData"),
    color = getLong("color"),
    x = getDouble("x").toFloat(),
    y = getDouble("y").toFloat(),
    scale = getDouble("scale").toFloat(),
    rotation = getDouble("rotation").toFloat(),
    z = getInt("z"),
)
