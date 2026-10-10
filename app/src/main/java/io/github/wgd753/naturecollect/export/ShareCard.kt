package io.github.wgd753.naturecollect.export

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import io.github.wgd753.naturecollect.data.Category
import io.github.wgd753.naturecollect.data.MediaFiles
import io.github.wgd753.naturecollect.data.db.CaptureEntity
import io.github.wgd753.naturecollect.ui.Lang
import io.github.wgd753.naturecollect.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.random.Random

/**
 * A single find drawn as a field-guide specimen label: the sticker on paper,
 * a hairline, the names and one line of provenance.
 */
object ShareCard {

    private const val WIDTH = 1080
    private const val HEIGHT = 1440
    private const val MARGIN = 96f

    private val PAPER = 0xFFEEF3EC.toInt()
    private val FOREST = 0xFF1F5B45.toInt()
    private val FOREST_DEEP = 0xFF143D2E.toInt()
    private val MUTED = 0xFF6B7A71.toInt()

    /** Renders the card, writes it to the cache and opens the share sheet. */
    suspend fun share(context: Context, media: MediaFiles, capture: CaptureEntity, number: Int?) {
        val uri = withContext(Dispatchers.IO) {
            val bitmap = render(media, capture, number)
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "shiye-${capture.id.take(12)}.png")
            try {
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bitmap.recycle()
            }
            FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(capture.displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, tr("分享", "Share"))
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun render(media: MediaFiles, capture: CaptureEntity, number: Int?): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PAPER)
        drawGrain(canvas)

        // Sticker, large and centred in the upper part, with a faint contact shadow.
        val boxTop = 120f
        val boxBottom = 880f
        val boxSize = min(WIDTH - MARGIN * 2, boxBottom - boxTop)
        loadSticker(media.file(capture.stickerFile), boxSize.toInt())?.let { sticker ->
            val scale = min(boxSize / sticker.width, boxSize / sticker.height)
            val w = sticker.width * scale
            val h = sticker.height * scale
            val cx = WIDTH / 2f
            val cy = (boxTop + boxBottom) / 2f
            val dst = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
            runCatching {
                val blur = Paint().apply { maskFilter = BlurMaskFilter(18f / scale, BlurMaskFilter.Blur.NORMAL) }
                val offset = IntArray(2)
                val shadow = sticker.extractAlpha(blur, offset)
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    color = FOREST_DEEP
                    alpha = 0x24
                }
                val sx = dst.left + offset[0] * scale
                val sy = dst.top + offset[1] * scale + 10f
                canvas.drawBitmap(shadow, null, RectF(sx, sy, sx + shadow.width * scale, sy + shadow.height * scale), shadowPaint)
                shadow.recycle()
            }
            canvas.drawBitmap(sticker, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            sticker.recycle()
        }

        // Hairline rule.
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED
            alpha = 0x66
            strokeWidth = 2f
        }
        canvas.drawLine(MARGIN, 940f, WIDTH - MARGIN, 940f, rule)

        val textWidth = WIDTH - MARGIN * 2

        // Category, letter-spaced, above the name.
        val category = Category.fromKey(capture.category)
        val categoryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED
            textSize = 26f
            letterSpacing = 0.28f
            typeface = Typeface.DEFAULT_BOLD
        }
        val categoryText = if (Lang.english) category.display.uppercase(Locale.ENGLISH) else category.display
        canvas.drawText(ellipsize(categoryText, categoryPaint, textWidth), MARGIN, 1008f, categoryPaint)

        // Name.
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = FOREST_DEEP
            textSize = 88f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(ellipsize(capture.displayName, namePaint, textWidth), MARGIN, 1112f, namePaint)

        // Scientific / English name.
        var y = 1112f
        capture.nameSci?.takeIf { it.isNotBlank() }?.let { sci ->
            val sciPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = MUTED
                textSize = 42f
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            }
            y += 68f
            canvas.drawText(ellipsize(sci, sciPaint, textWidth), MARGIN, y, sciPaint)
        }

        // Provenance line.
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED
            textSize = 30f
            letterSpacing = 0.04f
        }
        val meta = listOfNotNull(
            number?.let { "No.%03d".format(Locale.ENGLISH, it) },
            day(capture.createdAt),
            capture.placeName?.takeIf { it.isNotBlank() },
        ).joinToString("  ·  ")
        y += 74f
        canvas.drawText(ellipsize(meta, metaPaint, textWidth), MARGIN, y, metaPaint)

        // Wordmark.
        val markPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = FOREST
            textSize = 40f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("拾野", WIDTH - MARGIN, HEIGHT - 84f, markPaint)
        return bitmap
    }

    /** Very faint paper grain: a tiled noise texture at a few percent alpha. */
    private fun drawGrain(canvas: Canvas) {
        val size = 192
        val random = Random(7)
        val pixels = IntArray(size * size) {
            val dark = random.nextBoolean()
            val alpha = random.nextInt(0, 14)
            if (dark) (alpha shl 24) or 0x143D2E else (alpha shl 24) or 0xFFFFFF
        }
        val tile = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        tile.recycle()
    }

    private fun loadSticker(file: File, target: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    private fun ellipsize(text: String, paint: TextPaint, width: Float): String =
        TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString()

    private fun day(millis: Long): String =
        if (Lang.english) SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(Date(millis))
        else SimpleDateFormat("yyyy.MM.dd", Locale.CHINA).format(Date(millis))
}
