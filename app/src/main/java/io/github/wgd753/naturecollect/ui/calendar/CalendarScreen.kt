package io.github.wgd753.naturecollect.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wgd753.naturecollect.data.Category
import io.github.wgd753.naturecollect.data.db.CaptureEntity
import io.github.wgd753.naturecollect.ui.CategoryFilterRow
import io.github.wgd753.naturecollect.ui.PageHeader
import io.github.wgd753.naturecollect.ui.StickerImage
import io.github.wgd753.naturecollect.ui.rememberContainer
import io.github.wgd753.naturecollect.ui.WatercolorWash
import io.github.wgd753.naturecollect.ui.tr
import io.github.wgd753.naturecollect.ui.washFor
import io.github.wgd753.naturecollect.ui.theme.Forest
import io.github.wgd753.naturecollect.ui.theme.InkSoft
import io.github.wgd753.naturecollect.ui.theme.LatinStyle
import io.github.wgd753.naturecollect.ui.theme.Sun
import io.github.wgd753.naturecollect.ui.theme.TextMuted
import io.github.wgd753.naturecollect.ui.theme.TitleFont
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val WeekdayLabels: List<String>
    get() = listOf(
        tr("日", "Sun"), tr("一", "Mon"), tr("二", "Tue"), tr("三", "Wed"),
        tr("四", "Thu"), tr("五", "Fri"), tr("六", "Sat"),
    )

/** Month grid where each day shows that day's latest sticker and a count. */
@Composable
fun CalendarScreen(onOpenCapture: (String) -> Unit) {
    val container = rememberContainer()
    val captures by container.captures.observeAll().collectAsState(initial = emptyList())
    val today = LocalDate.now()
    var selectedEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpochDay)
    // Months are stored as an offset from the current month so the state is saveable.
    var monthOffset by rememberSaveable { mutableStateOf(0L) }
    val month = YearMonth.from(today).plusMonths(monthOffset)
    var filter by rememberSaveable { mutableStateOf<Category?>(null) }

    val zone = ZoneId.systemDefault()
    val byDay = remember(captures, filter) {
        captures
            .filter { filter == null || Category.fromKey(it.category).key == filter?.key }
            .groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader(tr("日历", "Calendar")) {
            TextButton(onClick = {
                monthOffset = 0
                selectedEpochDay = today.toEpochDay()
            }) { Text(tr("今天", "Today"), color = Forest) }
        }
        DayCard(selected, byDay[selected].orEmpty(), onOpenCapture)
        CategoryFilterRow(selected = filter, onSelect = { filter = it }, modifier = Modifier.padding(top = 12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { monthOffset-- }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = tr("上个月", "Previous month"), tint = Forest)
            }
            Text(
                text = tr(
                    "${month.year} 年 ${month.monthValue} 月",
                    "${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${month.year}",
                ),
                style = MaterialTheme.typography.titleLarge,
                color = Forest,
            )
            IconButton(onClick = { monthOffset++ }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = tr("下个月", "Next month"), tint = Forest)
            }
        }
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tween(260)) { if (forward) it else -it } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(260)) { if (forward) -it else it } + fadeOut(tween(200)))
            },
            label = "month",
        ) { shownMonth ->
            MonthGrid(
                month = shownMonth,
                today = today,
                selected = selected,
                byDay = byDay,
                onSelect = { selectedEpochDay = it.toEpochDay() },
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .pointerInput(Unit) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (total > 120) monthOffset-- else if (total < -120) monthOffset++
                            },
                            onHorizontalDrag = { _, amount -> total += amount },
                        )
                    },
            )
        }
    }
}

/** The big "today" page: the date in title numerals with the day's stickers scattered around on washes. */
@Composable
private fun DayCard(day: LocalDate, captures: List<CaptureEntity>, onOpenCapture: (String) -> Unit) {
    val ink = Forest
    BoxWithConstraints(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(210.dp),
    ) {
        val spots = listOf(
            Triple(0.02f, 0.04f, -8f),
            Triple(0.70f, 0.02f, 7f),
            Triple(0.02f, 0.52f, 6f),
            Triple(0.72f, 0.50f, -6f),
        )
        captures.take(spots.size).forEachIndexed { i, capture ->
            val (fx, fy, rot) = spots[i]
            Box(
                Modifier
                    .offset(x = maxWidth * fx, y = maxHeight * fy)
                    .size(96.dp)
                    .graphicsLayer { rotationZ = rot }
                    .clickable { onOpenCapture(capture.id) },
            ) {
                WatercolorWash(
                    washFor(Category.fromKey(capture.category)),
                    seed = capture.id.hashCode(),
                    modifier = Modifier.fillMaxSize(),
                    strength = 0.8f,
                )
                StickerImage(capture, Modifier.fillMaxSize().padding(10.dp))
            }
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${day.year}", color = ink, fontFamily = TitleFont, fontSize = 18.sp, letterSpacing = 1.sp)
            Text(
                day.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).uppercase(),
                color = ink,
                style = LatinStyle,
                fontSize = 13.sp,
            )
            Text(
                "${day.dayOfYear}/${day.lengthOfYear()} ${day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()}",
                color = TextMuted,
                style = LatinStyle,
                fontSize = 10.sp,
            )
            Text("${day.dayOfMonth}", color = ink, fontFamily = TitleFont, fontSize = 72.sp, lineHeight = 76.sp)
        }
        if (captures.isNotEmpty()) {
            Text(
                "${captures.size}",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .offset(x = 70.dp)
                    .border(1.dp, ink.copy(alpha = 0.6f), CircleShape)
                    .padding(horizontal = 9.dp, vertical = 2.dp),
                color = ink,
                fontFamily = TitleFont,
            )
        } else {
            Text(
                tr("这天还没有收集", "Nothing collected this day"),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    byDay: Map<LocalDate, List<CaptureEntity>>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // java.time numbers Monday..Sunday as 1..7; the grid starts on Sunday.
    val leadingBlanks = month.atDay(1).dayOfWeek.value % 7
    val cells: List<Int?> = List(leadingBlanks) { null } + (1..month.lengthOfMonth()).toList()

    Column(modifier.padding(bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            WeekdayLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSoft,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    val dayOfMonth = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(0.9f), contentAlignment = Alignment.Center) {
                        if (dayOfMonth != null) {
                            val date = month.atDay(dayOfMonth)
                            DayCell(
                                date = date,
                                captures = byDay[date].orEmpty(),
                                isToday = date == today,
                                isSelected = date == selected,
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    captures: List<CaptureEntity>,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val ink = Forest
    val seed = date.toEpochDay().toInt()
    Box(
        Modifier
            .fillMaxSize()
            .padding(2.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (isSelected) Modifier.border(1.dp, ink.copy(alpha = 0.55f), RoundedCornerShape(14.dp)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (captures.isEmpty()) {
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                if (isToday) WatercolorWash(Sun, seed = seed, modifier = Modifier.fillMaxSize())
                Text(
                    "${date.dayOfMonth}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = ink,
                )
            }
        } else {
            val first = captures.first()
            WatercolorWash(
                if (isToday) Sun else washFor(Category.fromKey(first.category)),
                seed = seed,
                modifier = Modifier.fillMaxSize(),
                strength = 0.8f,
            )
            StickerImage(first, Modifier.fillMaxSize().padding(5.dp))
            if (captures.size > 1) {
                Text(
                    "${captures.size}",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 3.dp),
                    color = ink,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
