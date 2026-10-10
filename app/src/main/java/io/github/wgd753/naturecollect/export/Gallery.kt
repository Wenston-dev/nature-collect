package io.github.wgd753.naturecollect.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Saves images into the phone's gallery under Pictures/自然收集. */
object Gallery {
    /** Writes [bitmap] and returns a human-readable description of where it went. */
    fun save(context: Context, bitmap: Bitmap, name: String, jpeg: Boolean = false): String {
        val ext = if (jpeg) "jpg" else "png"
        val format = if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
        val quality = if (jpeg) 92 else 100
        val fileName = "自然收集-$name-${System.currentTimeMillis()}.$ext"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, if (jpeg) "image/jpeg" else "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/自然收集")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("无法写入相册")
            context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(format, quality, it) }
            return "相册的「自然收集」文件夹"
        }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "export").apply { mkdirs() }
        File(dir, fileName).outputStream().use { bitmap.compress(format, quality, it) }
        return dir.path
    }
}
