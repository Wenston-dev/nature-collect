package io.github.wgd753.naturecollect.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import io.github.wgd753.naturecollect.data.Category
import io.github.wgd753.naturecollect.ui.theme.WashCoral
import io.github.wgd753.naturecollect.ui.theme.WashLilac
import io.github.wgd753.naturecollect.ui.theme.WashOchre
import io.github.wgd753.naturecollect.ui.theme.WashSap
import io.github.wgd753.naturecollect.ui.theme.WashStone
import io.github.wgd753.naturecollect.ui.theme.WashWater
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val Washes = listOf(WashSap, WashWater, WashOchre, WashCoral, WashLilac)

/**
 * The watercolour pigment closest in hue to [color]: any category or sticker
 * colour maps onto the small sketchbook palette, so washes never get garish.
 */
fun washFor(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    if (hsv[1] < 0.18f) return WashStone
    val hues = mapOf(WashSap to 100f, WashWater to 195f, WashOchre to 40f, WashCoral to 10f, WashLilac to 265f)
    return Washes.minBy { w ->
        val d = kotlin.math.abs(hues.getValue(w) - hsv[0])
        minOf(d, 360f - d)
    }
}

fun washFor(category: Category): Color = washFor(category.tint)

/**
 * An irregular watercolour blot: a few overlapping translucent layers with a
 * slightly darker drying edge and a little pigment granulation. [seed] keeps
 * each item's shape stable across recompositions.
 */
@Composable
fun WatercolorWash(color: Color, seed: Int, modifier: Modifier = Modifier, strength: Float = 1f) {
    androidx.compose.foundation.layout.Spacer(modifier.watercolorWash(color, seed, strength))
}

/** Paths are built once per size, so long grids of washes scroll smoothly. */
fun Modifier.watercolorWash(color: Color, seed: Int, strength: Float = 1f): Modifier =
    drawWithCache {
        val rnd = Random(seed)
        val c = Offset(size.width / 2, size.height / 2)
        val rx = size.width * 0.46f
        val ry = size.height * 0.44f
        val main = blob(c, rx, ry, rnd)
        val inner = blob(
            c + Offset((rnd.nextFloat() - 0.5f) * rx * 0.25f, (rnd.nextFloat() - 0.5f) * ry * 0.25f),
            rx * (0.68f + rnd.nextFloat() * 0.12f),
            ry * (0.66f + rnd.nextFloat() * 0.12f),
            rnd,
        )
        val edge = Stroke(width = 1.6f * density)
        val grain = grainBrush()
        onDrawBehind {
            // Layers: a broad pale pool, a denser offset pool, the drying edge, then granulation.
            drawPath(main, color, alpha = 0.26f * strength)
            drawPath(inner, color, alpha = 0.16f * strength)
            drawPath(main, color, alpha = 0.32f * strength, style = edge)
            clipPath(main) { drawRect(grain, alpha = 0.5f * strength) }
        }
    }

private fun blob(center: Offset, rx: Float, ry: Float, rnd: Random): Path {
    val n = 56
    val k1 = rnd.nextFloat() * 6.28f
    val k2 = rnd.nextFloat() * 6.28f
    val k3 = rnd.nextFloat() * 6.28f
    val a1 = 0.07f + rnd.nextFloat() * 0.06f
    val a2 = 0.04f + rnd.nextFloat() * 0.04f
    return Path().apply {
        for (i in 0..n) {
            val t = (i % n) * 2 * PI.toFloat() / n
            val wobble = 1f + a1 * sin(2 * t + k1) + a2 * sin(3 * t + k2) + 0.025f * sin(7 * t + k3)
            val x = center.x + cos(t) * rx * wobble
            val y = center.y + sin(t) * ry * wobble
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
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

/** Faint paper grain over a whole screen. */
fun Modifier.paperGrain(): Modifier = drawBehind { drawRect(grainBrush(), alpha = 0.22f) }
