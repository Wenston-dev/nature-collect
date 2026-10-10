package io.github.wgd753.naturecollect.ui.cutout

import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.wgd753.naturecollect.ui.FilterPill
import io.github.wgd753.naturecollect.ui.tr

/**
 * Photo + selection tools shared by the camera and the board's gallery import:
 * circle or tap to choose, eraser to remove extra things, size steps to
 * widen or narrow SAM's mask, and undo for any of it.
 */
@Composable
fun ColumnScope.CutoutEditor(state: CutoutState) {
    Text(
        text = if (state.erasing) tr("涂抹要擦掉的部分", "Paint over what to erase") else tr("圈出或点选", "Circle or tap"),
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 6.dp),
        style = MaterialTheme.typography.titleMedium,
        color = if (state.erasing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        when {
            state.error != null -> Text(tr("抠图失败：${state.error}", "Cutout failed: ${state.error}"), color = MaterialTheme.colorScheme.error)
            else -> SubjectPicker(
                bitmap = state.image.bitmap,
                overlay = state.overlay,
                busy = state.busy,
                onPrompt = state::select,
                erasing = state.erasing,
                onErase = state::erase,
            )
        }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = state.optionCount > 1,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr("范围", "Range"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            sizeLabels(state.optionCount).forEachIndexed { index, label ->
                Box(Modifier.padding(horizontal = 3.dp)) {
                    FilterPill(label, selected = index == state.selectedSize) { state.chooseSize(index) }
                }
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        ToolChip(Icons.Outlined.Gesture, tr("圈选", "Select"), selected = !state.erasing) { state.erasing = false }
        ToolChip(Icons.Outlined.AutoFixHigh, tr("橡皮擦", "Erase"), selected = state.erasing, enabled = state.canErase) { state.erasing = true }
        ToolChip(Icons.AutoMirrored.Outlined.Undo, tr("撤回", "Undo"), selected = false, enabled = state.canUndo) { state.undo() }
    }
}

@Composable
private fun ToolChip(icon: ImageVector, label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = fg, style = MaterialTheme.typography.titleMedium)
    }
}

private fun sizeLabels(count: Int): List<String> = when (count) {
    2 -> listOf(tr("小", "S"), tr("大", "L"))
    3 -> listOf(tr("小", "S"), tr("中", "M"), tr("大", "L"))
    else -> listOf(tr("小", "S"), tr("中", "M"), tr("大", "L"), tr("最大", "XL")).take(count)
}

