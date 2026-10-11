package io.github.wgd753.naturecollect.ui.cutout

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.wgd753.naturecollect.ml.PromptPoint
import io.github.wgd753.naturecollect.ml.SamPrompt
import io.github.wgd753.naturecollect.ui.theme.Pine
import io.github.wgd753.naturecollect.ui.theme.Rust
import kotlin.math.hypot

/**
 * The photo with the current selection highlighted. Draw a loop around what
 * you want (best for bottles, flowers, anything SAM might cut in half), or
 * tap it; further taps add to the selection.
 */
@Composable
fun SubjectPicker(
    bitmap: Bitmap,
    overlay: Bitmap?,
    busy: Boolean,
    onPrompt: (SamPrompt) -> Unit,
    modifier: Modifier = Modifier,
    erasing: Boolean = false,
    onErase: (List<PromptPoint>) -> Unit = {},
) {
    // Stroke points in view coordinates; kept after lifting so the loop stays visible.
    val stroke = remember(bitmap) { mutableStateListOf<Offset>() }
    val currentOnPrompt by rememberUpdatedState(onPrompt)
    val currentOnErase by rememberUpdatedState(onErase)
    val currentErasing by rememberUpdatedState(erasing)
    // The eraser trail only shows while the finger is down.
    var erasingNow by remember { mutableStateOf(false) }
    val transition = rememberInfiniteTransition(label = "ants")
    val dashPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "dash",
    )

    Box(
        modifier
            .aspectRatio(bitmap.width.toFloat() / bitmap.height)
            .clip(RoundedCornerShape(2.dp))
            .pointerInput(bitmap) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val eraser = currentErasing
                    erasingNow = eraser
                    stroke.clear()
                    stroke.add(down.position)
                    var travelled = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val last = stroke.last()
                        val d = hypot(change.position.x - last.x, change.position.y - last.y)
                        if (d > 4f) {
                            travelled += d
                            stroke.add(change.position)
                        }
                        change.consume()
                    }
                    val sx = bitmap.width / size.width.toFloat()
                    val sy = bitmap.height / size.height.toFloat()
                    if (eraser) {
                        val points = stroke.map { PromptPoint(it.x * sx, it.y * sy, positive = false) }
                        stroke.clear()
                        erasingNow = false
                        currentOnErase(points)
                    } else if (travelled < 24f || stroke.size < 6) {
                        val p = stroke.first()
                        stroke.clear()
                        currentOnPrompt(SamPrompt.Points(listOf(PromptPoint(p.x * sx, p.y * sy))))
                    } else {
                        currentOnPrompt(SamPrompt.Lasso(stroke.map { PromptPoint(it.x * sx, it.y * sy) }))
                    }
                }
            }
            .drawWithContent {
                drawContent()
                if (stroke.size > 1) {
                    val path = Path().apply {
                        moveTo(stroke[0].x, stroke[0].y)
                        for (i in 1 until stroke.size) lineTo(stroke[i].x, stroke[i].y)
                    }
                    if (erasingNow) {
                        // A soft wide trail the size of the eraser brush.
                        val brush = size.maxDimension * io.github.wgd753.naturecollect.ml.SamSegmenter.ERASER_WIDTH
                        drawPath(path, Rust.copy(alpha = 0.5f), style = Stroke(brush, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        return@drawWithContent
                    }
                    val width = 2.dp.toPx()
                    drawPath(path, Pine.copy(alpha = 0.45f), style = Stroke(width + 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(
                        path,
                        Color.White,
                        style = Stroke(
                            width,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), dashPhase),
                        ),
                    )
                }
            },
    ) {
        Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
        overlay?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
        if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White, strokeWidth = 2.dp)
    }
}
