package io.github.wgd753.naturecollect.ml

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Turns an image plus an alpha mask into a die-cut sticker with a white border and soft shadow. */
object StickerMaker {
    fun make(image: Bitmap, alpha: FloatArray): Bitmap? {
        val w = image.width
        val h = image.height
        val box = MaskOps.bounds(alpha, w, h) ?: return null
        val boxW = box[2] - box[0]
        val boxH = box[3] - box[1]
        if (boxW < 8 || boxH < 8) return null

        val border = max(6f, max(boxW, boxH) * 0.028f)
        val shadow = border * 0.9f
        val margin = (border + shadow * 2).roundToInt()

        // Cut the object out of the photo.
        val pixels = IntArray(boxW * boxH)
        image.getPixels(pixels, 0, boxW, box[0], box[1], boxW, boxH)
        for (y in 0 until boxH) {
            for (x in 0 until boxW) {
                val i = y * boxW + x
                val a = (alpha[(y + box[1]) * w + x + box[0]] * 255).roundToInt().coerceIn(0, 255)
                pixels[i] = (pixels[i] and 0x00FFFFFF) or (a shl 24)
            }
        }
        val cutout = Bitmap.createBitmap(pixels, boxW, boxH, Bitmap.Config.ARGB_8888)
        val silhouette = cutout.extractAlpha()

        val out = Bitmap.createBitmap(boxW + margin * 2, boxH + margin * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val ox = margin.toFloat()
        val oy = margin.toFloat()

        // Grow the silhouette by stamping it around a circle: cheap dilation for the border.
        fun stamp(paint: Paint, radius: Float, dx: Float = 0f, dy: Float = 0f) {
            val steps = 36
            for (s in 0 until steps) {
                val t = 2 * PI * s / steps
                canvas.drawBitmap(silhouette, ox + dx + (cos(t) * radius).toFloat(), oy + dy + (sin(t) * radius).toFloat(), paint)
            }
            canvas.drawBitmap(silhouette, ox + dx, oy + dy, paint)
        }

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(46, 40, 30, 20)
            maskFilter = BlurMaskFilter(shadow, BlurMaskFilter.Blur.NORMAL)
        }
        stamp(shadowPaint, border, dy = shadow * 0.6f)

        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        }
        stamp(whitePaint, border)
        stamp(whitePaint, border * 0.5f)

        canvas.drawBitmap(cutout, ox, oy, Paint(Paint.FILTER_BITMAP_FLAG))
        silhouette.recycle()
        cutout.recycle()
        return out
    }

    /** The sticker composited onto white, for sending to the recogniser. */
    fun onWhite(sticker: Bitmap, maxSide: Int = 768): Bitmap {
        val scale = minOf(1f, maxSide.toFloat() / max(sticker.width, sticker.height))
        val w = (sticker.width * scale).roundToInt().coerceAtLeast(1)
        val h = (sticker.height * scale).roundToInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(Bitmap.createScaledBitmap(sticker, w, h, true), 0f, 0f, null)
        return out
    }
}
