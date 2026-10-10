package io.github.wgd753.naturecollect.ui.board

import android.graphics.Bitmap
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
import io.github.wgd753.naturecollect.ui.theme.Card
import io.github.wgd753.naturecollect.ui.theme.InkSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

private val BackgroundColors = listOf(0xFFF8F6F1, 0xFFE9E4DA, 0xFFFBE9E4, 0xFFE8F1E4, 0xFFE4EDF7, 0xFFEDE8F6)
private val InkColors = listOf(0xFF1F2FC8, 0xFF2E5E3B, 0xFFD2452F, 0xFF2B2A28, 0xFFE59A1F, 0xFFFFFFFF)

/** Sticker width on the board at scale 1, as a fraction of the board width. */
private const val STICKER_BASE = 0.3f

@Composable
fun BoardScreen() {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val boards by container.boards.observeBoards().collectAsState(initial = emptyList())
    val captures by container.captures.observeAll().collectAsState(initial = emptyList())
    var selectedBoardId by rememberSaveable { mutableStateOf<String?>(null) }
    val board = boards.firstOrNull { it.id == selectedBoardId } ?: boards.firstOrNull()

    var showNewBoard by remember { mutableStateOf(false) }
    var showBoardMenu by remember { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { container.boards.ensureDefaultBoard() }

    Column(Modifier.fillMaxSize()) {
        PageHeader("贴纸板")
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { showNewBoard = true },
                modifier = Modifier.clip(CircleShape).background(Card),
            ) { Icon(Icons.Outlined.Add, contentDescription = "新画板", tint = MaterialTheme.colorScheme.primary) }
            boards.forEach { b ->
                FilterPill(b.name, selected = b.id == board?.id) {
                    if (b.id == board?.id) showBoardMenu = true else selectedBoardId = b.id
                }
            }
        }
        if (board == null) return@Column
        if (!fullscreen) {
            BoardEditor(
                board,
                captures,
                onBoardChange = { scope.launch { container.boards.updateBoard(it) } },
                fullscreen = false,
                onToggleFullscreen = { fullscreen = true },
            )
        }
    }

    if (fullscreen && board != null) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Box(Modifier.fillMaxSize().background(Paper).systemBarsPadding()) {
                BoardEditor(
                    board,
                    captures,
                    onBoardChange = { scope.launch { container.boards.updateBoard(it) } },
                    fullscreen = true,
                    onToggleFullscreen = { fullscreen = false },
                )
            }
        }
    }

    if (showNewBoard) {
        NameDialog(title = "新画板", initial = "", onDismiss = { showNewBoard = false }) { name ->
            showNewBoard = false
            scope.launch { selectedBoardId = container.boards.createBoard(name).id }
        }
    }
    if (showBoardMenu && board != null) {
        BoardMenuDialog(
            board = board,
            canDelete = boards.size > 1,
            onDismiss = { showBoardMenu = false },
            onRename = { name ->
                showBoardMenu = false
                scope.launch { container.boards.updateBoard(board.copy(name = name)) }
            },
            onDelete = {
                showBoardMenu = false
                scope.launch {
                    container.boards.deleteBoard(board)
                    selectedBoardId = null
                }
            },
        )
    }
}

private enum class Tool { MOVE, PEN }

@Composable
private fun BoardEditor(
    board: BoardEntity,
    captures: List<CaptureEntity>,
    onBoardChange: (BoardEntity) -> Unit,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by remember(board.id) { container.boards.observeItems(board.id) }.collectAsState(initial = emptyList())
    val captureById = remember(captures) { captures.associateBy { it.id } }
    var selectedItemId by remember(board.id) { mutableStateOf<String?>(null) }
    var tool by remember { mutableStateOf(Tool.MOVE) }
    var penColor by remember { mutableStateOf(InkColors.first()) }
    var showTextDialog by remember { mutableStateOf(false) }
    val exportLayer = rememberGraphicsLayer()
    // In full screen the board gets the room; trays fold away until asked for.
    var showTray by remember(fullscreen) { mutableStateOf(!fullscreen) }
    var showBackground by remember(fullscreen) { mutableStateOf(!fullscreen) }
    var importPhoto by remember { mutableStateOf<Bitmap?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) { loadBitmap(context, uri, maxSide = 1600) }
                if (bitmap != null) importPhoto = bitmap else Toast.makeText(context, "这张图片打不开", Toast.LENGTH_SHORT).show()
            }
        }
    }
    BackHandler(enabled = tool == Tool.PEN) { tool = Tool.MOVE }

    Column(Modifier.fillMaxSize()) {
        if (fullscreen) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onToggleFullscreen) {
                    Icon(Icons.Outlined.FullscreenExit, contentDescription = "退出全屏", tint = MaterialTheme.colorScheme.primary)
                }
                Text(
                    board.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                FilterPill("背景", selected = showBackground) { showBackground = !showBackground }
                FilterPill("素材", selected = showTray) { showTray = !showTray }
            }
        }
        // Background colour and pattern.
        AnimatedVisibility(showBackground, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackgroundColors.forEach { color ->
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .border(if (board.backgroundColor == color) 3.dp else 1.dp, if (board.backgroundColor == color) MaterialTheme.colorScheme.primary else InkSoft, CircleShape)
                        .clickable { onBoardChange(board.copy(backgroundColor = color)) },
                )
            }
            Spacer(Modifier.width(8.dp))
            listOf("空白", "点阵", "方格").forEachIndexed { index, label ->
                FilterPill(label, selected = board.pattern == index) { onBoardChange(board.copy(pattern = index)) }
            }
        }
        }

        // Asset tray: every sticker collected so far, plus importing from the gallery.
        AnimatedVisibility(showTray, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column {
        if (!fullscreen) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("素材 ${captures.size}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text("点一下贴到画板上", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "import") {
                Column(
                    Modifier
                        .size(if (fullscreen) 60.dp else 72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .border(1.5.dp, InkSoft, RoundedCornerShape(16.dp))
                        .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("相册", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            items(captures, key = { it.id }) { capture ->
                Box(
                    Modifier
                        .size(if (fullscreen) 60.dp else 72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Card)
                        .clickable {
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
                        .padding(6.dp),
                ) { StickerImage(capture, Modifier.fillMaxSize()) }
            }
        }
        }
        }

        // The board itself.
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (fullscreen) 8.dp else 16.dp), contentAlignment = Alignment.Center) {
            BoxWithConstraints(
                Modifier
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(24.dp))
                    .drawWithContent {
                        exportLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(exportLayer)
                    }
                    .background(Color(board.backgroundColor))
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
                            onSelect = {
                                selectedItemId = item.id
                                scope.launch { container.boards.bringToFront(item) }
                            },
                            onCommit = { scope.launch { container.boards.updateTransform(it) } },
                        )
                    }
                }
                if (tool == Tool.PEN) {
                    PenLayer(widthPx, heightPx, Color(penColor)) { pathData ->
                        scope.launch { container.boards.addDrawing(board.id, pathData, penColor) }
                    }
                }
                if (items.isEmpty() && tool == Tool.MOVE) {
                    EmptyState("🧩", if (captures.isEmpty()) "先去拍几张贴纸吧" else "从上面挑贴纸贴上来\n拖动、双指缩放和旋转")
                }
            }
        }

        // Bottom tools.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                selectedItemId?.let { id -> scope.launch { container.boards.deleteItem(id) } }
                selectedItemId = null
            }, enabled = selectedItemId != null) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除选中",
                    tint = if (selectedItemId != null) MaterialTheme.colorScheme.error else InkSoft,
                )
            }
            Spacer(Modifier.weight(1f))
            ToolButton(selected = false, onClick = { showTextDialog = true }) {
                Text("Aa", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            ToolButton(selected = tool == Tool.PEN, onClick = {
                tool = if (tool == Tool.PEN) Tool.MOVE else Tool.PEN
                selectedItemId = null
            }) {
                Icon(Icons.Outlined.Draw, contentDescription = "画笔", tint = if (tool == Tool.PEN) Color.White else MaterialTheme.colorScheme.primary)
            }
            if (tool == Tool.PEN) {
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {
                    items.lastOrNull { it.type == BoardItemType.DRAWING }?.let { scope.launch { container.boards.deleteItem(it.id) } }
                }) { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "撤销一笔", tint = MaterialTheme.colorScheme.primary) }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleFullscreen) {
                Icon(
                    if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                    contentDescription = if (fullscreen) "退出全屏" else "全屏编辑",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = {
                selectedItemId = null
                scope.launch {
                    val bitmap = exportLayer.toImageBitmap().asAndroidBitmap()
                    val where = withContext(Dispatchers.IO) { io.github.wgd753.naturecollect.export.Gallery.save(context, bitmap, board.name) }
                    Toast.makeText(context, "已保存到$where", Toast.LENGTH_SHORT).show()
                }
            }) { Icon(Icons.Outlined.FileDownload, contentDescription = "导出图片", tint = MaterialTheme.colorScheme.primary) }
        }
        if (tool == Tool.PEN) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                InkColors.forEach { color ->
                    Box(
                        Modifier
                            .padding(horizontal = 6.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(if (penColor == color) 3.dp else 1.dp, if (penColor == color) MaterialTheme.colorScheme.primary else InkSoft, CircleShape)
                            .clickable { penColor = color },
                    )
                }
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
        TextDialog(onDismiss = { showTextDialog = false }) { text, color ->
            showTextDialog = false
            scope.launch {
                selectedItemId = container.boards.addText(board.id, text, color).id
                tool = Tool.MOVE
            }
        }
    }
}

@Composable
private fun ToolButton(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Card)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
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
            BoardItemType.STICKER -> StickerImage(
                capture!!,
                Modifier
                    .width(widthDp)
                    .height(widthDp * aspect)
                    .then(outline),
            )
            BoardItemType.TEXT -> Text(
                text = item.text.orEmpty(),
                modifier = outline.padding(horizontal = 8.dp, vertical = 4.dp),
                color = Color(item.color),
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.Bold,
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
    val points = remember(item.pathData) { decodePath(item.pathData) }
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawStroke(points.map { Offset(it.x * boardWidth, it.y * boardHeight) }, Color(item.color)) },
    )
}

/** Captures a freehand stroke and hands it back as board-relative points. */
@Composable
private fun PenLayer(boardWidth: Float, boardHeight: Float, color: Color, onStroke: (String) -> Unit) {
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
                            onStroke(current.joinToString(";") { "%.4f,%.4f".format(java.util.Locale.US, it.x / boardWidth, it.y / boardHeight) })
                        }
                        current.clear()
                    },
                    onDragCancel = { current.clear() },
                )
            }
            .drawBehind { drawStroke(current.toList(), color) },
    )
}

private fun decodePath(data: String?): List<Offset> =
    data.orEmpty().split(';').mapNotNull { pair ->
        val parts = pair.split(',')
        if (parts.size != 2) null else Offset(parts[0].toFloatOrNull() ?: return@mapNotNull null, parts[1].toFloatOrNull() ?: return@mapNotNull null)
    }

private fun DrawScope.drawStroke(points: List<Offset>, color: Color) {
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
    drawPath(path, color, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
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
private fun TextDialog(onDismiss: () -> Unit, onAdd: (String, Long) -> Unit) {
    var text by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(InkColors.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("写点什么") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { Text("例如：春天的公园") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InkColors.filter { it != 0xFFFFFFFF }.forEach { c ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(if (color == c) 3.dp else 0.dp, InkSoft, CircleShape)
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(text.trim(), color) }, enabled = text.isNotBlank()) { Text("贴上去") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text("画板名字") }) },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("好") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun BoardMenuDialog(
    board: BoardEntity,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(board.name) }
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.MoreHoriz, contentDescription = null) },
        title = { Text(if (confirm) "删除「${board.name}」？" else "画板设置") },
        text = {
            if (confirm) {
                Text("画板上的排版会删除，贴纸本身还在图鉴里。")
            } else {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("名字") })
            }
        },
        confirmButton = {
            if (confirm) {
                TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
            } else {
                TextButton(onClick = { onRename(name.trim()) }, enabled = name.isNotBlank()) { Text("保存") }
            }
        },
        dismissButton = {
            if (!confirm && canDelete) {
                TextButton(onClick = { confirm = true }) { Text("删除画板", color = MaterialTheme.colorScheme.error) }
            } else {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
