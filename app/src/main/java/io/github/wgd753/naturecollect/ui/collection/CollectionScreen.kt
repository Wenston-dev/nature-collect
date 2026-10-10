package io.github.wgd753.naturecollect.ui.collection

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.wgd753.naturecollect.data.Category
import io.github.wgd753.naturecollect.data.SpeciesGroup
import io.github.wgd753.naturecollect.data.db.RecognitionStatus
import io.github.wgd753.naturecollect.data.groupBySpecies
import io.github.wgd753.naturecollect.export.Gallery
import io.github.wgd753.naturecollect.export.PosterRenderer
import io.github.wgd753.naturecollect.ui.CategoryFilterRow
import io.github.wgd753.naturecollect.ui.EmptyState
import io.github.wgd753.naturecollect.ui.PageHeader
import io.github.wgd753.naturecollect.ui.StickerImage
import io.github.wgd753.naturecollect.ui.rememberContainer
import io.github.wgd753.naturecollect.ui.theme.Card
import io.github.wgd753.naturecollect.ui.tint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

@Composable
fun CollectionScreen(onOpenCapture: (String) -> Unit, onOpenSettings: () -> Unit) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val captures by container.captures.observeAll().collectAsState(initial = null)
    var filter by rememberSaveable { mutableStateOf<Category?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    val all = captures.orEmpty()
    val groups = remember(all) { all.groupBySpecies() }
    val shown = groups.filter { filter == null || it.category == filter }
    val speciesCount = groups.count { it.cover.nameZh != null }
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
            val title = filter?.let { "我的${it.label}图鉴" } ?: "我的自然图鉴"
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val poster = PosterRenderer(container.media).render(shown, title)
                    try {
                        Gallery.save(context, poster, "图鉴", jpeg = true)
                    } finally {
                        poster.recycle()
                    }
                }
            }
            exporting = false
            val message = result.fold({ "图鉴长图已保存到$it" }, { "导出失败：${it.message ?: it.javaClass.simpleName}" })
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
                PageHeader("图鉴") {
                    IconButton(onClick = { export() }, enabled = !exporting && shown.isNotEmpty()) {
                        if (exporting) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.FileDownload, contentDescription = "导出图鉴长图", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (captures != null && all.isEmpty()) {
            EmptyState("🌿", "还没有收集到任何东西\n去「拍照」拍下第一只小鸟或一朵花吧")
            return@Column
        }
        StatsRow(speciesCount = speciesCount, total = all.size, newThisMonth = newThisMonth)
        CategoryFilterRow(
            selected = filter,
            onSelect = { filter = it },
            available = groups.map { it.category }.toSet(),
        )
        if (!selecting && groups.isNotEmpty()) {
            Text(
                "长按可以多选删除",
                modifier = Modifier.padding(start = 24.dp, top = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
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
            title = { Text("删除 ${doomed.size} 个条目？") },
            text = { Text("会一并删除这些条目下的 $records 条记录、照片和贴纸板上的贴纸，删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val ids = doomed.flatMap { group -> group.captures.map { it.id } }
                    selected = emptySet()
                    scope.launch { container.captures.deleteAll(ids) }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
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
            Icon(Icons.Outlined.Close, contentDescription = "取消选择", tint = MaterialTheme.colorScheme.primary)
        }
        Text("已选 $count 个", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onSelectAll) { Text("全选") }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.error)
                .clickable(onClick = onDelete)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("删除", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun StatsRow(speciesCount: Int, total: Int, newThisMonth: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatCard("$speciesCount", "已收集物种", Modifier.weight(1f))
        StatCard("$total", "遇见次数", Modifier.weight(1f))
        StatCard("+$newThisMonth", "本月新物种", Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Card)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = value,
            transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
            label = "stat",
        ) { v ->
            Text(v, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
        targetValue = if (selected) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "selectScale",
    )
    val cover = group.cover
    val recognizing = cover.status == RecognitionStatus.PENDING || cover.status == RecognitionStatus.RUNNING
    val accent = group.category.tint

    Column(
        modifier = modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent)
            .border(if (selected) 2.dp else 0.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            StickerImage(
                cover,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = if (selecting) tilt * 0.4f else tilt
                        scaleX = appear.value * press
                        scaleY = appear.value * press
                    },
            )
            if (group.captures.size > 1) {
                Text(
                    text = group.captures.size.toString(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(accent)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = selecting,
                modifier = Modifier.align(Alignment.TopStart),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (selected) Color.White else Color.White.copy(alpha = 0.8f))
                        .border(2.dp, if (selected) accent else Color.LightGray, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "已选", tint = accent, modifier = Modifier.size(24.dp))
                }
            }
        }
        Row(
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(50))
                .background(Card)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (recognizing && cover.nameZh == null) "识别中…" else group.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
