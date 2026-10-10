package io.github.wgd753.naturecollect.ui.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.github.wgd753.naturecollect.data.Category
import io.github.wgd753.naturecollect.data.db.CaptureEntity
import io.github.wgd753.naturecollect.data.db.RecognitionStatus
import io.github.wgd753.naturecollect.ui.FilterPill
import io.github.wgd753.naturecollect.ui.MediaImage
import io.github.wgd753.naturecollect.ui.StickerImage
import io.github.wgd753.naturecollect.ui.Tag
import io.github.wgd753.naturecollect.ui.formatDate
import io.github.wgd753.naturecollect.ui.holographic
import io.github.wgd753.naturecollect.ui.rememberContainer
import io.github.wgd753.naturecollect.ui.theme.Card
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptureDetailScreen(captureId: String, onBack: () -> Unit, onOpenBoard: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val capture by remember(captureId) { container.captures.observe(captureId) }.collectAsState(initial = null)
    var showPhoto by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf(false) }
    var editingNotes by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val c = capture
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.primary)
            }
            if (c != null) {
                Row {
                    IconButton(onClick = {
                        scope.launch {
                            container.boards.ensureDefaultBoard()
                            val board = container.boards.observeBoards().first().first()
                            container.boards.addSticker(
                                board.id,
                                c.id,
                                x = 0.3f + Random.nextFloat() * 0.4f,
                                y = 0.3f + Random.nextFloat() * 0.4f,
                                rotation = Random.nextFloat() * 16f - 8f,
                            )
                            onOpenBoard()
                        }
                    }) {
                        Icon(Icons.Outlined.AutoAwesomeMosaic, contentDescription = "贴到画板", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        if (c == null) return@Column

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(targetState = showPhoto, label = "photoToggle") { photo ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { showPhoto = !showPhoto },
                    contentAlignment = Alignment.Center,
                ) {
                    if (photo) {
                        MediaImage(container.media.file(c.photoFile), Modifier.fillMaxSize())
                    } else {
                        StickerImage(c, Modifier.fillMaxSize().padding(12.dp).holographic(0.32f, 3000))
                    }
                }
            }
            Text(
                if (showPhoto) "点一下看贴纸" else "点一下看原图",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.displayName, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = { editingName = true }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "改名字", tint = MaterialTheme.colorScheme.primary)
                }
            }
            c.nameSci?.let { Text(it, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(8.dp))
            RecognitionLine(c) { scope.launch { container.recognition.retry(c.id) } }
            c.description?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(16.dp),
                )
            }
            val alternatives = c.alternatives?.split('|')?.filter { it.isNotBlank() }.orEmpty()
            if (alternatives.isNotEmpty() && c.status == RecognitionStatus.DONE) {
                Spacer(Modifier.height(12.dp))
                Text("也可能是：", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    alternatives.forEach { name ->
                        FilterPill(name, selected = false) {
                            scope.launch { container.captures.rename(c.id, name, Category.fromKey(c.category)) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            InfoCard(
                rows = listOf(
                    "时间" to formatDate(c.createdAt),
                    "地点" to (c.placeName ?: "未填写"),
                    "坐标" to (c.latitude?.let { "%.5f, %.5f".format(it, c.longitude) } ?: "没有位置信息"),
                    "笔记" to (c.note ?: "—"),
                ),
                onEdit = { editingNotes = true },
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (editingName && c != null) {
        RenameDialog(c, onDismiss = { editingName = false }) { name, category ->
            editingName = false
            scope.launch { container.captures.rename(c.id, name, category) }
        }
    }
    if (editingNotes && c != null) {
        NotesDialog(c, onDismiss = { editingNotes = false }) { place, note ->
            editingNotes = false
            scope.launch { container.captures.updateNotes(c.id, place, note) }
        }
    }
    if (confirmDelete && c != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这次收集？") },
            text = { Text("贴纸和原图都会删除，贴纸板上的这张也会一起移除。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        container.captures.delete(c.id)
                        onBack()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun RecognitionLine(capture: CaptureEntity, onRetry: () -> Unit) {
    val category = Category.fromKey(capture.category)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tag("${category.emoji} ${category.label}")
        when (capture.status) {
            RecognitionStatus.DONE -> capture.confidence?.let { Tag("把握 ${(it * 100).toInt()}%") }
            RecognitionStatus.MANUAL -> Tag("手动命名")
            RecognitionStatus.PENDING, RecognitionStatus.RUNNING -> Tag(capture.errorMessage ?: "等待识别")
            RecognitionStatus.FAILED -> Tag(capture.errorMessage ?: "识别失败")
        }
        if (capture.status == RecognitionStatus.FAILED || capture.status == RecognitionStatus.DONE ||
            (capture.status == RecognitionStatus.PENDING && capture.errorMessage != null)
        ) {
            IconButton(onClick = onRetry) {
                Icon(Icons.Outlined.Refresh, contentDescription = "重新识别", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun InfoCard(rows: List<Pair<String, String>>, onEdit: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .clickable(onClick = onEdit)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { (label, value) ->
            Row {
                Text(label, modifier = Modifier.padding(end = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value)
            }
        }
        Text("点这里编辑地点和笔记", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenameDialog(capture: CaptureEntity, onDismiss: () -> Unit, onSave: (String, Category) -> Unit) {
    var name by remember { mutableStateOf(capture.nameZh.orEmpty()) }
    var category by remember { mutableStateOf(Category.fromKey(capture.category)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("它叫什么？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("中文名") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Category.entries.forEach { option ->
                        FilterPill("${option.emoji} ${option.label}", selected = option == category) { category = option }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, category) }, enabled = name.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun NotesDialog(capture: CaptureEntity, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var place by remember { mutableStateOf(capture.placeName.orEmpty()) }
    var note by remember { mutableStateOf(capture.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("地点和笔记") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = place, onValueChange = { place = it }, label = { Text("地点，例如 西湖公园") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("笔记") }, minLines = 3)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(place, note) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
