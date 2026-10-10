package io.github.wgd753.naturecollect.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.text.TextPaint
import io.github.wgd753.naturecollect.data.Category
import io.github.wgd753.naturecollect.data.MediaFiles
import io.github.wgd753.naturecollect.data.SpeciesGroup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min

/**
 * Draws the collection as one tall poster: a title, stats per category and a
 * grid of stickers with their names, ready to share or print.
 */
class PosterRenderer(private val media: MediaFiles) {

    fun render(groups: List<SpeciesGroup>, title: String): Bitmap {
        val width = 1080
        val margin = 56f
        val columns = if (groups.size > 60) 4 else 3
        val gap = 20f
        val cell = (width - margin * 2 - gap * (columns - 1)) / columns
        val labelHeight = 86f
        val rowHeight = cell + labelHeight + gap

        // Keep the bitmap within a sane memory budget on huge collections.
        val headerHeight = 420f
        val footerHeight = 140f
        val maxHeight = 16000f
        val maxRows = ((maxHeight - headerHeight - footerHeight) / rowHeight).toInt()
        val shown = groups.take(maxRows * columns)
        val rows = ceil(shown.size / columns.toFloat()).toInt()
        val height = (headerHeight + rows * rowHeight + footerHeight).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PAPER)
        drawDots(canvas, width, height)

        // Header.
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            textSize = 92f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
        }
        canvas.drawText(title, margin, 150f, titlePaint)
        val total = groups.sumOf { it.captures.size }
        val species = groups.count { it.cover.nameZh != null }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED
            textSize = 36f
        }
        canvas.drawText("$species 种 · $total 次遇见 · ${day(System.currentTimeMillis())}", margin, 215f, subPaint)

        // Category chips.
        val chipPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            color = Color.WHITE
        }
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        var x = margin
        var y = 270f
        Category.entries.forEach { category ->
            val count = groups.count { it.category.key == category.key }
            if (count == 0) return@forEach
            val text = "${category.emoji} ${category.label} $count"
            val w = chipPaint.measureText(text) + 48f
            if (x + w > width - margin) {
                x = margin
                y += 76f
            }
            bg.color = tint(category)
            canvas.drawRoundRect(RectF(x, y, x + w, y + 60f), 30f, 30f, bg)
            canvas.drawText(text, x + 24f, y + 41f, chipPaint)
            x += w + 16f
        }

        // Grid.
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            textSize = if (columns == 4) 28f else 34f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val countPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD }
        val top = headerHeight
        shown.forEachIndexed { index, group ->
            val col = index % columns
            val row = index / columns
            val left = margin + col * (cell + gap)
            val cellTop = top + row * rowHeight
            canvas.drawRoundRect(RectF(left, cellTop, left + cell, cellTop + cell + labelHeight - 8f), 36f, 36f, card)

            loadSticker(group, (cell * 0.86f).toInt())?.let { sticker ->
                val box = cell * 0.86f
                val scale = min(box / sticker.width, box / sticker.height)
                val w = sticker.width * scale
                val h = sticker.height * scale
                val cx = left + cell / 2
                val cy = cellTop + cell / 2 + 6f
                canvas.save()
                canvas.rotate(((group.key.hashCode() % 7) - 3) * 1.5f, cx, cy)
                canvas.drawBitmap(sticker, null, RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.restore()
                sticker.recycle()
            }

            val name = TextUtils.ellipsize(group.name, namePaint, cell - 32f, TextUtils.TruncateAt.END).toString()
            canvas.drawText(name, left + cell / 2, cellTop + cell + 34f, namePaint)
            bg.color = tint(group.category)
            canvas.drawCircle(left + cell / 2, cellTop + cell + 60f, 6f, bg)

            if (group.captures.size > 1) {
                val r = 26f
                canvas.drawCircle(left + cell - r - 10f, cellTop + r + 10f, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK })
                canvas.drawText(group.captures.size.toString(), left + cell - r - 10f, cellTop + r + 19f, countPaint)
            }
        }

        // Footer.
        val footPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        val more = groups.size - shown.size
        val foot = if (more > 0) "还有 $more 种没放下 · 自然收集" else "自然收集 · 我的野外笔记"
        canvas.drawText(foot, width / 2f, height - 64f, footPaint)
        return bitmap
    }

    private fun loadSticker(group: SpeciesGroup, target: Int): Bitmap? = runCatching {
        val file = media.file(group.cover.stickerFile)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    private fun drawDots(canvas: Canvas, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x14_1F2FC8 }
        var y = 24f
        while (y < height) {
            var x = 24f
            while (x < width) {
                canvas.drawCircle(x, y, 2.5f, paint)
                x += 48f
            }
            y += 48f
        }
    }

    private fun day(millis: Long) = SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(millis))

    private fun tint(category: Category): Int = category.color.toInt()

    private companion object {
        val PAPER = 0xFFEFEBE3.toInt()
        val CARD = 0xFFF8F6F1.toInt()
        val INK = 0xFF1F2FC8.toInt()
        val MUTED = 0xFF8A877F.toInt()
    }
}
