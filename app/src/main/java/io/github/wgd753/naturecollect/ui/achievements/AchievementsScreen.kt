package io.github.wgd753.naturecollect.ui.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wgd753.naturecollect.data.Category
import io.github.wgd753.naturecollect.ui.rememberContainer
import io.github.wgd753.naturecollect.ui.theme.Card
import io.github.wgd753.naturecollect.ui.theme.Forest
import io.github.wgd753.naturecollect.ui.theme.PaperDeep
import io.github.wgd753.naturecollect.ui.theme.TextMuted
import io.github.wgd753.naturecollect.ui.tint
import io.github.wgd753.naturecollect.ui.tr

/** Species milestones per nature category, plus a few cross-category ones. */
@Composable
fun AchievementsScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val captures by container.captures.observeAll().collectAsState(initial = emptyList())
    val categories = Category.entries
    val summary = remember(captures, categories, io.github.wgd753.naturecollect.ui.Lang.english) { achievementSummary(captures) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = tr("返回", "Back"), tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                tr("成就", "Achievements"),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${summary.earned}/${summary.total}",
                style = MaterialTheme.typography.titleMedium,
                color = TextMuted,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            summary.categories.forEach { CategoryRow(it) }
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Card),
            ) {
                summary.extras.forEachIndexed { i, extra ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = PaperDeep)
                    ExtraRow(extra)
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(progress: CategoryProgress) {
    val category = progress.category
    val tint = category.tint
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Text(category.emoji, fontSize = 22.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    category.display,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(
                    tr("${progress.species} 种", "${progress.species} species"),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tint.copy(alpha = 0.12f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.fraction)
                        .clip(RoundedCornerShape(2.dp))
                        .background(tint),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SpeciesTiers.forEach { tier ->
                    val earned = progress.species >= tier
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (earned) tint else Card)
                                .border(1.dp, if (earned) tint else tint.copy(alpha = 0.35f), CircleShape),
                        )
                        Text(
                            "$tier",
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            color = if (earned) MaterialTheme.colorScheme.onSurface else TextMuted.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraRow(extra: ExtraAchievement) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            extra.title,
            color = if (extra.done) MaterialTheme.colorScheme.onSurface else TextMuted,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (extra.done) {
            Icon(Icons.Filled.Check, contentDescription = tr("已完成", "Done"), tint = Forest, modifier = Modifier.size(20.dp))
        } else {
            Text("${extra.current}/${extra.goal}", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        }
    }
}
