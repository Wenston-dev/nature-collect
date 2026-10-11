package io.github.wgd753.naturecollect.ui.cutout

import android.graphics.Bitmap
import android.graphics.Color

/** Translucent ochre wash over the selected object (the palette's Sun). */
fun maskOverlay(alpha: FloatArray, width: Int, height: Int): Bitmap {
    val pixels = IntArray(width * height) { i ->
        val a = (alpha[i] * 150).toInt().coerceIn(0, 255)
        Color.argb(a, 227, 178, 91)
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}
