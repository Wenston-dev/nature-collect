package io.github.wgd753.naturecollect.ui.collection

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wgd753.naturecollect.R
import io.github.wgd753.naturecollect.data.Category
import io.github.wgd753.naturecollect.data.SpeciesGroup
import io.github.wgd753.naturecollect.data.db.RecognitionStatus
import io.github.wgd753.naturecollect.data.groupBySpecies
import io.github.wgd753.naturecollect.export.Gallery
import io.github.wgd753.naturecollect.export.PosterRenderer
import io.github.wgd753.naturecollect.ui.CategoryFilterRow
import io.github.wgd753.naturecollect.ui.EmptyState
import io.github.wgd753.naturecollect.ui.HeaderIconButton
import io.github.wgd753.naturecollect.ui.PageHeader
import io.github.wgd753.naturecollect.ui.StickerImage
import io.github.wgd753.naturecollect.ui.WatercolorWash
import io.github.wgd753.naturecollect.ui.achievements.achievementSummary
import io.github.wgd753.naturecollect.ui.rememberContainer
import io.github.wgd753.naturecollect.ui.theme.Forest
import io.github.wgd753.naturecollect.ui.theme.Paper
import io.github.wgd753.naturecollect.ui.theme.Sun
import io.github.wgd753.naturecollect.ui.theme.TextMuted
import io.github.wgd753.naturecollect.ui.theme.TitleFont
import io.github.wgd753.naturecollect.ui.tint
import io.github.wgd753.naturecollect.ui.tr
import io.github.wgd753.naturecollect.ui.washFor
import io.github.wgd753.naturecollect.ui.watercolorWash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

@Composable
fun CollectionScreen(
    onOpenCapture: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenAchievements: () -> Unit = {},
) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val captures by container.captures.observeAll().collectAsState(initial = null)
    var filter by rememberSaveable { mutableStateOf<Category?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val apiKey by container.settings.apiKey.collectAsState()

    val all = captures.orEmpty()
    val categories = Category.entries
    val groups = remember(all, categories) { all.groupBySpecies() }
    val shown = groups.filter { filter == null || it.category.key == filter?.key }
    val speciesCount = groups.count { it.cover.nameZh != null }
    val achievements = remember(all, categories) { achievementSummary(all) }
    val selecting = selected.isNotEmpty()
    val monthStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
    }
    val newThisMonth = groups.count { it.firstSeen >= monthStart && it.cover.nameZh != null }

    BackHandler(enabled = selecting) { selected = emptySet() }
    // Drop selections that no longer exist (e.g. after deleting).
    LaunchedEffect(groups) { selected = selected.filterTo(mutableSetOf()) { key -> groups.any { it.key == key } } }

    fun export() {
        if (exporting || shown.isEmpty()) return
        exporting = true
        scope.launch {
            val title = filter?.let { tr("我的${it.display}图鉴", "My ${it.display}") } ?: tr("我的自然图鉴", "My nature collection")
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val poster = PosterRenderer(container.media, io.github.wgd753.naturecollect.export.CanvasWash.titleTypeface(context)).render(shown, title)
                    try {
                        Gallery.save(context, poster, tr("图鉴", "collection"), jpeg = true)
                    } finally {
                        poster.recycle()
                    }
                }
            }
            exporting = false
            val message = result.fold({ tr("图鉴长图已保存到$it", "Saved to $it") }, { tr("导出失败：", "Export failed: ") + (it.message ?: it.javaClass.simpleName) })
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = selecting,
            transitionSpec = {
                (slideInVertically { -it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
            },
            label = "collectionHeader",
        ) { inSelection ->
            if (inSelection) {
                SelectionBar(
                    count = selected.size,
                    onClose = { selected = emptySet() },
                    onSelectAll = { selected = shown.map { it.key }.toSet() },
                    onDelete = { confirmDelete = true },
                )
            } else {
                PageHeader(tr("图鉴", "Collection")) {
                    if (exporting) {
                        Box(Modifier.padding(start = 8.dp).size(42.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Forest)
                        }
                    } else {
                        HeaderIconButton(Icons.Outlined.FileDownload, tr("导出图鉴长图", "Export poster"), enabled = shown.isNotEmpty()) { export() }
                    }
                    HeaderIconButton(Icons.Outlined.Settings, tr("设置", "Settings"), onClick = onOpenSettings)
                }
            }
        }
        if (captures != null && all.isEmpty()) {
            EmptyState("🌿", art = R.drawable.art_mascot_camera, message = tr("还没有收集\n去拍下第一只小鸟或一朵花吧", "Nothing here yet\nGo snap a bird or a flower"))
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 0.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                StatsLine(
                    speciesCount = speciesCount,
                    total = all.size,
                    newThisMonth = newThisMonth,
                    achievementsEarned = achievements.earned,
                    achievementsTotal = achievements.total,
                    onOpenAchievements = onOpenAchievements,
                )
            }
            if (apiKey == null) {
                item(key = "nokey", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .watercolorWash(Sun, seed = 41, strength = 0.7f)
                            .clickable(onClick = onOpenSettings)
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            tr("还没填 DeepSeek API Key，拍到的东西不会自动识别。点这里去设置", "No DeepSeek API key yet, so photos won't be identified. Tap to set it up"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Forest,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    CategoryFilterRow(
                        selected = filter,
                        onSelect = { filter = it },
                        available = groups.map { it.category.key }.toSet(),
                        onManage = onOpenCategories,
                        modifier = Modifier.padding(horizontal = 0.dp),
                    )
                }
            }
            items(shown, key = { it.key }) { group ->
                val isSelected = group.key in selected
                SpeciesCell(
                    group = group,
                    selecting = selecting,
                    selected = isSelected,
                    modifier = Modifier.animateItem(),
                    onClick = {
                        if (selecting) {
                            selected = if (isSelected) selected - group.key else selected + group.key
                        } else {
                            onOpenCapture(group.cover.id)
                        }
                    },
                    onLongClick = { selected = selected + group.key },
                )
            }
        }
    }

    if (confirmDelete) {
        val doomed = groups.filter { it.key in selected }
        val records = doomed.sumOf { it.captures.size }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("删除 ${doomed.size} 个条目？", "Delete ${doomed.size} entries?")) },
            text = { Text(tr("会一并删除这些条目下的 $records 条记录、照片和贴纸板上的贴纸，删除后无法恢复。", "This also deletes their $records records, photos and board stickers. It can't be undone.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val ids = doomed.flatMap { group -> group.captures.map { it.id } }
                    selected = emptySet()
                    scope.launch { container.captures.deleteAll(ids) }
                }) { Text(tr("删除", "Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("取消", "Cancel")) } },
        )
    }
}

@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = tr("取消选择", "Clear selection"), tint = Forest)
        }
        Text(tr("已选 $count 个", "$count selected"), style = MaterialTheme.typography.titleLarge, color = Forest)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onSelectAll) { Text(tr("全选", "Select all"), color = Forest) }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onDelete)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(tr("删除", "Delete"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** One line of big title-font numbers with small inline units, then a quiet achievements link. */
@Composable
private fun StatsLine(
    speciesCount: Int,
    total: Int,
    newThisMonth: Int,
    achievementsEarned: Int,
    achievementsTotal: Int,
    onOpenAchievements: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedContent(
                targetState = speciesCount,
                transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                label = "speciesCount",
            ) { n -> StatNumber("$n") }
            StatUnit(tr("种", "species"))
            Spacer(Modifier.width(18.dp))
            StatNumber("$total")
            StatUnit(tr("次遇见", "sightings"))
            Spacer(Modifier.width(18.dp))
            StatNumber("+$newThisMonth")
            StatUnit(tr("本月", "this month"))
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenAchievements)
                .padding(top = 6.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr("成就", "Achievements"), color = TextMuted, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(4.dp))
            Text("$achievementsEarned/$achievementsTotal", color = TextMuted, style = MaterialTheme.typography.labelMedium)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun StatNumber(value: String) {
    Text(value, color = Forest, fontFamily = TitleFont, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = 0.5.sp)
}

@Composable
private fun StatUnit(label: String) {
    Text(
        label,
        color = TextMuted,
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 3.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeciesCell(
    group: SpeciesGroup,
    selecting: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // A slight, stable tilt per species makes the grid feel hand-placed.
    val tilt = ((group.key.hashCode() % 7) - 3) * 1.5f
    val appear = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
    val press by animateFloatAsState(
        targetValue = if (selected) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "selectScale",
    )
    val cover = group.cover
    val recognizing = cover.status == RecognitionStatus.PENDING || cover.status == RecognitionStatus.RUNNING
    val wash = washFor(rememberStickerTint(cover, fallback = group.category.tint))

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            WatercolorWash(wash, seed = group.key.hashCode(), modifier = Modifier.fillMaxSize().padding(4.dp))
            StickerImage(
                cover,
                Modifier
                    .fillMaxSize()
                    .padding(18.dp)
                    .graphicsLayer {
                        rotationZ = if (selecting) 0f else tilt
                        scaleX = appear.value
                        scaleY = appear.value
                    },
            )
            if (selected) {
                // A thin ink ring around the wash marks the selection.
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                        .border(1.5.dp, Forest, RoundedCornerShape(28.dp)),
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = selecting,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (selected) Forest else Paper)
                        .border(1.5.dp, if (selected) Forest else TextMuted, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Outlined.Check, contentDescription = tr("已选", "Selected"), tint = Paper, modifier = Modifier.size(14.dp))
                }
            }
        }
        Row(
            Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = if (recognizing && cover.nameZh == null) tr("识别中…", "Identifying…") else group.name,
                fontFamily = TitleFont,
                fontSize = 15.sp,
                letterSpacing = 0.9.sp,
                color = if (recognizing && cover.nameZh == null) TextMuted else Forest,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (group.captures.size > 1) {
                Text(
                    text = "×${group.captures.size}",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(start = 5.dp, bottom = 1.dp),
                )
            }
        }
    }
}
