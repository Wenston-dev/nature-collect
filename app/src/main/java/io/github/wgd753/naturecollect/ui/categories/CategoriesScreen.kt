package io.github.wgd753.naturecollect.ui.categories

import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wgd753.naturecollect.data.Category
import io.github.wgd753.naturecollect.ui.rememberContainer
import io.github.wgd753.naturecollect.ui.ColorSwatchButton
import io.github.wgd753.naturecollect.ui.specimenCard
import io.github.wgd753.naturecollect.ui.theme.Pine
import io.github.wgd753.naturecollect.ui.theme.Soft
import io.github.wgd753.naturecollect.ui.theme.spaced
import io.github.wgd753.naturecollect.ui.tint
import io.github.wgd753.naturecollect.ui.tr

private val Palette = listOf(
    0xFF3D7DCA, 0xFF2BA3A8, 0xFF3E9B57, 0xFF8DB33A, 0xFFE5B21F,
    0xFFE58A1F, 0xFFE4574A, 0xFFE45A92, 0xFF9B5BD6, 0xFF7A6FD8, 0xFF9C6B3E, 0xFF5F6B73,
)

private val EmojiPicks = listOf(
    "🐦", "🦆", "🦉", "🐿️", "🐱", "🐶", "🐟", "🐢", "🦋", "🐝", "🐞", "🕷️",
    "🌸", "🌻", "🌷", "🌹", "🌿", "🌳", "🌵", "🍄", "🍁", "🍂", "🍓", "🌰",
    "🪨", "🐚", "🪶", "🌊", "⛰️", "🏙️", "🍜", "☕", "📚", "🧸", "🚗", "✨",
)

/** Add, rename, recolour, reorder and delete the collection's categories. */
@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val categories = Category.entries
    var editing by remember { mutableStateOf<Category?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    fun save(list: List<Category>) = container.categories.save(list)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = tr("返回", "Back"), tint = Pine)
            }
            Text(spaced(tr("分类管理", "Categories")), style = MaterialTheme.typography.titleLarge, color = Pine, modifier = Modifier.weight(1f))
            TextButton(onClick = { confirmReset = true }) { Text(tr("恢复默认", "Reset"), color = Soft) }
        }
        // Long-press a row and drag to reorder; the new order is saved on release.
        var order by remember(categories) { mutableStateOf(categories) }
        var draggingKey by remember { mutableStateOf<String?>(null) }
        var dragOffset by remember { mutableFloatStateOf(0f) }
        val spacing = with(LocalDensity.current) { 10.dp.toPx() }
        val haptics = LocalHapticFeedback.current
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            order.forEach { category ->
                key(category.key) {
                    val dragging = draggingKey == category.key
                    var rowHeight by remember { mutableIntStateOf(0) }
                    // A lifted row casts a deeper shadow, so it reads above the others while dragging.
                    val lift by animateDpAsState(if (dragging) 5.dp else 1.dp, label = "lift")
                    Row(
                        Modifier
                            .zIndex(if (dragging) 1f else 0f)
                            .onSizeChanged { rowHeight = it.height }
                            .graphicsLayer {
                                translationY = if (dragging) dragOffset else 0f
                                val sc = if (dragging) 1.03f else 1f
                                scaleX = sc
                                scaleY = sc
                            }
                            .pointerInput(category.key) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingKey = category.key
                                        dragOffset = 0f
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val step = rowHeight + spacing
                                        val i = order.indexOfFirst { it.key == category.key }
                                        if (dragOffset > step / 2 && i in 0 until order.lastIndex) {
                                            order = order.move(i, i + 1)
                                            dragOffset -= step
                                        } else if (dragOffset < -step / 2 && i > 0) {
                                            order = order.move(i, i - 1)
                                            dragOffset += step
                                        }
                                    },
                                    onDragEnd = {
                                        draggingKey = null
                                        dragOffset = 0f
                                        if (order != categories) save(order)
                                    },
                                    onDragCancel = {
                                        draggingKey = null
                                        dragOffset = 0f
                                        order = categories
                                    },
                                )
                            }
                            .fillMaxWidth()
                            .specimenCard(lift)
                            .clickable { editing = category }
                            .padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            Text(category.emoji, fontSize = 20.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(category.tint))
                                Spacer(Modifier.width(7.dp))
                                Text(category.display, style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.sp), color = Pine)
                            }
                            Text(
                                category.hint ?: tr("没有说明", "No hint"),
                                style = MaterialTheme.typography.labelMedium,
                                color = Soft,
                                maxLines = 1,
                            )
                        }
                        Icon(
                            Icons.Outlined.DragHandle,
                            contentDescription = null,
                            tint = Soft.copy(alpha = 0.6f),
                        )
                    }
                }
            }
            run {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, Pine.copy(alpha = 0.35f))
                        .clickable { adding = true }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = Pine, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("添加分类", "Add category"), color = Pine, style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.sp))
                }
            }
        }
    }

    if (adding) {
        CategoryDialog(
            initial = Category(container.categories.newKey(), "", "✨", Palette.random()),
            isNew = true,
            onDismiss = { adding = false },
            onSave = {
                adding = false
                // New categories go just before "其他", which stays last as the catch-all.
                val otherIndex = categories.indexOfFirst { c -> c.key == Category.OTHER_KEY }.takeIf { i -> i >= 0 } ?: categories.size
                save(categories.toMutableList().apply { add(otherIndex, it) })
            },
            onDelete = null,
        )
    }
    editing?.let { category ->
        CategoryDialog(
            initial = category,
            isNew = false,
            onDismiss = { editing = null },
            onSave = { updated ->
                editing = null
                save(categories.map { if (it.key == updated.key) updated else it })
            },
            onDelete = if (category.key == Category.OTHER_KEY) null else {
                {
                    editing = null
                    save(categories.filter { it.key != category.key })
                }
            },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(tr("恢复默认分类？", "Reset categories?")) },
            text = { Text(tr("你添加的分类会被移除，里面的收集会归到「其他」。", "Categories you added will be removed. Their finds move to “Other”.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    container.categories.resetToDefaults()
                }) { Text(tr("恢复", "Reset"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(tr("取消", "Cancel")) } },
        )
    }
}

/** Keeps the first [count] user-perceived characters, so a multi-codepoint emoji stays whole. */
private fun firstGraphemes(text: String, count: Int): String {
    val breaks = java.text.BreakIterator.getCharacterInstance()
    breaks.setText(text)
    var end = 0
    repeat(count) {
        val next = breaks.next()
        if (next == java.text.BreakIterator.DONE) return text
        end = next
    }
    return text.substring(0, end)
}

private fun List<Category>.move(from: Int, to: Int): List<Category> {
    if (to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(
    initial: Category,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var label by remember { mutableStateOf(initial.label) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var hint by remember { mutableStateOf(initial.hint.orEmpty()) }
    var color by remember { mutableStateOf(initial.color) }
    var confirmDelete by remember { mutableStateOf(false) }
    val emojiFocus = remember { FocusRequester() }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("删除「${initial.display}」？", "Delete “${initial.display}”?")) },
            text = { Text(tr("这个分类里的收集不会删除，会归到「其他」。", "Finds in this category won't be deleted. They move to “Other”.")) },
            confirmButton = { TextButton(onClick = onDelete) { Text(tr("删除", "Delete"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("取消", "Cancel")) } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) tr("添加分类", "Add category") else tr("编辑分类", "Edit category")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Any emoji works: type it with the keyboard's emoji panel, or pick a common one.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(color).copy(alpha = 0.16f))
                            .border(1.dp, Color(color).copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                            .clickable { emojiFocus.requestFocus() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(emoji.ifEmpty { "?" }, fontSize = 26.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = firstGraphemes(it, 2) },
                        label = { Text(tr("自定义 emoji", "Custom emoji")) },
                        placeholder = { Text(tr("用表情键盘输入", "Use the emoji keyboard")) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 22.sp),
                        modifier = Modifier.weight(1f).focusRequester(emojiFocus),
                    )
                }
                Text(tr("常用", "Common"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    EmojiPicks.forEach { e ->
                        Text(
                            e,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (e == emoji) Color(color).copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { emoji = e }
                                .padding(4.dp),
                        )
                    }
                }
                OutlinedTextField(value = label, onValueChange = { label = it.take(8) }, label = { Text(tr("名称", "Name")) }, singleLine = true)
                OutlinedTextField(
                    value = hint,
                    onValueChange = { hint = it.take(60) },
                    label = { Text(tr("识别提示（可选）", "Recognition hint (optional)")) },
                    placeholder = { Text(tr("例如：各种石头和矿物", "e.g. rocks and minerals")) },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorSwatchButton(color, tr("分类颜色", "Category color"), onPick = { color = it }, size = 28.dp)
                    Palette.forEach { c ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank(),
                onClick = { onSave(initial.copy(label = label.trim(), emoji = emoji.trim().ifEmpty { "✨" }, hint = hint.trim().ifEmpty { null }, color = color)) },
            ) { Text(tr("保存", "Save")) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text(tr("删除", "Delete"), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(tr("取消", "Cancel")) }
            }
        },
    )
}
