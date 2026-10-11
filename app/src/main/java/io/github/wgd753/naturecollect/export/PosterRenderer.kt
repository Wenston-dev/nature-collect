package io.github.wgd753.naturecollect.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import io.github.wgd753.naturecollect.data.Category
import io.github.wgd753.naturecollect.data.MediaFiles
import io.github.wgd753.naturecollect.data.SpeciesGroup
import io.github.wgd753.naturecollect.ui.theme.spaced
import io.github.wgd753.naturecollect.ui.tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min

/**
 * Draws the collection as one tall poster: a spaced title, a stats strip and a
 * grid of specimen cards (taped sticker, name and catalogue number), ready to
 * share or print.
 */
class PosterRenderer(private val media: MediaFiles, private val mono: Typeface? = null) {

    fun render(groups: List<SpeciesGroup>, title: String): Bitmap {
        val width = 1080
        val margin = 56f
        val columns = if (groups.size > 60) 4 else 3
        val gap = 24f
        val cell = (width - margin * 2 - gap * (columns - 1)) / columns
        val labelHeight = if (columns == 4) 58f else 66f
        val rowHeight = cell + labelHeight + gap
        val monoFace = mono ?: Typeface.MONOSPACE

        // Category counts, laid out first so the header can grow with them.
        val chipPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f
            color = SpecimenCanvas.SOFT
        }
        val chipCount = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 26f
            color = SpecimenCanvas.PINE
            typeface = monoFace
        }
        val chips = mutableListOf<Chip>()
        var x = margin
        var y = 370f
        Category.entries.forEach { category ->
            val count = groups.count { it.category.key == category.key }
            if (count == 0) return@forEach
            val label = category.display + " "
            val w = chipPaint.measureText(label) + chipCount.measureText(count.toString()) + 36f
            if (x + w > width - margin) {
                x = margin
                y += 44f
            }
            chips += Chip(label, count.toString(), x, y)
            x += w
        }

        // Keep the bitmap within a sane memory budget on huge collections.
        val headerHeight = 440f + (y - 370f)
        val footerHeight = 140f
        val maxHeight = 16000f
        val maxRows = ((maxHeight - headerHeight - footerHeight) / rowHeight).toInt()
        val shown = groups.take(maxRows * columns)
        val rows = ceil(shown.size / columns.toFloat()).toInt()
        val height = (headerHeight + rows * rowHeight + footerHeight).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(SpecimenCanvas.CELADON)
        SpecimenCanvas.grain(canvas, width, height)

        // Header: spaced title and a date stamp.
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SpecimenCanvas.PINE
            textSize = 72f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.18f
        }
        canvas.drawText(SpecimenCanvas.ellipsize(spaced(title), titlePaint, width - margin * 2 - 200f), margin, 150f, titlePaint)
        SpecimenCanvas.dateStamp(canvas, width - margin - 76f, 118f, 70f, System.currentTimeMillis(), monoFace)

        // Stats strip between two hairlines.
        val total = groups.sumOf { it.captures.size }
        val species = groups.count { it.cover.nameZh != null }
        val stripTop = 210f
        val stripBottom = 310f
        SpecimenCanvas.hairline(canvas, margin, stripTop, width - margin)
        SpecimenCanvas.hairline(canvas, margin, stripBottom, width - margin)
        val stats = listOf(
            species.toString() to tr("种", "species"),
            total.toString() to tr("次遇见", "sightings"),
            day(System.currentTimeMillis()) to tr("导出", "exported"),
        )
        val colWidth = (width - margin * 2) / stats.size
        val numberPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SpecimenCanvas.PINE
            textSize = 44f
            typeface = monoFace
        }
        val unitPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SpecimenCanvas.SOFT
            textSize = 24f
            letterSpacing = 0.06f
        }
        stats.forEachIndexed { i, (value, unit) ->
            val x = margin + i * colWidth + if (i == 0) 0f else 24f
            if (i > 0) {
                val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = SpecimenCanvas.PINE
                    alpha = 0x33
                    strokeWidth = 1.5f
                }
                canvas.drawLine(margin + i * colWidth, stripTop + 16f, margin + i * colWidth, stripBottom - 16f, divider)
            }
            numberPaint.textSize = if (i == 2) 32f else 44f
            canvas.drawText(value, x, stripTop + 54f, numberPaint)
            canvas.drawText(unit, x, stripTop + 86f, unitPaint)
        }

        // Category counts as plain text.
        chips.forEach { chip ->
            canvas.drawText(chip.label, chip.x, chip.y, chipPaint)
            canvas.drawText(chip.count, chip.x + chipPaint.measureText(chip.label), chip.y, chipCount)
        }

        // Grid of specimen cards.
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SpecimenCanvas.PINE
            textSize = if (columns == 4) 24f else 30f
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SpecimenCanvas.SOFT
            textSize = if (columns == 4) 17f else 20f
            typeface = monoFace
            textAlign = Paint.Align.RIGHT
        }
        val top = headerHeight
        shown.forEachIndexed { index, group ->
            val col = index % columns
            val row = index / columns
            val left = margin + col * (cell + gap)
            val cardTop = top + row * rowHeight
            val card = RectF(left, cardTop, left + cell, cardTop + cell + labelHeight)
            SpecimenCanvas.sheet(canvas, card)

            loadSticker(group, (cell * 0.78f).toInt())?.let { sticker ->
                val box = cell * 0.74f
                val scale = min(box / sticker.width, box / sticker.height)
                val w = sticker.width * scale
                val h = sticker.height * scale
                val cx = left + cell / 2
                val cy = cardTop + cell / 2 + 8f
                SpecimenCanvas.sticker(canvas, sticker, RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), rotation = ((group.key.hashCode() % 7) - 3) * 1.5f)
                sticker.recycle()
            }
            SpecimenCanvas.tapedCorners(canvas, card, group.key.hashCode(), cell * 0.26f, cell * 0.09f)

            // Label row under a hairline: name left, "No.004 · ×5" right.
            val ruleY = cardTop + cell
            SpecimenCanvas.hairline(canvas, left + 10f, ruleY, left + cell - 10f, color = SpecimenCanvas.DIVIDER, alpha = 0xFF, stroke = 2f)
            val baseline = ruleY + labelHeight * 0.62f
            val meta = buildString {
                append("No.%03d".format(Locale.ENGLISH, index + 1))
                if (group.captures.size > 1) append(" · ×${group.captures.size}")
            }
            val metaWidth = metaPaint.measureText(meta)
            canvas.drawText(meta, left + cell - 14f, baseline, metaPaint)
            canvas.drawText(SpecimenCanvas.ellipsize(group.name, namePaint, cell - metaWidth - 40f), left + 14f, baseline, namePaint)
        }

        // Footer: a small rust "拾野 采集" stamp and one line.
        val footPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SpecimenCanvas.SOFT
            textSize = 26f
            typeface = monoFace
            letterSpacing = 0.1f
        }
        val more = groups.size - shown.size
        val foot = if (more > 0) tr("还有 $more 种没放下 · 拾野", "$more more not shown · Shiye") else tr("拾野 · 我的野外笔记", "Shiye · My field notes")
        canvas.drawText(foot, margin, height - 64f, footPaint)
        SpecimenCanvas.stamp(canvas, width - margin - 50f, height - 76f, 46f, listOf("拾野", "采集"), monoFace)
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

    private fun day(millis: Long) =
        SimpleDateFormat("yyyy.MM.dd", Locale.ENGLISH).format(Date(millis))

    private class Chip(val label: String, val count: String, val x: Float, val y: Float)
}
