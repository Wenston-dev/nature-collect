package io.github.wgd753.naturecollect.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.wgd753.naturecollect.ui.PageHeader
import io.github.wgd753.naturecollect.ui.theme.InkSoft
import java.time.LocalDate
import java.time.YearMonth

private val WeekdayLabels = listOf("日", "一", "二", "三", "四", "五", "六")

/** Month grid; each day cell will later show that day's sticker and count. */
@Composable
fun CalendarScreen() {
    val today = LocalDate.now()
    // Months are stored as an offset from the current month so the state is saveable.
    var monthOffset by rememberSaveable { mutableStateOf(0L) }
    val month = YearMonth.from(today).plusMonths(monthOffset)

    Column(Modifier.fillMaxSize()) {
        PageHeader("日历") {
            TextButton(onClick = { monthOffset = 0 }) { Text("今天") }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { monthOffset-- }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "上个月")
            }
            Text(
                text = "${month.year} 年 ${month.monthValue} 月",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = { monthOffset++ }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "下个月")
            }
        }
        MonthGrid(month = month, today = today, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun MonthGrid(month: YearMonth, today: LocalDate, modifier: Modifier = Modifier) {
    // java.time numbers Monday..Sunday as 1..7; the grid starts on Sunday.
    val leadingBlanks = month.atDay(1).dayOfWeek.value % 7
    val cells: List<Int?> = List(leadingBlanks) { null } + (1..month.lengthOfMonth()).toList()

    Column(modifier) {
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
                    val day = week.getOrNull(i)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day != null) {
                            val isToday = month.atDay(day) == today
                            Box(
                                modifier = Modifier
                                    .fillMaxSize(0.7f)
                                    .clip(CircleShape)
                                    .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = day.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isToday) Color.White else MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
