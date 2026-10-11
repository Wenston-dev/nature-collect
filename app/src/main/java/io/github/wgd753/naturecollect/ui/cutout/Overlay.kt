package io.github.wgd753.naturecollect.ui.cutout

import android.graphics.Bitmap
import android.graphics.Color

/** Translucent rust tint over the selected object (the palette's Rust, #B4532E). */
fun maskOverlay(alpha: FloatArray, width: Int, height: Int): Bitmap {
    val pixels = IntArray(width * height) { i ->
        val a = (alpha[i] * 120).toInt().coerceIn(0, 255)
        Color.argb(a, 180, 83, 46)
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}
