package io.github.wgd753.naturecollect.ui.cutout

import android.graphics.Bitmap
import android.graphics.Color

/** Translucent ink-blue highlight over the selected object. */
fun maskOverlay(alpha: FloatArray, width: Int, height: Int): Bitmap {
    val pixels = IntArray(width * height) { i ->
        val a = (alpha[i] * 140).toInt().coerceIn(0, 255)
        Color.argb(a, 70, 90, 255)
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}
