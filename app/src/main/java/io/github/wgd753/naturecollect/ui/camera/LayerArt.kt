package io.github.wgd753.naturecollect.ui.camera

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Small overlay bitmaps for the scan screen, built off the main thread. */
object LayerArt {
    private const val SIDE = 360

    /** How long the layering beat lasts: each candidate gets a moment, then the pick settles. */
    fun showMillis(count: Int): Long = 260L * count + 520L

    /**
     * A SAM candidate mask as an overlay: a crisp 2px contour over a very faint fill,
     * white so the screen can tint it (white for candidates, rust for the pick).
     */
    fun outline(alpha: FloatArray, w: Int, h: Int): Bitmap {
        val scale = SIDE.toFloat() / max(w, h)
        val sw = (w * scale).roundToInt().coerceAtLeast(1)
        val sh = (h * scale).roundToInt().coerceAtLeast(1)
        val inside = BooleanArray(sw * sh) { i ->
            val x = ((i % sw) / scale).toInt().coerceIn(0, w - 1)
            val y = ((i / sw) / scale).toInt().coerceIn(0, h - 1)
            alpha[y * w + x] > 0.5f
        }
        val pixels = IntArray(sw * sh)
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                val i = y * sw + x
                if (!inside[i]) continue
                var edge = false
                loop@ for (dy in -2..2) for (dx in -2..2) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until sw || ny !in 0 until sh || !inside[ny * sw + nx]) { edge = true; break@loop }
                }
                pixels[i] = if (edge) Color.WHITE else Color.argb(40, 255, 255, 255)
            }
        }
        return Bitmap.createBitmap(pixels, sw, sh, Bitmap.Config.ARGB_8888)
    }

    /**
     * The photo's strongest edges (Sobel), white with alpha by strength. The scan
     * line lights these up as it passes, so it reads as the photo being analysed.
     */
    fun edges(photo: Bitmap): Bitmap {
        val scale = SIDE.toFloat() / max(photo.width, photo.height)
        val sw = (photo.width * scale).roundToInt().coerceAtLeast(3)
        val sh = (photo.height * scale).roundToInt().coerceAtLeast(3)
        val small = Bitmap.createScaledBitmap(photo, sw, sh, true)
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)
        if (small !== photo) small.recycle()
        val lum = FloatArray(sw * sh) { i ->
            val p = px[i]
            (0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)) / 255f
        }
        val mag = FloatArray(sw * sh)
        var maxMag = 1e-3f
        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                fun l(dx: Int, dy: Int) = lum[(y + dy) * sw + x + dx]
                val gx = l(1, -1) + 2 * l(1, 0) + l(1, 1) - l(-1, -1) - 2 * l(-1, 0) - l(-1, 1)
                val gy = l(-1, 1) + 2 * l(0, 1) + l(1, 1) - l(-1, -1) - 2 * l(0, -1) - l(1, -1)
                val m = sqrt(gx * gx + gy * gy)
                mag[y * sw + x] = m
                if (m > maxMag) maxMag = m
            }
        }
        val out = IntArray(sw * sh) { i ->
            val t = ((mag[i] / maxMag - 0.12f) / 0.4f).coerceIn(0f, 1f)
            Color.argb((t * 255).roundToInt(), 255, 255, 255)
        }
        return Bitmap.createBitmap(out, sw, sh, Bitmap.Config.ARGB_8888)
    }
}
