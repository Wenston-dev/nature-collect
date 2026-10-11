package io.github.wgd753.naturecollect.ui.detail

import io.github.wgd753.naturecollect.ui.cutout.CutoutDialog
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.MoreVert
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import io.github.wgd753.naturecollect.export.ShareCard
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.unit.dp
import io.github.wgd753.naturecollect.data.Category
import io.github.wgd753.naturecollect.data.db.CaptureEntity
import io.github.wgd753.naturecollect.data.db.RecognitionStatus
import io.github.wgd753.naturecollect.ui.FilterPill
import io.github.wgd753.naturecollect.ui.MediaImage
import io.github.wgd753.naturecollect.ui.StickerImage
import io.github.wgd753.naturecollect.ui.holographic
import io.github.wgd753.naturecollect.ui.rememberContainer
import io.github.wgd753.naturecollect.ui.tint
import io.github.wgd753.naturecollect.ui.tr
import io.github.wgd753.naturecollect.ui.Lang
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import io.github.wgd753.naturecollect.export.SpecimenCanvas
import io.github.wgd753.naturecollect.ui.DateStamp
import io.github.wgd753.naturecollect.ui.Hairline
import io.github.wgd753.naturecollect.ui.TapeStrip
import io.github.wgd753.naturecollect.ui.specimenCard
import io.github.wgd753.naturecollect.ui.theme.Divider
import io.github.wgd753.naturecollect.ui.theme.LabelBorder
import io.github.wgd753.naturecollect.ui.theme.MonoFont
import io.github.wgd753.naturecollect.ui.theme.MonoStyle
import io.github.wgd753.naturecollect.ui.theme.Pine
import io.github.wgd753.naturecollect.ui.theme.Soft
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
    var showMore by remember { mutableStateOf(false) }
    var reframePhoto by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

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
    // This species' place in the collection, counted by when each species was first seen.
    val speciesNumber = remember(all, c?.nameZh) {
        val current = c
        if (current?.nameZh.isNullOrBlank()) {
            null
        } else {
            val order = all.filter { !it.nameZh.isNullOrBlank() }
                .groupBy { speciesKey(it) }
                .map { (key, list) -> key to list.minOf { it.createdAt } }
                .sortedBy { it.second }
                .map { it.first }
            order.indexOf(speciesKey(current!!)).takeIf { it >= 0 }?.plus(1)
        }
    }
    var sharing by remember { mutableStateOf(false) }
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
                    IconButton(
                        enabled = !sharing,
                        onClick = {
                            sharing = true
                            scope.launch {
                                try {
                                    ShareCard.share(context, container.media, c, speciesNumber, siblings.indexOfFirst { it.id == c.id }.takeIf { it >= 0 }?.let { siblings.size - it })
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    snackbar.currentSnackbarData?.dismiss()
                                    snackbar.showSnackbar(tr("分享失败：${e.message}", "Couldn't share: ${e.message}"))
                                } finally {
                                    sharing = false
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = tr("分享", "Share"), tint = MaterialTheme.colorScheme.primary)
                    }
                    Box {
                        IconButton(onClick = { showMore = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = tr("更多", "More"), tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            DropdownMenuItem(
                                text = { Text(tr("重新框选", "Re-select subject")) },
                                leadingIcon = { Icon(Icons.Outlined.Crop, contentDescription = null) },
                                onClick = {
                                    showMore = false
                                    scope.launch {
                                        reframePhoto = withContext(Dispatchers.IO) {
                                            BitmapFactory.decodeFile(container.media.file(c.photoFile).path)
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(tr("重新识别", "Identify again")) },
                                leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                                onClick = {
                                    showMore = false
                                    scope.launch { container.recognition.retry(c.id) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(tr("删除", "Delete"), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMore = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                }
            }
        }
        if (c == null) return@Column

        val category = Category.fromKey(c.category)
        // Which encounter of this species this find is, oldest = 1.
        val encounter = siblings.indexOfFirst { it.id == c.id }.takeIf { it >= 0 }?.let { siblings.size - it }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // One white sheet: the mounted sticker, its herbarium label and a date stamp.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .specimenCard()
                    .padding(16.dp),
            ) {
                Box(Modifier.fillMaxWidth()) {
                    AnimatedContent(targetState = showPhoto, label = "photoToggle") { photo ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { showPhoto = !showPhoto },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (photo) {
                                MediaImage(container.media.file(c.photoFile), Modifier.fillMaxSize())
                            } else {
                                // A small fixed tilt per capture, like a specimen mounted by hand.
                                val tilt = remember(c.id) { Random(c.id.hashCode()).nextFloat() * 8f - 4f }
                                StickerImage(
                                    c,
                                    Modifier
                                        .fillMaxSize()
                                        .padding(36.dp)
                                        .graphicsLayer { rotationZ = tilt }
                                        .holographic(0.32f, 3000),
                                )
                            }
                        }
                    }
                    if (!showPhoto) {
                        TapeStrip(Modifier.align(Alignment.TopStart).padding(start = 40.dp, top = 40.dp), angle = -30f)
                        TapeStrip(Modifier.align(Alignment.TopEnd).padding(end = 84.dp, top = 32.dp), angle = 24f)
                    }
                    DateStamp(c.createdAt, Modifier.align(Alignment.TopEnd), size = 62.dp)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    RecognitionLine(
                        c,
                        modifier = Modifier.weight(0.38f).padding(end = 12.dp),
                    ) { scope.launch { container.recognition.retry(c.id) } }
                    HerbariumLabel(
                        head = SpecimenCanvas.labelHead(speciesNumber, category),
                        name = c.displayName,
                        sci = c.nameSci?.takeIf { it.isNotBlank() },
                        rows = listOfNotNull(
                            tr("地点", "Place") to (c.placeName?.takeIf { it.isNotBlank() } ?: tr("未填写", "Not set")),
                            tr("时间", "Time") to labelTime(c.createdAt),
                            tr("坐标", "Coords") to (SpecimenCanvas.coordinates(c.latitude, c.longitude) ?: tr("没有位置信息", "No location")),
                            encounter?.let { tr("遇见", "Seen") to tr("第 $it 次", "#$it") },
                        ),
                        onRename = { editingName = true },
                        onEditNotes = { editingNotes = true },
                        modifier = Modifier.weight(0.62f),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Hairline(color = Divider)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editingDescription = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        c.description ?: tr("还没有简介，点这里写一句，或者让 AI 帮你写", "No description yet. Tap to write one, or let AI do it"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (c.description != null) Pine else Soft,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = tr("编辑简介", "Edit description"),
                        tint = Soft,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp).size(14.dp),
                    )
                }
                c.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Pine,
                        modifier = Modifier.fillMaxWidth().clickable { editingNotes = true }.padding(vertical = 4.dp),
                    )
                }
                val alternatives = c.alternatives?.split('|')?.filter { it.isNotBlank() }.orEmpty()
                if (alternatives.isNotEmpty() && c.status == RecognitionStatus.DONE) {
                    Spacer(Modifier.height(10.dp))
                    Text(tr("也可能是：", "Could also be:"), style = MaterialTheme.typography.labelMedium, color = Soft)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            }
            if (siblings.size > 1) {
                Spacer(Modifier.height(16.dp))
                Timeline(siblings, currentId = c.id, onOpen = { if (it != c.id) onOpenCapture(it) })
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    reframePhoto?.let { photo ->
        CutoutDialog(
            photo = photo,
            onDismiss = { reframePhoto = null },
            onDone = { sticker ->
                reframePhoto = null
                val id = c?.id ?: return@CutoutDialog
                scope.launch {
                    container.captures.replaceSticker(id, sticker)
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar(tr("贴纸已更新", "Sticker updated"), actionLabel = tr("重新识别", "Identify again"))
                    if (result == SnackbarResult.ActionPerformed) container.recognition.retry(id)
                }
            },
        )
    }
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

/** Category, recognition status and retry, stacked in the narrow space beside the label. */
@Composable
private fun RecognitionLine(capture: CaptureEntity, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    val category = Category.fromKey(capture.category)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(category.tint, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(category.display, style = MaterialTheme.typography.labelMedium, color = Pine)
        }
        val status = when (capture.status) {
            RecognitionStatus.DONE -> capture.confidence?.let { tr("把握 ${(it * 100).toInt()}%", "${(it * 100).toInt()}% sure") }
            RecognitionStatus.MANUAL -> tr("手动命名", "Named by hand")
            RecognitionStatus.PENDING, RecognitionStatus.RUNNING -> capture.errorMessage ?: tr("等待识别", "Waiting")
            RecognitionStatus.FAILED -> capture.errorMessage ?: tr("识别失败", "Not recognized")
        }
        status?.let { Text(it, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal), color = Soft, maxLines = 4) }
        if (capture.status == RecognitionStatus.FAILED || capture.status == RecognitionStatus.DONE ||
            (capture.status == RecognitionStatus.PENDING && capture.errorMessage != null)
        ) {
            IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Refresh, contentDescription = tr("重新识别", "Recognize again"), tint = Soft, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** "2026.10.11 15:20" for the label's mono time row. */
private fun labelTime(millis: Long): String =
    java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.ENGLISH).format(java.util.Date(millis))

/**
 * The herbarium label: mono catalogue line, spaced name, mono scientific name,
 * a dashed rule, then label/value rows. Tapping the names renames; tapping the
 * rows edits place and notes.
 */
@Composable
private fun HerbariumLabel(
    head: String,
    name: String,
    sci: String?,
    rows: List<Pair<String, String>>,
    onRename: () -> Unit,
    onEditNotes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(Color.White)
            .border(1.dp, LabelBorder)
            .padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        Text(head, style = MonoStyle, color = Soft)
        Row(
            Modifier.clickable(onClick = onRename),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.4.sp,
                lineHeight = 26.sp,
                color = Pine,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.Outlined.Edit, contentDescription = tr("改名字", "Rename"), tint = Soft, modifier = Modifier.padding(start = 4.dp).size(14.dp))
        }
        Text(
            sci ?: tr("还没有英文名 / 学名", "No English name yet"),
            modifier = Modifier.clickable(onClick = onRename),
            style = if (sci != null) MonoStyle else MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
            color = Soft,
        )
        Spacer(Modifier.height(5.dp))
        Canvas(Modifier.fillMaxWidth().height(1.dp)) {
            drawLine(
                DashColor,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
            )
        }
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth().clickable(onClick = onEditNotes)) {
            rows.forEachIndexed { i, (label, value) ->
                Row(Modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
                        color = Soft,
                        modifier = Modifier.width(if (Lang.english) 44.dp else 30.dp),
                    )
                    Text(value, style = MonoStyle, color = Pine, modifier = Modifier.weight(1f))
                    if (i == 0) {
                        Icon(Icons.Outlined.Edit, contentDescription = tr("编辑", "Edit"), tint = Soft, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

private val DashColor = Color(0xFFC4CFC6)

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
                    textStyle = LocalTextStyle.current.copy(fontFamily = MonoFont),
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
                        FilterPill(option.display, selected = option == category, tint = option.tint) {
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
                    ) { Text(tr("让 AI 写一句", "Let AI write one")) }
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
