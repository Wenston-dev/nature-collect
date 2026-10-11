package io.github.wgd753.naturecollect.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.wgd753.naturecollect.ui.theme.Card
import io.github.wgd753.naturecollect.ui.theme.Pine
import io.github.wgd753.naturecollect.ui.theme.Rust
import io.github.wgd753.naturecollect.ui.theme.Tape
import java.util.Calendar
import kotlin.random.Random

/** White card stock with a faint shadow and square corners. */
fun Modifier.specimenCard(elevation: Dp = 1.dp): Modifier =
    shadow(elevation, RectangleShape, ambientColor = Color(0x1F1E3228), spotColor = Color(0x1F1E3228))
        .background(Card, RectangleShape)

/**
 * Two short linen tape strips over the top corners, drawn above the content.
 * [seed] gives each card a slightly different tilt.
 */
fun Modifier.tapedCorners(seed: Int = 0, length: Dp = 26.dp, width: Dp = 9.dp): Modifier = drawWithContent {
    drawContent()
    val rnd = Random(seed)
    val l = length.toPx()
    val w = width.toPx()
    listOf(Offset(l * 0.25f, w * 0.2f) to -38f, Offset(size.width - l * 0.25f, w * 0.2f) to 38f).forEach { (c, base) ->
        rotate(base + (rnd.nextFloat() - 0.5f) * 10f, pivot = c) {
            drawRect(Tape, topLeft = Offset(c.x - l / 2, c.y - w / 2), size = Size(l, w))
        }
    }
}

/** A single tape strip, for holding stickers on the board. */
@Composable
fun TapeStrip(modifier: Modifier = Modifier, angle: Float = -8f) {
    Box(modifier.rotate(angle).size(30.dp, 10.dp).background(Tape))
}

/** Thin pine rule. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Pine.copy(alpha = 0.35f)) {
    Box(modifier.fillMaxWidth().height(0.5.dp).background(color))
}

/** Short rust bar under the active tab or chip. */
fun Modifier.activeUnderline(active: Boolean, width: Dp = 14.dp): Modifier = drawBehind {
    if (!active) return@drawBehind
    val w = width.toPx()
    val h = 2.dp.toPx()
    drawRect(Rust, topLeft = Offset((size.width - w) / 2, size.height - h), size = Size(w, h))
}

/**
 * Round rust date stamp ("拾野 / 2026 / 10.11"), tilted like it was pressed by
 * hand. Mono figures, drawn with the platform canvas for exact centring.
 */
@Composable
fun DateStamp(millis: Long, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val year = cal.get(Calendar.YEAR).toString()
    val day = "%02d.%02d".format(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    Canvas(modifier.size(size).rotate(-14f)) {
        val color = Rust.copy(alpha = 0.8f)
        val r = this.size.minDimension / 2
        drawCircle(color, r - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        drawCircle(color, r - 4.dp.toPx(), style = Stroke(0.7.dp.toPx()))
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val cx = center.x
        drawContext.canvas.nativeCanvas.apply {
            paint.textSize = r * 0.30f
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            drawText("拾野", cx, center.y - r * 0.22f, paint)
            paint.typeface = android.graphics.Typeface.MONOSPACE
            paint.textSize = r * 0.26f
            drawText(year, cx, center.y + r * 0.12f, paint)
            drawText(day, cx, center.y + r * 0.44f, paint)
        }
    }
}

private val grainBitmap: android.graphics.Bitmap by lazy {
    val size = 96
    val rnd = Random(11)
    val px = IntArray(size * size) {
        val a = if (rnd.nextFloat() < 0.18f) rnd.nextInt(10, 40) else 0
        (a shl 24)
    }
    android.graphics.Bitmap.createBitmap(px, size, size, android.graphics.Bitmap.Config.ARGB_8888)
}

private fun grainBrush() = ShaderBrush(
    android.graphics.BitmapShader(grainBitmap, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT),
)

/** Very faint paper grain over a whole page. */
fun Modifier.paperGrain(): Modifier = drawBehind { drawRect(grainBrush(), alpha = 0.12f) }
