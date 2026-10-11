package io.github.wgd753.naturecollect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.wgd753.naturecollect.ui.theme.Card
import io.github.wgd753.naturecollect.ui.theme.LabelBorder
import io.github.wgd753.naturecollect.ui.theme.Pine

private val Swatch = RoundedCornerShape(2.dp)

/** A handful of starting points; anything else comes from the picker. */
// The specimen palette first (pine, soft, rust, card stock), then the page,
// water, linen and label tones, and a few plain inks.
val QuickColors = listOf(
    0xFF22392F, 0xFF6F8277, 0xFFB4532E, 0xFFFBFBF7, 0xFFE2EBE3, 0xFFC9DCD5,
    0xFFE2DBC4, 0xFF9FB0A4, 0xFFFFFFFF, 0xFF1F2421, 0xFF3D6E9C, 0xFFC8963E,
)

/**
 * Full colour picker: drag on the square for saturation/brightness and on
 * the rainbow bar for hue, or tap a quick colour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(color: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(color.toInt(), it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }

    fun emit() {
        onChange(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)).toLong() and 0xFFFFFFFFL)
    }
    fun set(c: Long) {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toInt(), out)
        hue = out[0]; sat = out[1]; value = out[2]
        onChange(c)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val pure = Color.hsv(hue, 1f, 1f)
        // Saturation (x) / brightness (y) square.
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
                .clip(RoundedCornerShape(2.dp))
                .drawBehind {
                    drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val p = Offset(sat * size.width, (1 - value) * size.height)
                    drawCircle(Color.White, radius = 11.dp.toPx(), center = p, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.Black.copy(alpha = 0.3f), radius = 13.dp.toPx(), center = p, style = Stroke(1.dp.toPx()))
                }
                .pointerInput(Unit) {
                    fun update(o: Offset) {
                        sat = (o.x / size.width).coerceIn(0f, 1f)
                        value = 1f - (o.y / size.height).coerceIn(0f, 1f)
                        emit()
                    }
                    detectTapGestures { update(it) }
                }
                .pointerInput(Unit) {
                    detectDragGestures(onDragStart = {
                        sat = (it.x / size.width).coerceIn(0f, 1f)
                        value = 1f - (it.y / size.height).coerceIn(0f, 1f)
                        emit()
                    }) { change, _ ->
                        change.consume()
                        sat = (change.position.x / size.width).coerceIn(0f, 1f)
                        value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                        emit()
                    }
                },
        )
        // Hue bar.
        Box(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clip(RoundedCornerShape(2.dp))
                .drawBehind {
                    drawRect(Brush.horizontalGradient(List(7) { Color.hsv(it * 60f % 360f, 1f, 1f) }.let { it + Color.hsv(359f, 1f, 1f) }))
                    val x = hue / 360f * size.width
                    drawCircle(Color.White, radius = size.height / 2 - 2.dp.toPx(), center = Offset(x.coerceIn(size.height / 2, size.width - size.height / 2), size.height / 2), style = Stroke(3.dp.toPx()))
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        hue = (it.x / size.width).coerceIn(0f, 1f) * 359f
                        emit()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        hue = (change.position.x / size.width).coerceIn(0f, 1f) * 359f
                        emit()
                    }
                },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickColors.forEach { c ->
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(Swatch)
                        .background(Color(c))
                        .border(if (c == color) 2.dp else 1.dp, if (c == color) Pine else Color.Black.copy(alpha = 0.12f), Swatch)
                        .clickable { set(c) },
                )
            }
        }
    }
}

/** Dialog wrapper around [ColorPicker] with a live preview. */
@Composable
fun ColorPickerDialog(initial: Long, title: String, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    var color by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(Swatch).background(Color(color)).border(1.dp, Color.Black.copy(alpha = 0.12f), Swatch))
                Spacer(Modifier.width(10.dp))
                Text(title)
            }
        },
        text = { ColorPicker(color, onChange = { color = it }) },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text(tr("好", "OK")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消", "Cancel")) } },
    )
}

/** A colour swatch in a thin label-border frame that opens the picker. */
@Composable
fun ColorSwatchButton(color: Long, title: String, onPick: (Long) -> Unit, size: androidx.compose.ui.unit.Dp = 32.dp) {
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(size)
            .clip(Swatch)
            .background(Card)
            .border(1.dp, LabelBorder, Swatch)
            .clickable { open = true },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size - 10.dp).background(Color(color)))
    }
    if (open) {
        ColorPickerDialog(color, title, onDismiss = { open = false }) {
            open = false
            onPick(it)
        }
    }
}
