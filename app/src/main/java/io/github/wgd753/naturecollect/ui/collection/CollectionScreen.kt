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
import io.github.wgd753.naturecollect.ui.Hairline
import io.github.wgd753.naturecollect.ui.achievements.achievementSummary
import io.github.wgd753.naturecollect.ui.rememberContainer
import io.github.wgd753.naturecollect.ui.specimenCard
import io.github.wgd753.naturecollect.ui.theme.Card
import io.github.wgd753.naturecollect.ui.theme.Divider
import io.github.wgd753.naturecollect.ui.theme.MonoFont
import io.github.wgd753.naturecollect.ui.theme.MonoStyle
import io.github.wgd753.naturecollect.ui.theme.Pine
import io.github.wgd753.naturecollect.ui.theme.Rust
import io.github.wgd753.naturecollect.ui.theme.Soft
import io.github.wgd753.naturecollect.ui.theme.spaced
import io.github.wgd753.naturecollect.ui.tr
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
    // Catalogue numbers follow collection order: the first species found is No.001.
    val catalogue = remember(groups) {
        groups.sortedBy { it.firstSeen }.withIndex().associate { (i, g) -> g.key to i + 1 }
    }

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
                    val poster = PosterRenderer(container.media, io.github.wgd753.naturecollect.export.SpecimenCanvas.monoTypeface(context), io.github.wgd753.naturecollect.data.Settings.showStamp(context)).render(shown, title)
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
                PageHeader(spaced(tr("图鉴", "Collection"))) {
                    if (exporting) {
                        Box(Modifier.padding(start = 8.dp).size(42.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Pine)
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                            .specimenCard()
                            .clickable(onClick = onOpenSettings)
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            tr("还没填 DeepSeek API Key，拍到的东西不会自动识别。点这里去设置", "No DeepSeek API key yet, so photos won't be identified. Tap to set it up"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Pine,
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
                    number = catalogue[group.key],
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
            Icon(Icons.Outlined.Close, contentDescription = tr("取消选择", "Clear selection"), tint = Pine)
        }
        Text(tr("已选 $count 个", "$count selected"), style = MaterialTheme.typography.titleLarge, color = Pine)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onSelectAll) { Text(tr("全选", "Select all"), color = Pine) }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(2.dp))
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

/** Three stat columns between two hairlines, then a quiet achievements link. */
@Composable
private fun StatsLine(
    speciesCount: Int,
    total: Int,
    newThisMonth: Int,
    achievementsEarned: Int,
    achievementsTotal: Int,
    onOpenAchievements: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp)) {
        Hairline()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StatColumn(tr("种", "species")) {
                AnimatedContent(
                    targetState = speciesCount,
                    transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                    label = "speciesCount",
                ) { n -> StatNumber("$n") }
            }
            StatRule()
            StatColumn(tr("次遇见", "sightings")) { StatNumber("$total") }
            StatRule()
            StatColumn(tr("本月新种", "new this month")) { StatNumber("+$newThisMonth") }
        }
        Hairline()
        Row(
            Modifier
                .clickable(onClick = onOpenAchievements)
                .padding(top = 8.dp, bottom = 2.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr("成就", "Achievements"), color = Soft, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(6.dp))
            Text("$achievementsEarned/$achievementsTotal", color = Soft, style = MonoStyle)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Soft,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatColumn(label: String, number: @Composable () -> Unit) {
    Column(Modifier.weight(1f).padding(start = 10.dp, top = 6.dp, bottom = 5.dp)) {
        number()
        Text(label, color = Soft, fontSize = 10.sp, letterSpacing = 0.6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatRule() {
    Box(Modifier.width(0.5.dp).height(30.dp).background(Pine.copy(alpha = 0.2f)))
}

@Composable
private fun StatNumber(value: String) {
    Text(value, color = Pine, fontFamily = MonoFont, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 23.sp)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeciesCell(
    group: SpeciesGroup,
    number: Int?,
    selecting: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val appear = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
    val press by animateFloatAsState(
        targetValue = if (selected) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "selectScale",
    )
    val cover = group.cover
    val recognizing = cover.status == RecognitionStatus.PENDING || cover.status == RecognitionStatus.RUNNING

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .specimenCard()
            .then(if (selected) Modifier.border(1.5.dp, Pine) else Modifier)
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
                .aspectRatio(1.1f),
        ) {
            StickerImage(
                cover,
                Modifier
                    .fillMaxSize()
                    .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 8.dp)
                    .graphicsLayer {
                        scaleX = appear.value
                        scaleY = appear.value
                    },
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = selecting,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 8.dp),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (selected) Rust else Card)
                        .border(1.dp, if (selected) Rust else Soft, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Outlined.Check, contentDescription = tr("已选", "Selected"), tint = Card, modifier = Modifier.size(13.dp))
                }
            }
        }
        Hairline(Modifier.padding(horizontal = 8.dp), color = Divider)
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (recognizing && cover.nameZh == null) tr("识别中…", "Identifying…") else group.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
                color = if (recognizing && cover.nameZh == null) Soft else Pine,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val meta = listOfNotNull(number?.let { "No.%03d".format(it) }, "×${group.captures.size}").joinToString(" · ")
            Text(
                text = meta,
                style = MonoStyle.copy(fontSize = 9.sp, letterSpacing = 0.3.sp),
                color = Soft,
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
