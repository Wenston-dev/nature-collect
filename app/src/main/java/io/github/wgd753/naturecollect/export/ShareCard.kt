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
import io.github.wgd753.naturecollect.ui.washFor
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import android.graphics.Path
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

    private val PAPER = 0xFFF8F9F4.toInt()
    private val FOREST = 0xFF2F4639.toInt()
    private val FOREST_DEEP = 0xFF24372D.toInt()
    private val MUTED = 0xFF7F8F84.toInt()

    /** Renders the card, writes it to the cache and opens the share sheet. */
    suspend fun share(context: Context, media: MediaFiles, capture: CaptureEntity, number: Int?) {
        val uri = withContext(Dispatchers.IO) {
            val mark = runCatching { android.graphics.BitmapFactory.decodeResource(context.resources, io.github.wgd753.naturecollect.R.drawable.art_logo) }.getOrNull()
            val bitmap = render(media, capture, number, mark, CanvasWash.titleTypeface(context))
            mark?.recycle()
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

    fun render(media: MediaFiles, capture: CaptureEntity, number: Int?, mark: Bitmap? = null, titleFace: Typeface? = null): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PAPER)
        CanvasWash.grain(canvas, WIDTH, HEIGHT)
        val category = Category.fromKey(capture.category)
        val wash = washFor(category).toArgb()

        // Sticker, large and centred in the upper part, with a faint contact shadow.
        val boxTop = 120f
        val boxBottom = 880f
        val boxSize = min(WIDTH - MARGIN * 2, boxBottom - boxTop)
        loadSticker(media.file(capture.stickerFile), boxSize.toInt())?.let { sticker ->
            // Leave room around the sticker for the wash to show.
            val scale = min(boxSize / sticker.width, boxSize / sticker.height) * 0.82f
            val w = sticker.width * scale
            val h = sticker.height * scale
            val cx = WIDTH / 2f
            val cy = (boxTop + boxBottom) / 2f
            val dst = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
            // A watercolour blot in the category's wash, a little larger than the sticker.
            CanvasWash.blot(canvas, cx, cy, boxSize * 0.5f, boxSize * 0.46f, wash, capture.id.hashCode(), strength = 1.3f)
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

        // Category as a small wash pill above the name.
        val categoryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = FOREST
            textSize = 28f
            letterSpacing = 0.12f
        }
        val categoryText = ellipsize(if (Lang.english) category.display.uppercase(Locale.ENGLISH) else category.display, categoryPaint, textWidth)
        val pillWidth = categoryPaint.measureText(categoryText) + 44f
        CanvasWash.blot(canvas, MARGIN + pillWidth / 2 - 6f, 998f, pillWidth / 2, 28f, wash, category.key.hashCode(), strength = 1.6f)
        canvas.drawText(categoryText, MARGIN + 16f, 1008f, categoryPaint)

        // Name.
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = FOREST
            textSize = 92f
            typeface = titleFace ?: Typeface.DEFAULT_BOLD
            letterSpacing = if (titleFace != null) 0.06f else 0f
        }
        canvas.drawText(ellipsize(capture.displayName, namePaint, textWidth), MARGIN, 1112f, namePaint)

        // Scientific / English name.
        var y = 1112f
        capture.nameSci?.takeIf { it.isNotBlank() }?.let { sci ->
            val sciPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = MUTED
                textSize = 30f
                letterSpacing = 0.18f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            y += 62f
            canvas.drawText(ellipsize(sci.uppercase(Locale.ENGLISH), sciPaint, textWidth), MARGIN, y, sciPaint)
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
            typeface = titleFace ?: Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("拾野", WIDTH - MARGIN, HEIGHT - 84f, markPaint)
        if (mark != null) {
            val h = 56f
            val w = h * mark.width / mark.height
            val right = WIDTH - MARGIN - markPaint.measureText("拾野") - 14f
            val top = HEIGHT - 84f - h + 10f
            canvas.drawBitmap(mark, null, android.graphics.RectF(right - w, top, right, top + h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        return bitmap
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

/**
 * Plain android.graphics versions of the app's watercolour look, for images
 * rendered off screen (share card, poster).
 */
internal object CanvasWash {

    @Volatile private var cachedTitle: Typeface? = null

    /** ZCOOL XiaoWei for titles, or null if the font can't be loaded. */
    fun titleTypeface(context: Context): Typeface? =
        cachedTitle ?: runCatching { ResourcesCompat.getFont(context, io.github.wgd753.naturecollect.R.font.zcool_xiaowei) }
            .getOrNull()?.also { cachedTitle = it }

    /**
     * An irregular watercolour blot centred on ([cx], [cy]): a broad pale pool,
     * a denser offset pool, a little granulation and a slightly darker drying edge.
     */
    fun blot(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int, seed: Int, strength: Float = 1f) {
        val rnd = Random(seed)
        val main = blobPath(cx, cy, rx, ry, rnd)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        fun alpha(a: Float) = (a * strength * 255).toInt().coerceIn(0, 255)

        paint.alpha = alpha(0.24f)
        canvas.drawPath(main, paint)
        val second = blobPath(
            cx + (rnd.nextFloat() - 0.5f) * rx * 0.18f,
            cy + (rnd.nextFloat() - 0.5f) * ry * 0.18f,
            rx * (0.9f + rnd.nextFloat() * 0.08f),
            ry * (0.86f + rnd.nextFloat() * 0.1f),
            rnd,
        )
        paint.alpha = alpha(0.10f)
        canvas.drawPath(second, paint)
        val inner = blobPath(
            cx + (rnd.nextFloat() - 0.5f) * rx * 0.25f,
            cy + (rnd.nextFloat() - 0.5f) * ry * 0.25f,
            rx * (0.62f + rnd.nextFloat() * 0.12f),
            ry * (0.6f + rnd.nextFloat() * 0.12f),
            rnd,
        )
        paint.alpha = alpha(0.15f)
        canvas.drawPath(inner, paint)

        // Pigment granulation: sparse darker specks inside the blot.
        canvas.save()
        canvas.clipPath(main)
        paint.alpha = alpha(0.22f)
        val specks = (rx * ry / 220f).toInt().coerceIn(20, 900)
        val speck = (min(rx, ry) * 0.012f).coerceIn(0.8f, 2.4f)
        repeat(specks) {
            canvas.drawCircle(cx + (rnd.nextFloat() * 2 - 1) * rx, cy + (rnd.nextFloat() * 2 - 1) * ry, speck, paint)
        }
        canvas.restore()

        // The drying edge.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (min(rx, ry) * 0.02f).coerceIn(1.5f, 4f)
        paint.alpha = alpha(0.32f)
        canvas.drawPath(main, paint)
    }

    private fun blobPath(cx: Float, cy: Float, rx: Float, ry: Float, rnd: Random): Path {
        val n = 64
        val k1 = rnd.nextFloat() * 6.28f
        val k2 = rnd.nextFloat() * 6.28f
        val k3 = rnd.nextFloat() * 6.28f
        val a1 = 0.07f + rnd.nextFloat() * 0.06f
        val a2 = 0.04f + rnd.nextFloat() * 0.04f
        return Path().apply {
            for (i in 0 until n) {
                val t = i * 2 * Math.PI.toFloat() / n
                val wobble = 1f + a1 * kotlin.math.sin(2 * t + k1) + a2 * kotlin.math.sin(3 * t + k2) + 0.025f * kotlin.math.sin(7 * t + k3)
                val x = cx + kotlin.math.cos(t) * rx * wobble
                val y = cy + kotlin.math.sin(t) * ry * wobble
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }

    /** Very faint paper grain: a tiled noise texture at a few percent alpha. */
    fun grain(canvas: Canvas, width: Int, height: Int) {
        val size = 192
        val random = Random(7)
        val pixels = IntArray(size * size) {
            val dark = random.nextBoolean()
            val alpha = random.nextInt(0, 14)
            if (dark) (alpha shl 24) or 0x2F4639 else (alpha shl 24) or 0xFFFFFF
        }
        val tile = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        tile.recycle()
    }
}
