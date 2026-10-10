package io.github.wgd753.naturecollect.ml

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.roundToInt

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

        // White die-cut border: blur the silhouette and threshold low, which grows
        // it by about [border] with rounded corners and bridges tiny gaps, so the
        // outline stays smooth even where the mask edge is busy (leaves, fur).
        val outW = boxW + margin * 2
        val outH = boxH + margin * 2
        val grown = FloatArray(outW * outH)
        for (y in 0 until boxH) {
            for (x in 0 until boxW) {
                grown[(y + margin) * outW + x + margin] = alpha[(y + box[1]) * w + x + box[0]]
            }
        }
        val r = (border * 0.75f).roundToInt().coerceAtLeast(2)
        val blurred = MaskOps.boxBlur(MaskOps.boxBlur(grown, outW, outH, r), outW, outH, r)
        val outlinePixels = IntArray(outW * outH) { i ->
            val a = (MaskOps.smoothstep(0.04f, 0.16f, blurred[i]) * 255).roundToInt()
            (a shl 24) or 0xFFFFFF
        }
        val outline = Bitmap.createBitmap(outlinePixels, outW, outH, Bitmap.Config.ARGB_8888)

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(46, 40, 30, 20)
            maskFilter = BlurMaskFilter(shadow, BlurMaskFilter.Blur.NORMAL)
        }
        val outlineAlpha = outline.extractAlpha()
        canvas.drawBitmap(outlineAlpha, 0f, shadow * 0.6f, shadowPaint)
        canvas.drawBitmap(outline, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.drawBitmap(cutout, margin.toFloat(), margin.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        outlineAlpha.recycle()
        outline.recycle()
        cutout.recycle()
        return out
    }

    /** A whole photo as a sticker: white rounded frame and the same soft shadow. */
    fun framed(photo: Bitmap, maxSide: Int = 1024): Bitmap {
        val scale = minOf(1f, maxSide.toFloat() / max(photo.width, photo.height))
        val w = (photo.width * scale).roundToInt().coerceAtLeast(1)
        val h = (photo.height * scale).roundToInt().coerceAtLeast(1)
        val border = max(8f, max(w, h) * 0.03f)
        val shadow = border
        val margin = (border + shadow * 2).roundToInt()
        val out = Bitmap.createBitmap(w + margin * 2, h + margin * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val frame = android.graphics.RectF(margin - border, margin - border, margin + w + border, margin + h + border)
        val radius = border * 1.2f
        canvas.drawRoundRect(
            android.graphics.RectF(frame).apply { offset(0f, shadow * 0.6f) },
            radius,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(46, 40, 30, 20)
                maskFilter = BlurMaskFilter(shadow, BlurMaskFilter.Blur.NORMAL)
            },
        )
        canvas.drawRoundRect(frame, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawBitmap(
            Bitmap.createScaledBitmap(photo, w, h, true),
            margin.toFloat(),
            margin.toFloat(),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
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
