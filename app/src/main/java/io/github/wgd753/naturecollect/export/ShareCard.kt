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
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import io.github.wgd753.naturecollect.R
import io.github.wgd753.naturecollect.data.Category
import io.github.wgd753.naturecollect.data.MediaFiles
import io.github.wgd753.naturecollect.data.Settings
import io.github.wgd753.naturecollect.data.db.CaptureEntity
import io.github.wgd753.naturecollect.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * A single find drawn as a mounted specimen: the sticker taped to white card
 * stock on the celadon page, a herbarium label box and a rust date stamp.
 */
object ShareCard {

    private const val WIDTH = 1080
    private const val HEIGHT = 1440

    /** Renders the card, writes it to the cache and opens the share sheet. */
    suspend fun share(context: Context, media: MediaFiles, capture: CaptureEntity, number: Int?, encounter: Int? = null) {
        val uri = withContext(Dispatchers.IO) {
            val bitmap = render(media, capture, number, SpecimenCanvas.monoTypeface(context), encounter, Settings.showStamp(context))
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

    fun render(media: MediaFiles, capture: CaptureEntity, number: Int?, mono: Typeface? = null, encounter: Int? = null, stamp: Boolean = true): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(SpecimenCanvas.CELADON)
        SpecimenCanvas.grain(canvas, WIDTH, HEIGHT)
        val monoFace = mono ?: Typeface.MONOSPACE
        val category = Category.fromKey(capture.category)

        // The sheet.
        val sheet = RectF(64f, 64f, WIDTH - 64f, HEIGHT - 64f)
        SpecimenCanvas.sheet(canvas, sheet)

        // Sticker, large in the upper part.
        val boxTop = sheet.top + 90f
        val boxBottom = sheet.top + 830f
        val boxSize = min(sheet.width() - 160f, boxBottom - boxTop)
        loadSticker(media.file(capture.stickerFile), boxSize.toInt())?.let { sticker ->
            val scale = min(boxSize / sticker.width, boxSize / sticker.height) * 0.86f
            val w = sticker.width * scale
            val h = sticker.height * scale
            val cx = WIDTH / 2f - 30f
            val cy = (boxTop + boxBottom) / 2f
            val dst = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
            SpecimenCanvas.sticker(canvas, sticker, dst, rotation = -4f)
            sticker.recycle()
        }

        // Date stamp, top right.
        if (stamp) SpecimenCanvas.dateStamp(canvas, sheet.right - 120f, sheet.top + 120f, 78f, capture.createdAt, monoFace)

        // Herbarium label, bottom right.
        val rows = buildList {
            capture.placeName?.takeIf { it.isNotBlank() }?.let { add(tr("地点", "Place") to it) }
            add(tr("时间", "Time") to SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.ENGLISH).format(Date(capture.createdAt)))
            SpecimenCanvas.coordinates(capture.latitude, capture.longitude)?.let { add(tr("坐标", "Coords") to it) }
            encounter?.let { add(tr("遇见", "Seen") to tr("第 $it 次", "#$it")) }
        }
        SpecimenCanvas.label(
            canvas,
            right = sheet.right - 48f,
            bottom = sheet.bottom - 48f,
            width = 600f,
            head = SpecimenCanvas.labelHead(number, category),
            name = capture.displayName,
            sci = capture.nameSci?.takeIf { it.isNotBlank() },
            rows = rows,
            mono = monoFace,
            scale = 2.6f,
        )

        // Wordmark, bottom left.
        val markPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SpecimenCanvas.SOFT
            textSize = 26f
            typeface = monoFace
            letterSpacing = 0.08f
        }
        canvas.drawText("拾野 · SHIYE", sheet.left + 48f, sheet.bottom - 52f, markPaint)
        return bitmap
    }

    private fun loadSticker(file: File, target: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

/**
 * Plain android.graphics versions of the specimen look, for images rendered
 * off screen (share card, poster, board export): card stock, tape strips,
 * hairlines, the herbarium label and the rust stamp.
 */
internal object SpecimenCanvas {

    val CELADON = 0xFFE2EBE3.toInt()
    val CARD = 0xFFFBFBF7.toInt()
    val PINE = 0xFF22392F.toInt()
    val SOFT = 0xFF6F8277.toInt()
    val RUST = 0xFFB4532E.toInt()
    val DIVIDER = 0xFFDCE2DB.toInt()
    val LABEL_BORDER = 0xFF9FB0A4.toInt()
    val TAPE = 0xD9E2DBC4.toInt()

    @Volatile private var cachedMono: Typeface? = null

    /** DM Mono, or null if the font can't be loaded. */
    fun monoTypeface(context: Context): Typeface? =
        cachedMono ?: runCatching { ResourcesCompat.getFont(context, R.font.dm_mono) }
            .getOrNull()?.also { cachedMono = it }

    /** White card stock with a faint shadow, square corners. */
    fun sheet(canvas: Canvas, rect: RectF) {
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x241E3228
            maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRect(rect.left, rect.top + 3f, rect.right, rect.bottom + 3f, shadow)
        canvas.drawRect(rect, Paint().apply { color = CARD })
    }

    /** A linen tape strip centred on ([cx], [cy]). */
    fun tape(canvas: Canvas, cx: Float, cy: Float, length: Float, width: Float, angle: Float) {
        canvas.save()
        canvas.rotate(angle, cx, cy)
        canvas.drawRect(cx - length / 2, cy - width / 2, cx + length / 2, cy + width / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TAPE })
        canvas.restore()
    }

    /** Two tape strips over the top corners of [rect]. */
    fun tapedCorners(canvas: Canvas, rect: RectF, seed: Int, length: Float, width: Float) {
        val rnd = Random(seed)
        tape(canvas, rect.left + length * 0.25f, rect.top + width * 0.2f, length, width, -38f + (rnd.nextFloat() - 0.5f) * 10f)
        tape(canvas, rect.right - length * 0.25f, rect.top + width * 0.2f, length, width, 38f + (rnd.nextFloat() - 0.5f) * 10f)
    }

    /** Thin pine rule. */
    fun hairline(canvas: Canvas, x1: Float, y: Float, x2: Float, color: Int = PINE, alpha: Int = 0x59, stroke: Float = 1.5f) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.alpha = alpha
            strokeWidth = stroke
        }
        canvas.drawLine(x1, y, x2, y, paint)
    }

    /** A sticker with a soft contact shadow, optionally tilted about its centre. */
    fun sticker(canvas: Canvas, sticker: Bitmap, dst: RectF, rotation: Float = 0f) {
        val scale = dst.width() / sticker.width
        canvas.save()
        if (rotation != 0f) canvas.rotate(rotation, dst.centerX(), dst.centerY())
        runCatching {
            val blur = Paint().apply { maskFilter = BlurMaskFilter(max(1f, 14f / scale), BlurMaskFilter.Blur.NORMAL) }
            val offset = IntArray(2)
            val shadow = sticker.extractAlpha(blur, offset)
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                color = 0xFF1B2A20.toInt()
                alpha = 0x30
            }
            val sx = dst.left + offset[0] * scale
            val sy = dst.top + offset[1] * scale + 8f
            canvas.drawBitmap(shadow, null, RectF(sx, sy, sx + shadow.width * scale, sy + shadow.height * scale), shadowPaint)
            shadow.recycle()
        }
        canvas.drawBitmap(sticker, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    /** Round rust stamp, tilted -14°, with up to three centred lines. */
    fun stamp(canvas: Canvas, cx: Float, cy: Float, r: Float, lines: List<String>, mono: Typeface?) {
        canvas.save()
        canvas.rotate(-14f, cx, cy)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RUST
            alpha = 0xD0
            style = Paint.Style.STROKE
            strokeWidth = r * 0.045f
        }
        canvas.drawCircle(cx, cy, r - ring.strokeWidth, ring)
        ring.strokeWidth = r * 0.02f
        canvas.drawCircle(cx, cy, r * 0.86f, ring)
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RUST
            alpha = 0xD0
            textAlign = Paint.Align.CENTER
        }
        val step = r * 0.34f
        val firstBaseline = cy - step * (lines.size - 1) / 2f + r * 0.1f
        lines.forEachIndexed { i, line ->
            val cjk = line.any { it.code > 0x2E80 }
            text.typeface = if (cjk) Typeface.DEFAULT_BOLD else (mono ?: Typeface.MONOSPACE)
            text.textSize = if (cjk) r * 0.30f else r * 0.26f
            canvas.drawText(line, cx, firstBaseline + i * step, text)
        }
        canvas.restore()
    }

    /** "拾野 / 2026 / 10.11". */
    fun dateStamp(canvas: Canvas, cx: Float, cy: Float, r: Float, millis: Long, mono: Typeface?) {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val day = "%02d.%02d".format(Locale.ENGLISH, cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        stamp(canvas, cx, cy, r, listOf("拾野", cal.get(Calendar.YEAR).toString(), day), mono)
    }

    /** "No.004 · PLANT": built-in categories by key, custom ones by name. */
    fun labelHead(number: Int?, category: Category): String {
        val cat = if (Category.defaults.any { it.key == category.key }) category.key else category.display.uppercase(Locale.ENGLISH)
        return listOfNotNull(number?.let { "No.%03d".format(Locale.ENGLISH, it) }, cat).joinToString(" · ")
    }

    /** "31.2146N 121.5470E", or null without a fix. */
    fun coordinates(lat: Double?, lng: Double?): String? {
        if (lat == null || lng == null) return null
        return "%.4f%s %.4f%s".format(Locale.ENGLISH, abs(lat), if (lat >= 0) "N" else "S", abs(lng), if (lng >= 0) "E" else "W")
    }

    /**
     * The herbarium label box anchored at its bottom-right corner: mono head,
     * spaced name, mono scientific name, a dashed rule and label/value rows.
     * [scale] multiplies the mockup's dp sizes into pixels.
     */
    fun label(
        canvas: Canvas,
        right: Float,
        bottom: Float,
        width: Float,
        head: String,
        name: String,
        sci: String?,
        rows: List<Pair<String, String>>,
        mono: Typeface?,
        scale: Float,
    ) {
        val pad = 9f * scale
        val inner = width - pad * 2
        val headPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SOFT
            textSize = 9.5f * scale
            typeface = mono ?: Typeface.MONOSPACE
        }
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PINE
            textSize = 20f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.12f
        }
        val keyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SOFT
            textSize = 9.5f * scale
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PINE
            textSize = 9.5f * scale
            typeface = mono ?: Typeface.MONOSPACE
        }
        val smallLine = 14f * scale
        val nameLine = 26f * scale
        val rowLine = 13.5f * scale
        val height = pad + smallLine + nameLine + (if (sci != null) smallLine else 0f) + 6f * scale + rows.size * rowLine + pad
        val box = RectF(right - width, bottom - height, right, bottom)
        canvas.drawRect(box, Paint().apply { color = 0xFFFFFFFF.toInt() })
        canvas.drawRect(box, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LABEL_BORDER
            style = Paint.Style.STROKE
            strokeWidth = scale
        })

        var y = box.top + pad + 10f * scale
        canvas.drawText(ellipsize(head, headPaint, inner), box.left + pad, y, headPaint)
        y += nameLine - 2f * scale
        canvas.drawText(ellipsize(name, namePaint, inner), box.left + pad, y, namePaint)
        if (sci != null) {
            y += smallLine
            canvas.drawText(ellipsize(sci, headPaint, inner), box.left + pad, y, headPaint)
        }
        y += 6f * scale
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFC4CFC6.toInt()
            strokeWidth = scale
            pathEffect = DashPathEffect(floatArrayOf(3f * scale, 2f * scale), 0f)
        }
        canvas.drawLine(box.left + pad, y, box.right - pad, y, dash)
        val keyWidth = (rows.maxOfOrNull { keyPaint.measureText(it.first) } ?: 0f) + 8f * scale
        rows.forEach { (k, v) ->
            y += rowLine
            canvas.drawText(k, box.left + pad, y, keyPaint)
            canvas.drawText(ellipsize(v, valuePaint, inner - keyWidth), box.left + pad + keyWidth, y, valuePaint)
        }
    }

    /** Very faint paper grain: a tiled noise texture at a few percent alpha. */
    fun grain(canvas: Canvas, width: Int, height: Int) {
        val size = 192
        val random = Random(7)
        val pixels = IntArray(size * size) {
            val alpha = if (random.nextFloat() < 0.2f) random.nextInt(6, 22) else 0
            (alpha shl 24) or 0x22392F
        }
        val tile = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        tile.recycle()
    }

    fun ellipsize(text: String, paint: TextPaint, width: Float): String =
        TextUtils.ellipsize(text, paint, max(0f, width), TextUtils.TruncateAt.END).toString()

}
