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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import android.graphics.BitmapFactory
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.platform.LocalContext
import io.github.wgd753.naturecollect.export.Gallery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Switch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import io.github.wgd753.naturecollect.data.speciesKey
import io.github.wgd753.naturecollect.recognition.RecognitionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import io.github.wgd753.naturecollect.ui.tint
import io.github.wgd753.naturecollect.ui.tr
import io.github.wgd753.naturecollect.ui.Lang
import io.github.wgd753.naturecollect.ui.theme.Card
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptureDetailScreen(
    captureId: String,
    onBack: () -> Unit,
    onOpenBoard: () -> Unit,
    onOpenCapture: (String) -> Unit,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val capture by remember(captureId) { container.captures.observe(captureId) }.collectAsState(initial = null)
    var showPhoto by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf(false) }
    var editingNotes by remember { mutableStateOf(false) }
    var editingDescription by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val all by container.captures.observeAll().collectAsState(initial = emptyList())

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showSaveMenu by remember { mutableStateOf(false) }

    /** Renames, then offers a one-tap undo that puts the old names back. */
    fun renameWithUndo(previous: CaptureEntity, newName: String, action: suspend () -> Unit) {
        scope.launch {
            action()
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(tr("已改成「$newName」", "Renamed to “$newName”"), actionLabel = tr("撤回", "Undo"), duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) container.captures.update(previous)
        }
    }

    fun saveImage(file: java.io.File, label: String) {
        scope.launch {
            val message = runCatching {
                withContext(Dispatchers.IO) {
                    val bitmap = BitmapFactory.decodeFile(file.path) ?: error(tr("文件不见了", "File not found"))
                    Gallery.save(context, bitmap, label, jpeg = file.name.endsWith(".jpg"))
                }
            }.fold({ tr("已保存到$it", "Saved to $it") }, { tr("保存失败：${it.message}", "Couldn't save: ${it.message}") })
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    val c = capture
    // Every encounter of the same species, newest first, so this page doubles as the species page.
    val siblings = remember(all, c?.nameZh, c?.id) {
        c?.let { current -> all.filter { speciesKey(it) == speciesKey(current) }.sortedByDescending { it.createdAt } }.orEmpty()
    }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = tr("返回", "Back"), tint = MaterialTheme.colorScheme.primary)
            }
            if (c != null) {
                Row {
                    Box {
                        IconButton(onClick = { showSaveMenu = true }) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = tr("保存到相册", "Save to gallery"), tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(expanded = showSaveMenu, onDismissRequest = { showSaveMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(tr("保存贴纸（透明背景）", "Save sticker (transparent)")) },
                                onClick = {
                                    showSaveMenu = false
                                    saveImage(container.media.file(c.stickerFile), tr("${c.displayName}-贴纸", "${c.displayName}-sticker"))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(tr("保存原图", "Save original photo")) },
                                onClick = {
                                    showSaveMenu = false
                                    saveImage(container.media.file(c.photoFile), tr("${c.displayName}-原图", "${c.displayName}-photo"))
                                },
                            )
                        }
                    }
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
                        Icon(Icons.Outlined.AutoAwesomeMosaic, contentDescription = tr("贴到画板", "Add to board"), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = tr("删除", "Delete"), tint = MaterialTheme.colorScheme.error)
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
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.displayName, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = { editingName = true }) {
                    Icon(Icons.Outlined.Edit, contentDescription = tr("改名字", "Rename"), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                c.nameSci ?: tr("还没有英文名 / 学名，点 ✎ 补充", "No English name yet. Tap ✎ to add"),
                modifier = Modifier.clickable { editingName = true },
                fontStyle = if (c.nameSci != null) FontStyle.Italic else FontStyle.Normal,
                style = if (c.nameSci != null) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            RecognitionLine(c) { scope.launch { container.recognition.retry(c.id) } }
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Card)
                    .clickable { editingDescription = true }
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("简介", "About"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.Edit, contentDescription = tr("编辑简介", "Edit description"), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    c.description ?: tr("还没有简介，点这里写一句，或者让 AI 帮你写", "No description yet. Tap to write one, or let AI do it"),
                    color = if (c.description != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val alternatives = c.alternatives?.split('|')?.filter { it.isNotBlank() }.orEmpty()
            if (alternatives.isNotEmpty() && c.status == RecognitionStatus.DONE) {
                Spacer(Modifier.height(12.dp))
                Text(tr("也可能是：", "Could also be:"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    alternatives.forEach { name ->
                        FilterPill(name, selected = false) {
                            renameWithUndo(c, name) {
                                container.captures.rename(c.id, name, Category.fromKey(c.category), nameSci = null)
                                container.recognition.fillDetails(c.id)
                            }
                        }
                    }
                }
            }
            if (siblings.size > 1) {
                Spacer(Modifier.height(16.dp))
                Encounters(siblings, currentId = c.id, onOpen = { if (it != c.id) onOpenCapture(it) })
            }
            Spacer(Modifier.height(16.dp))
            InfoCard(
                rows = listOf(
                    tr("时间", "Time") to formatDate(c.createdAt),
                    tr("地点", "Place") to (c.placeName ?: tr("未填写", "Not set")),
                    tr("坐标", "Coordinates") to (c.latitude?.let { "%.5f, %.5f".format(it, c.longitude) } ?: tr("没有位置信息", "No location")),
                    tr("笔记", "Notes") to (c.note ?: "—"),
                ),
                onEdit = { editingNotes = true },
            )
            Spacer(Modifier.height(32.dp))
        }
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    if (editingName && c != null) {
        RenameDialog(c, onDismiss = { editingName = false }) { name, sci, category, description ->
            editingName = false
            renameWithUndo(c, name) { container.captures.rename(c.id, name, category, sci, description) }
        }
    }
    if (editingDescription && c != null) {
        DescriptionDialog(c, onDismiss = { editingDescription = false }) { text ->
            editingDescription = false
            scope.launch { container.captures.updateDescription(c.id, text) }
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
            title = { Text(if (siblings.size > 1) tr("删除「${c.displayName}」？", "Delete “${c.displayName}”?") else tr("删除这次收集？", "Delete this find?")) },
            text = {
                Text(
                    if (siblings.size > 1) {
                        tr("图鉴里它一共有 ${siblings.size} 次遇见。可以只删这一次，也可以全部删除。贴纸、原图和贴纸板上的贴纸会一起移除。", "You've found this ${siblings.size} times. Delete just this one, or all of them. Stickers, photos and board stickers will be removed too.")
                    } else {
                        tr("贴纸和原图都会删除，贴纸板上的这张也会一起移除。", "The sticker and photo will be deleted, and removed from the board.")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        container.captures.delete(c.id)
                        onBack()
                    }
                }) { Text(if (siblings.size > 1) tr("只删这一次", "Just this one") else tr("删除", "Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                Row {
                    if (siblings.size > 1) {
                        TextButton(onClick = {
                            confirmDelete = false
                            val ids = siblings.map { it.id }
                            scope.launch {
                                container.captures.deleteAll(ids)
                                onBack()
                            }
                        }) { Text(tr("全部 ${siblings.size} 次", "All ${siblings.size}"), color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = { confirmDelete = false }) { Text(tr("取消", "Cancel")) }
                }
            },
        )
    }
}

@Composable
private fun RecognitionLine(capture: CaptureEntity, onRetry: () -> Unit) {
    val category = Category.fromKey(capture.category)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tag("${category.emoji} ${category.display}", color = category.tint.copy(alpha = 0.22f))
        when (capture.status) {
            RecognitionStatus.DONE -> capture.confidence?.let { Tag(tr("把握 ${(it * 100).toInt()}%", "${(it * 100).toInt()}% sure")) }
            RecognitionStatus.MANUAL -> Tag(tr("手动命名", "Named by hand"))
            RecognitionStatus.PENDING, RecognitionStatus.RUNNING -> Tag(capture.errorMessage ?: tr("等待识别", "Waiting"))
            RecognitionStatus.FAILED -> Tag(capture.errorMessage ?: tr("识别失败", "Not recognized"))
        }
        if (capture.status == RecognitionStatus.FAILED || capture.status == RecognitionStatus.DONE ||
            (capture.status == RecognitionStatus.PENDING && capture.errorMessage != null)
        ) {
            IconButton(onClick = onRetry) {
                Icon(Icons.Outlined.Refresh, contentDescription = tr("重新识别", "Recognize again"), tint = MaterialTheme.colorScheme.primary)
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
        rows.forEachIndexed { i, (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.padding(end = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, modifier = Modifier.weight(1f))
                if (i == 0) {
                    Icon(Icons.Outlined.Edit, contentDescription = tr("编辑", "Edit"), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun Encounters(siblings: List<CaptureEntity>, currentId: String, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            tr("每一次遇见 · ${siblings.size} 次", "Every find · ${siblings.size}"),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        LazyRow(
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(siblings, key = { it.id }) { item ->
                val current = item.id == currentId
                Column(
                    Modifier
                        .width(84.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Card)
                        .border(if (current) 2.dp else 0.dp, if (current) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable { onOpen(item.id) }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StickerImage(item, Modifier.size(64.dp))
                    Text(
                        (if (Lang.english) java.text.SimpleDateFormat("MMM d", java.util.Locale.ENGLISH) else java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINA)).format(java.util.Date(item.createdAt)),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private enum class NameField { ZH, SCI }

/**
 * Edit both names together. Typing in one field translates it into the other
 * after a short pause (unless the other was typed by hand), and also fills in
 * the category and blurb; "立即翻译" forces it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenameDialog(
    capture: CaptureEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, Category, String?) -> Unit,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var zh by remember { mutableStateOf(capture.nameZh.orEmpty()) }
    var sci by remember { mutableStateOf(capture.nameSci.orEmpty()) }
    var category by remember { mutableStateOf(Category.fromKey(capture.category)) }
    var description by remember { mutableStateOf<String?>(null) }
    var autoTranslate by rememberSaveable { mutableStateOf(true) }
    var lastEdited by remember { mutableStateOf<NameField?>(null) }
    var typedByHand by remember { mutableStateOf(emptySet<NameField>()) }
    var categoryTouched by remember { mutableStateOf(false) }
    var translating by remember { mutableStateOf<NameField?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var manualJob by remember { mutableStateOf<Job?>(null) }

    suspend fun translate(from: NameField, force: Boolean) {
        val query = if (from == NameField.ZH) zh else sci
        if (query.isBlank()) return
        val target = if (from == NameField.ZH) NameField.SCI else NameField.ZH
        translating = target
        status = tr("正在翻译…", "Translating…")
        try {
            val result = container.recognition.lookupName(query)
            val keepTarget = !force && target in typedByHand && (if (target == NameField.ZH) zh else sci).isNotBlank()
            if (!keepTarget) {
                if (target == NameField.SCI) sci = result.nameSci.orEmpty() else zh = result.nameZh
                typedByHand = typedByHand - target
            }
            if (!categoryTouched) category = result.category
            description = result.description
            status = when {
                keepTarget -> tr("保留了你手填的名字", "Kept the name you typed")
                target == NameField.SCI && result.nameSci == null -> tr("没找到英文名", "No English name found")
                else -> tr("已翻译", "Translated")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RecognitionException) {
            status = e.message
        } catch (e: Exception) {
            status = tr("翻译失败", "Translation failed")
        } finally {
            translating = null
        }
    }

    // Auto-translate once typing pauses; a new keystroke cancels the pending one.
    // Keyed on the edited field only, so filling in the other field doesn't re-trigger it.
    val source = lastEdited
    val query = when (source) {
        NameField.ZH -> zh
        NameField.SCI -> sci
        null -> ""
    }
    LaunchedEffect(query, source, autoTranslate) {
        if (!autoTranslate || source == null) return@LaunchedEffect
        delay(900)
        translate(source, force = false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("它叫什么？", "What is it?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = zh,
                    onValueChange = {
                        zh = it
                        lastEdited = NameField.ZH
                        typedByHand = typedByHand + NameField.ZH
                    },
                    label = { Text(tr("中文名", "Chinese name")) },
                    singleLine = true,
                    trailingIcon = { if (translating == NameField.ZH) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
                )
                OutlinedTextField(
                    value = sci,
                    onValueChange = {
                        sci = it
                        lastEdited = NameField.SCI
                        typedByHand = typedByHand + NameField.SCI
                    },
                    label = { Text(tr("英文名 / 学名", "English name")) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontStyle = FontStyle.Italic),
                    trailingIcon = { if (translating == NameField.SCI) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = autoTranslate, onCheckedChange = { autoTranslate = it })
                    Spacer(Modifier.width(8.dp))
                    Text(tr("编辑后自动翻译", "Auto-translate"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(
                        enabled = translating == null && (zh.isNotBlank() || sci.isNotBlank()),
                        onClick = {
                            val from = lastEdited ?: if (zh.isNotBlank()) NameField.ZH else NameField.SCI
                            manualJob?.cancel()
                            manualJob = scope.launch { translate(from, force = true) }
                        },
                    ) { Text(tr("立即翻译", "Translate")) }
                }
                status?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Category.entries.forEach { option ->
                        FilterPill("${option.emoji} ${option.display}", selected = option == category, tint = option.tint) {
                            category = option
                            categoryTouched = true
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(zh, sci, category, description) }, enabled = zh.isNotBlank()) { Text(tr("保存", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消", "Cancel")) } },
    )
}

/** Write the blurb by hand, or ask DeepSeek for one based on the name. */
@Composable
private fun DescriptionDialog(capture: CaptureEntity, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(capture.description.orEmpty()) }
    var writing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("简介", "About")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(200) },
                    minLines = 3,
                    placeholder = { Text(tr("写一句关于它的介绍", "A line about it")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = !writing && capture.nameZh != null,
                        onClick = {
                            writing = true
                            status = null
                            scope.launch {
                                try {
                                    val result = container.recognition.lookupName(capture.nameZh.orEmpty())
                                    result.description?.let { text = it } ?: run { status = tr("AI 没有写出来，再试一次", "AI came up empty. Try again") }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    status = e.message ?: tr("生成失败", "Couldn't generate")
                                } finally {
                                    writing = false
                                }
                            }
                        },
                    ) { Text(tr("✨ 让 AI 写一句", "✨ Let AI write one")) }
                    if (writing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                status?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(tr("保存", "Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消", "Cancel")) } },
    )
}

@Composable
private fun NotesDialog(capture: CaptureEntity, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var place by remember { mutableStateOf(capture.placeName.orEmpty()) }
    var note by remember { mutableStateOf(capture.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("地点和笔记", "Place and notes")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = place, onValueChange = { place = it }, label = { Text(tr("地点，例如 西湖公园", "Place, e.g. Central Park")) }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(tr("笔记", "Notes")) }, minLines = 3)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(place, note) }) { Text(tr("保存", "Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消", "Cancel")) } },
    )
}
