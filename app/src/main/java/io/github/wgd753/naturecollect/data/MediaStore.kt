package io.github.wgd753.naturecollect.data

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

/** Photos and stickers live in the app's private files directory. */
class MediaFiles(context: Context) {
    val dir: File = File(context.filesDir, "media").apply { mkdirs() }

    fun file(name: String): File = File(dir, name)

    fun saveJpeg(name: String, bitmap: Bitmap, quality: Int = 90): File =
        write(name) { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }

    /** Stickers need their alpha channel; PNG keeps the edges crisp. */
    fun savePng(name: String, bitmap: Bitmap): File =
        write(name) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    fun delete(name: String) {
        file(name).delete()
    }

    private fun write(name: String, block: (FileOutputStream) -> Unit): File {
        val target = file(name)
        val tmp = File(dir, "$name.tmp")
        FileOutputStream(tmp).use(block)
        tmp.renameTo(target)
        return target
    }
}
