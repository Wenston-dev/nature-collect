package io.github.wgd753.naturecollect.ui.collection

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import io.github.wgd753.naturecollect.data.db.CaptureEntity
import io.github.wgd753.naturecollect.ui.rememberContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val cache = LruCache<String, Int>(256)

/**
 * The sticker's main colour, for tinting its card. Falls back to [fallback]
 * (the category colour) while loading or when the sticker is mostly grey.
 */
@Composable
fun rememberStickerTint(capture: CaptureEntity, fallback: Color): Color {
    val container = rememberContainer()
    val file = container.media.file(capture.stickerFile)
    val tint by produceState<Color?>(cache.get(file.path)?.let { Color(it) }, file.path) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { dominantColor(file) }?.also { cache.put(file.path, it) }?.let { Color(it) }
        }
    }
    return tint ?: fallback
}

/**
 * Hue histogram over the sticker's opaque pixels, weighted by saturation so the
 * white die-cut border and grey background bits don't win. Returns the average
 * colour of the strongest hue bin, or null if the sticker is essentially grey.
 */
private fun dominantColor(file: File): Int? {
    if (!file.exists()) return null
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, opts)
    var sample = 1
    while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= 64) sample *= 2
    val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val w = bitmap.width
    val h = bitmap.height
    val px = IntArray(w * h)
    bitmap.getPixels(px, 0, w, 0, 0, w, h)
    bitmap.recycle()

    val bins = 24
    val weight = FloatArray(bins)
    val sumR = FloatArray(bins)
    val sumG = FloatArray(bins)
    val sumB = FloatArray(bins)
    val hsv = FloatArray(3)
    var total = 0f
    for (p in px) {
        if ((p ushr 24) < 200) continue
        android.graphics.Color.colorToHSV(p, hsv)
        val s = hsv[1]
        val v = hsv[2]
        if (s < 0.15f || v < 0.15f) continue
        val wgt = s * s * (0.3f + v)
        val b = ((hsv[0] / 360f) * bins).toInt().coerceIn(0, bins - 1)
        weight[b] += wgt
        sumR[b] += ((p shr 16) and 0xFF) * wgt
        sumG[b] += ((p shr 8) and 0xFF) * wgt
        sumB[b] += (p and 0xFF) * wgt
        total += wgt
    }
    if (total <= 0f) return null
    // Neighbouring bins count too, so a hue that straddles a bin edge isn't split.
    val best = (0 until bins).maxBy { weight[it] + 0.5f * (weight[(it + 1) % bins] + weight[(it + bins - 1) % bins]) }
    if (weight[best] < total * 0.08f) return null
    val wb = weight[best]
    return android.graphics.Color.rgb((sumR[best] / wb).toInt(), (sumG[best] / wb).toInt(), (sumB[best] / wb).toInt())
}
