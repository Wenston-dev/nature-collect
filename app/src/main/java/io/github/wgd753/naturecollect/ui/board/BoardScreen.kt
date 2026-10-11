package io.github.wgd753.naturecollect.ui.board

import io.github.wgd753.naturecollect.ui.Mascot
import io.github.wgd753.naturecollect.R
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiNature
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FlipToFront
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.wgd753.naturecollect.export.Gallery
import io.github.wgd753.naturecollect.ui.ColorPicker
import io.github.wgd753.naturecollect.ui.ColorSwatchButton
import io.github.wgd753.naturecollect.ui.HeaderIconButton
import io.github.wgd753.naturecollect.ui.QuickColors
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.systemBarsPadding
import coil3.compose.AsyncImage
import io.github.wgd753.naturecollect.ui.camera.loadBitmap
import io.github.wgd753.naturecollect.ui.cutout.CutoutDialog
import io.github.wgd753.naturecollect.ui.theme.Paper
import java.util.UUID
import io.github.wgd753.naturecollect.data.db.BoardEntity
import io.github.wgd753.naturecollect.data.db.BoardItemEntity
import io.github.wgd753.naturecollect.data.db.BoardItemType
import io.github.wgd753.naturecollect.data.db.CaptureEntity
import io.github.wgd753.naturecollect.ui.EmptyState
import io.github.wgd753.naturecollect.ui.FilterPill
import io.github.wgd753.naturecollect.ui.PageHeader
import io.github.wgd753.naturecollect.ui.StickerImage
import io.github.wgd753.naturecollect.ui.rememberContainer
import io.github.wgd753.naturecollect.ui.tr
import io.github.wgd753.naturecollect.ui.theme.Card
import io.github.wgd753.naturecollect.ui.theme.InkSoft
import io.github.wgd753.naturecollect.ui.theme.HandFont
import io.github.wgd753.naturecollect.ui.theme.HandInk
import io.github.wgd753.naturecollect.ui.paperGrain
import io.github.wgd753.naturecollect.ui.washFor
import io.github.wgd753.naturecollect.ui.watercolorWash
import io.github.wgd753.naturecollect.ui.tint
import io.github.wgd753.naturecollect.ui.collection.rememberStickerTint
import io.github.wgd753.naturecollect.data.Category
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

/** Sticker width on the board at scale 1, as a fraction of the board width. */
private const val STICKER_BASE = 0.3f

private enum class Tool { MOVE, PEN }

private enum class Sheet { STICKERS, BACKGROUND }

@Composable
fun BoardScreen() {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val boards by container.boards.observeBoards().collectAsState(initial = emptyList())
    val captures by container.captures.observeAll().collectAsState(initial = emptyList())
    var selectedBoardId by rememberSaveable { mutableStateOf<String?>(null) }
    val board = boards.firstOrNull { it.id == selectedBoardId } ?: boards.firstOrNull()
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { container.boards.ensureDefaultBoard() }
    if (board == null) return

    val editor: @Composable (Boolean) -> Unit = { isFullscreen ->
        BoardEditor(
            board = board,
            boards = boards,
            captures = captures,
            fullscreen = isFullscreen,
            onToggleFullscreen = { fullscreen = !fullscreen },
            onSelectBoard = { selectedBoardId = it },
            onNewBoard = { name -> scope.launch { selectedBoardId = container.boards.createBoard(name).id } },
            onBoardChange = { scope.launch { container.boards.updateBoard(it) } },
            onDeleteBoard = {
                scope.launch {
                    container.boards.deleteBoard(board)
                    selectedBoardId = null
                }
            },
        )
    }
    if (!fullscreen) editor(false)
    if (fullscreen) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Box(Modifier.fillMaxSize().background(Paper).systemBarsPadding()) { editor(true) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun BoardEditor(
    board: BoardEntity,
    boards: List<BoardEntity>,
    captures: List<CaptureEntity>,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onSelectBoard: (String) -> Unit,
    onNewBoard: (String) -> Unit,
    onBoardChange: (BoardEntity) -> Unit,
    onDeleteBoard: () -> Unit,
) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by remember(board.id) { container.boards.observeItems(board.id) }.collectAsState(initial = emptyList())
    val captureById = remember(captures) { captures.associateBy { it.id } }
    var selectedItemId by remember(board.id) { mutableStateOf<String?>(null) }
    var tool by remember { mutableStateOf(Tool.MOVE) }
    var penColor by remember { mutableLongStateOf(HandInk.toArgb().toLong() and 0xFFFFFFFFL) }
    var penWidth by remember { mutableFloatStateOf(4f) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var showTextDialog by remember { mutableStateOf(false) }
    var importPhoto by remember { mutableStateOf<Bitmap?>(null) }
    val exportLayer = rememberGraphicsLayer()
    val selectedItem = items.firstOrNull { it.id == selectedItemId }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) { loadBitmap(context, uri, maxSide = 1600) }
                if (bitmap != null) importPhoto = bitmap else Toast.makeText(context, tr("这张图片打不开", "Couldn't open this image"), Toast.LENGTH_SHORT).show()
            }
        }
    }
    BackHandler(enabled = tool == Tool.PEN || selectedItemId != null) {
        tool = Tool.MOVE
        selectedItemId = null
    }

    fun addSticker(capture: CaptureEntity) {
        scope.launch {
            val item = container.boards.addSticker(
                board.id,
                capture.id,
                x = 0.3f + Random.nextFloat() * 0.4f,
                y = 0.3f + Random.nextFloat() * 0.4f,
                rotation = Random.nextFloat() * 16f - 8f,
            )
            selectedItemId = item.id
            tool = Tool.MOVE
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Header: the board's name doubles as the board switcher.
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoardSwitcher(board, boards, onSelectBoard, onNewBoard, onRename = { onBoardChange(board.copy(name = it)) }, onDelete = onDeleteBoard)
            Spacer(Modifier.weight(1f))
            HeaderIconButton(if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, if (fullscreen) tr("退出全屏", "Exit full screen") else tr("全屏", "Full screen"), onClick = onToggleFullscreen)
            HeaderIconButton(Icons.Outlined.FileDownload, tr("保存到相册", "Save to gallery")) {
                selectedItemId = null
                scope.launch {
                    val bitmap = exportLayer.toImageBitmap().asAndroidBitmap()
                    val where = withContext(Dispatchers.IO) { Gallery.save(context, bitmap, board.name) }
                    Toast.makeText(context, tr("已保存到$where", "Saved to $where"), Toast.LENGTH_SHORT).show()
                }
            }
        }

        // The board itself.
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (fullscreen) 8.dp else 16.dp), contentAlignment = Alignment.Center) {
            BoxWithConstraints(
                Modifier
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                    .drawWithContent {
                        exportLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(exportLayer)
                    }
                    .background(Color(board.backgroundColor))
                    .paperGrain()
                    .drawBehind { drawPattern(board.pattern) },
            ) {
                val widthPx = constraints.maxWidth.toFloat()
                val heightPx = constraints.maxHeight.toFloat()
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(board.id) { detectTapGestures { selectedItemId = null } },
                )
                items.filter { it.type == BoardItemType.DRAWING }.forEach { drawing ->
                    DrawingItem(drawing, widthPx, heightPx)
                }
                items.filter { it.type != BoardItemType.DRAWING }.forEach { item ->
                    key(item.id) {
                        TransformableItem(
                            item = item,
                            capture = item.captureId?.let { captureById[it] },
                            boardWidth = widthPx,
                            boardHeight = heightPx,
                            selected = item.id == selectedItemId,
                            enabled = tool == Tool.MOVE,
                            onSelect = { selectedItemId = item.id },
                            onCommit = { scope.launch { container.boards.updateTransform(it) } },
                        )
                    }
                }
                if (tool == Tool.PEN) {
                    PenLayer(widthPx, heightPx, Color(penColor), penWidth) { pathData ->
                        scope.launch { container.boards.addDrawing(board.id, pathData, penColor) }
                    }
                }
                if (items.isEmpty() && tool == Tool.MOVE) {
                    Mascot(
                        R.drawable.art_mascot_sleep,
                        Modifier.align(Alignment.Center).size(140.dp).graphicsLayer { alpha = 0.85f },
                    )
                }
            }
        }

        // One bar at a time: tools, the selected item's actions, or the pen's settings.
        val mode = when {
            tool == Tool.PEN -> 2
            selectedItem != null -> 1
            else -> 0
        }
        AnimatedContent(
            targetState = mode,
            transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { it / 2 } + fadeOut()) },
            label = "boardBar",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        ) { m ->
            when (m) {
                2 -> Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ColorSwatchButton(penColor, tr("画笔颜色", "Pen color"), onPick = { penColor = it })
                    Slider(
                        value = penWidth,
                        onValueChange = { penWidth = it },
                        valueRange = 2f..16f,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    )
                    IconButton(onClick = {
                        items.lastOrNull { it.type == BoardItemType.DRAWING }?.let { scope.launch { container.boards.deleteItem(it.id) } }
                    }) { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = tr("撤销一笔", "Undo stroke"), tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = { tool = Tool.MOVE }) {
                        Icon(Icons.Outlined.Check, contentDescription = tr("完成", "Done"), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                1 -> Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val item = selectedItem
                    if (item?.type == BoardItemType.TEXT) {
                        ColorSwatchButton(item.color, tr("文字颜色", "Text color"), onPick = { c -> scope.launch { container.boards.setColor(item, c) } })
                    }
                    IconButton(onClick = { item?.let { scope.launch { container.boards.bringToFront(it) } } }) {
                        Icon(Icons.Outlined.FlipToFront, contentDescription = tr("放到最上层", "Bring to front"), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        item?.let { scope.launch { container.boards.deleteItem(it.id) } }
                        selectedItemId = null
                    }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = tr("删除", "Delete"), tint = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { selectedItemId = null }) {
                        Icon(Icons.Outlined.Check, contentDescription = tr("完成", "Done"), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                else -> Row(
                    Modifier.fillMaxWidth().padding(6.dp),
                ) {
                    BarTool(Icons.Outlined.EmojiNature, tr("贴纸", "Stickers")) { sheet = Sheet.STICKERS }
                    BarTool(Icons.Outlined.TextFields, tr("文字", "Text")) { showTextDialog = true }
                    BarTool(Icons.Outlined.Draw, tr("画笔", "Pen")) {
                        tool = Tool.PEN
                        selectedItemId = null
                    }
                    BarTool(Icons.Outlined.Palette, tr("背景", "Background")) { sheet = Sheet.BACKGROUND }
                }
            }
        }
    }

    if (sheet != null) {
        ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Paper) {
            when (sheet) {
                Sheet.STICKERS -> LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 460.dp),
                ) {
                    item(key = "import") {
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                .clickable {
                                    sheet = null
                                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = tr("从相册导入", "Import from gallery"), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    items(captures, key = { it.id }) { capture ->
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    sheet = null
                                    addSticker(capture)
                                }
                                .padding(6.dp),
                        ) { StickerImage(capture, Modifier.fillMaxSize()) }
                    }
                }
                Sheet.BACKGROUND -> Column(
                    Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(tr("空白", "Blank"), tr("点阵", "Dots"), tr("方格", "Grid")).forEachIndexed { index, label ->
                            FilterPill(label, selected = board.pattern == index) { onBoardChange(board.copy(pattern = index)) }
                        }
                    }
                    ColorPicker(board.backgroundColor, onChange = { onBoardChange(board.copy(backgroundColor = it)) })
                }
                null -> Unit
            }
        }
    }

    importPhoto?.let { photo ->
        CutoutDialog(photo = photo, onDismiss = { importPhoto = null }) { sticker ->
            importPhoto = null
            scope.launch {
                val name = "board-${UUID.randomUUID()}.png"
                withContext(Dispatchers.IO) { container.media.savePng(name, sticker) }
                selectedItemId = container.boards.addImage(board.id, name, sticker.width, sticker.height).id
                tool = Tool.MOVE
            }
        }
    }

    if (showTextDialog) {
        TextDialog(onDismiss = { showTextDialog = false }) { text, color, font, scale ->
            showTextDialog = false
            scope.launch {
                selectedItemId = container.boards.addText(board.id, text, color, font, scale).id
                tool = Tool.MOVE
            }
        }
    }
}

@Composable
private fun RowScope.BarTool(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** Board name with a menu: switch boards, add, rename or delete. */
@Composable
private fun BoardSwitcher(
    board: BoardEntity,
    boards: List<BoardEntity>,
    onSelect: (String) -> Unit,
    onNew: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf<String?>(null) } // "new" or "rename"
    var confirmDelete by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).clickable { open = true }.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(board.name, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
            Icon(Icons.Outlined.ExpandMore, contentDescription = tr("切换画板", "Switch board"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            boards.forEach { b ->
                DropdownMenuItem(
                    text = { Text(b.name, fontWeight = if (b.id == board.id) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { if (b.id == board.id) Icon(Icons.Outlined.Check, contentDescription = null) },
                    onClick = {
                        open = false
                        onSelect(b.id)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text(tr("新画板", "New board")) }, leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) }, onClick = {
                open = false
                naming = "new"
            })
            DropdownMenuItem(text = { Text(tr("重命名", "Rename")) }, leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) }, onClick = {
                open = false
                naming = "rename"
            })
            if (boards.size > 1) {
                DropdownMenuItem(
                    text = { Text(tr("删除画板", "Delete board"), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        open = false
                        confirmDelete = true
                    },
                )
            }
        }
    }
    naming?.let { mode ->
        NameDialog(
            title = if (mode == "new") tr("新画板", "New board") else tr("重命名", "Rename"),
            initial = if (mode == "new") "" else board.name,
            onDismiss = { naming = null },
        ) { name ->
            naming = null
            if (mode == "new") onNew(name) else onRename(name)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("删除「${board.name}」？", "Delete \"${board.name}\"?")) },
            text = { Text(tr("贴纸本身还在图鉴里。", "Stickers stay in your collection.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(tr("删除", "Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("取消", "Cancel")) } },
        )
    }
}

/** Text fonts, stored by key on the item; "hand" (and anything older) is the handwriting face. */
private fun fontFor(key: String?): FontFamily = when (key) {
    "round" -> FontFamily.SansSerif
    "serif" -> FontFamily.Serif
    else -> HandFont
}

/** The handwriting face has a single weight, so only the others get bold. */
private fun weightFor(key: String?): FontWeight = when (key) {
    "round", "serif" -> FontWeight.Bold
    else -> FontWeight.Normal
}

/**
 * A sticker or text that can be dragged, pinched and rotated. The change is
 * kept locally while the fingers move and saved when they lift.
 */
@Composable
private fun TransformableItem(
    item: BoardItemEntity,
    capture: CaptureEntity?,
    boardWidth: Float,
    boardHeight: Float,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onCommit: (BoardItemEntity) -> Unit,
) {
    if (item.type == BoardItemType.STICKER && capture == null) return
    val container = rememberContainer()
    var local by remember(item.id) { mutableStateOf(item) }
    var dragging by remember { mutableStateOf(false) }
    // Follow database changes, but never yank the item out from under a finger.
    LaunchedEffect(item.x, item.y, item.scale, item.rotation) { if (!dragging) local = item }
    val latest by rememberUpdatedState(local)
    val pop = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f)) }
    val density = LocalDensity.current

    val baseWidthPx = boardWidth * STICKER_BASE
    val aspect = when {
        capture != null -> capture.stickerHeight.toFloat() / capture.stickerWidth
        item.type == BoardItemType.IMAGE -> item.pathData?.split(',')?.let { parts ->
            val w = parts.getOrNull(0)?.toFloatOrNull()
            val h = parts.getOrNull(1)?.toFloatOrNull()
            if (w != null && h != null && w > 0f) h / w else null
        } ?: 1f
        else -> 0.5f
    }
    val widthDp = with(density) { baseWidthPx.toDp() }

    Box(
        Modifier
            .offset { IntOffset((local.x * boardWidth).roundToInt(), (local.y * boardHeight).roundToInt()) }
            .graphicsLayer {
                // Offset puts the top-left at (x, y); shift so (x, y) is the centre.
                translationX = -size.width / 2
                translationY = -size.height / 2
                scaleX = local.scale * pop.value
                scaleY = local.scale * pop.value
                rotationZ = local.rotation
            }
            .then(
                if (!enabled) {
                    Modifier
                } else {
                    Modifier.pointerInput(item.id) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            dragging = true
                            onSelect()
                            do {
                                val event = awaitPointerEvent()
                                val zoom = event.calculateZoom()
                                val rotation = event.calculateRotation()
                                val pan = event.calculatePan()
                                if (zoom != 1f || rotation != 0f || pan != Offset.Zero) {
                                    val current = latest
                                    // Pan arrives in the item's rotated/scaled frame; map it back to the board.
                                    val rad = Math.toRadians(current.rotation.toDouble())
                                    val cos = kotlin.math.cos(rad).toFloat()
                                    val sin = kotlin.math.sin(rad).toFloat()
                                    val dx = (pan.x * cos - pan.y * sin) * current.scale
                                    val dy = (pan.x * sin + pan.y * cos) * current.scale
                                    local = current.copy(
                                        x = (current.x + dx / boardWidth).coerceIn(-0.1f, 1.1f),
                                        y = (current.y + dy / boardHeight).coerceIn(-0.1f, 1.1f),
                                        scale = (current.scale * zoom).coerceIn(0.3f, 4f),
                                        rotation = current.rotation + rotation,
                                    )
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } while (event.changes.any { it.pressed })
                            dragging = false
                            onCommit(latest)
                        }
                    }
                },
            ),
    ) {
        val outline = if (selected) {
            Modifier.border(1.5.dp, InkSoft, RoundedCornerShape(8.dp))
        } else {
            Modifier
        }
        when (item.type) {
            BoardItemType.STICKER -> {
                // Each sticker sits on a soft wash of its own colour; overlapping ones pool together.
                val wash = washFor(rememberStickerTint(capture!!, Category.fromKey(capture.category).tint))
                Box(
                    Modifier
                        .width(widthDp * 1.3f)
                        .height(widthDp * aspect * 1.3f)
                        .watercolorWash(wash, item.id.hashCode(), strength = 0.9f),
                    contentAlignment = Alignment.Center,
                ) {
                    StickerImage(
                        capture,
                        Modifier
                            .width(widthDp)
                            .height(widthDp * aspect)
                            .then(outline),
                    )
                }
            }
            BoardItemType.TEXT -> Text(
                text = item.text.orEmpty(),
                modifier = outline.padding(horizontal = 8.dp, vertical = 4.dp),
                color = Color(item.color),
                fontFamily = fontFor(item.pathData),
                fontWeight = weightFor(item.pathData),
                fontSize = 26.sp,
            )
            BoardItemType.IMAGE -> AsyncImage(
                model = container.media.file(item.text.orEmpty()),
                contentDescription = null,
                modifier = Modifier
                    .width(widthDp)
                    .height(widthDp * aspect)
                    .then(outline),
            )
            BoardItemType.DRAWING -> Unit
        }
    }
}

@Composable
private fun DrawingItem(item: BoardItemEntity, boardWidth: Float, boardHeight: Float) {
    val (width, points) = remember(item.pathData) { decodeStroke(item.pathData) }
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawStroke(points.map { Offset(it.x * boardWidth, it.y * boardHeight) }, Color(item.color), width) },
    )
}

/** Captures a freehand stroke and hands it back as board-relative points. */
@Composable
private fun PenLayer(boardWidth: Float, boardHeight: Float, color: Color, width: Float, onStroke: (String) -> Unit) {
    val current = remember { mutableStateListOf<Offset>() }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(color) {
                detectDragGestures(
                    onDragStart = { current.clear(); current.add(it) },
                    onDrag = { change, _ -> change.consume(); current.add(change.position) },
                    onDragEnd = {
                        if (current.size > 1) {
                            val points = current.joinToString(";") { "%.4f,%.4f".format(java.util.Locale.US, it.x / boardWidth, it.y / boardHeight) }
                            onStroke("w=%.1f#".format(java.util.Locale.US, width) + points)
                        }
                        current.clear()
                    },
                    onDragCancel = { current.clear() },
                )
            }
            .drawBehind { drawStroke(current.toList(), color, width) },
    )
}

/** "w=6.0#x,y;x,y…" (older strokes have no width prefix and default to 4dp). */
private fun decodeStroke(data: String?): Pair<Float, List<Offset>> {
    val raw = data.orEmpty()
    val hash = raw.indexOf('#')
    if (raw.startsWith("w=") && hash > 0) {
        val width = raw.substring(2, hash).toFloatOrNull() ?: 4f
        return width to decodePath(raw.substring(hash + 1))
    }
    return 4f to decodePath(raw)
}

private fun decodePath(data: String?): List<Offset> =
    data.orEmpty().split(';').mapNotNull { pair ->
        val parts = pair.split(',')
        if (parts.size != 2) null else Offset(parts[0].toFloatOrNull() ?: return@mapNotNull null, parts[1].toFloatOrNull() ?: return@mapNotNull null)
    }

private fun DrawScope.drawStroke(points: List<Offset>, color: Color, widthDp: Float = 4f) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            // Smooth the stroke through midpoints.
            val prev = points[i - 1]
            val cur = points[i]
            quadraticTo(prev.x, prev.y, (prev.x + cur.x) / 2, (prev.y + cur.y) / 2)
        }
        lineTo(points.last().x, points.last().y)
    }
    drawPath(path, color, style = Stroke(width = widthDp.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawPattern(pattern: Int) {
    val step = 22.dp.toPx()
    val color = Color(0x22000000)
    when (pattern) {
        1 -> {
            var y = step
            while (y < size.height) {
                var x = step
                while (x < size.width) {
                    drawCircle(color, radius = 1.6.dp.toPx(), center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        2 -> {
            var x = step
            while (x < size.width) {
                drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }
    }
}

@Composable
private fun TextDialog(onDismiss: () -> Unit, onAdd: (String, Long, String, Float) -> Unit) {
    var text by remember { mutableStateOf("") }
    var color by remember { mutableLongStateOf(HandInk.toArgb().toLong() and 0xFFFFFFFFL) }
    var font by remember { mutableStateOf("hand") }
    var size by remember { mutableFloatStateOf(1f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("文字", "Text")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { Text(tr("写点什么", "Write something")) })
                Text(
                    text.ifBlank { tr("预览", "Preview") },
                    color = Color(color),
                    fontFamily = fontFor(font),
                    fontWeight = weightFor(font),
                    fontSize = (22 * size).sp,
                    maxLines = 2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("hand" to tr("手写", "Hand"), "round" to tr("圆体", "Round"), "serif" to tr("衬线", "Serif")).forEach { (key, label) ->
                        FilterPill(label, selected = font == key) { font = key }
                    }
                }
                Slider(value = size, onValueChange = { size = it }, valueRange = 0.6f..2.2f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorSwatchButton(color, tr("文字颜色", "Text color"), onPick = { color = it }, size = 30.dp)
                    QuickColors.take(8).forEach { c ->
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(if (color == c) 3.dp else 1.dp, if (color == c) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.12f), CircleShape)
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(text.trim(), color, font, size) }, enabled = text.isNotBlank()) { Text(tr("贴上去", "Add")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消", "Cancel")) } },
    )
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text(tr("画板名字", "Board name")) }) },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text(tr("好", "OK")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消", "Cancel")) } },
    )
}
