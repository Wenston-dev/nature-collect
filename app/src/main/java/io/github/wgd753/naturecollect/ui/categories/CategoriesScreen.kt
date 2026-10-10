package io.github.wgd753.naturecollect.ui.categories

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
import io.github.wgd753.naturecollect.ui.tint

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
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.primary)
            }
            Text("分类管理", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            TextButton(onClick = { confirmReset = true }) { Text("恢复默认") }
        }
        Text(
            "拍照识别时，AI 会从这些分类里挑一个。可以给分类写一句说明，帮它分得更准。",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(categories, key = { _, c -> c.key }) { index, category ->
                Row(
                    Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { editing = category }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(category.tint.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) { Text(category.emoji, fontSize = 22.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(category.label, fontWeight = FontWeight.Bold, color = category.tint)
                        Text(
                            category.hint ?: "没有说明",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = { save(categories.move(index, index - 1)) }, enabled = index > 0) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上移")
                    }
                    IconButton(onClick = { save(categories.move(index, index + 1)) }, enabled = index < categories.lastIndex) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下移")
                    }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .clickable { adding = true }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("添加分类", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
            title = { Text("恢复默认分类？") },
            text = { Text("你添加的分类会被移除，里面的收集会归到「其他」。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    container.categories.resetToDefaults()
                }) { Text("恢复", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("取消") } },
        )
    }
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

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除「${initial.label}」？") },
            text = { Text("这个分类里的收集不会删除，会归到「其他」。") },
            confirmButton = { TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "添加分类" else "编辑分类") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(Color(color).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) { Text(emoji, fontSize = 26.sp) }
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { if (it.isNotEmpty()) emoji = it.take(8) },
                        label = { Text("Emoji") },
                        singleLine = true,
                        modifier = Modifier.width(110.dp),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    EmojiPicks.forEach { e ->
                        Text(
                            e,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (e == emoji) Color(color).copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { emoji = e }
                                .padding(4.dp),
                        )
                    }
                }
                OutlinedTextField(value = label, onValueChange = { label = it.take(8) }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(
                    value = hint,
                    onValueChange = { hint = it.take(60) },
                    label = { Text("说明（可选，给 AI 看）") },
                    placeholder = { Text("例如：各种石头和矿物") },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                onClick = { onSave(initial.copy(label = label.trim(), emoji = emoji.trim(), hint = hint.trim().ifEmpty { null }, color = color)) },
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
