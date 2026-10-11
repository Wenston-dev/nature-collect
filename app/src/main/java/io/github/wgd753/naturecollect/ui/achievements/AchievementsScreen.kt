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
import io.github.wgd753.naturecollect.ui.WatercolorWash
import io.github.wgd753.naturecollect.ui.theme.Forest
import io.github.wgd753.naturecollect.ui.theme.Paper
import io.github.wgd753.naturecollect.ui.theme.PaperDeep
import io.github.wgd753.naturecollect.ui.theme.TextMuted
import io.github.wgd753.naturecollect.ui.theme.TitleFont
import io.github.wgd753.naturecollect.ui.tr
import io.github.wgd753.naturecollect.ui.washFor

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
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = tr("返回", "Back"), tint = Forest)
            }
            Text(
                tr("成就", "Achievements"),
                style = MaterialTheme.typography.titleLarge,
                color = Forest,
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
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            summary.categories.forEach { CategoryRow(it) }
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(thickness = 0.5.dp, color = PaperDeep)
            Column(Modifier.fillMaxWidth()) {
                summary.extras.forEachIndexed { i, extra ->
                    if (i > 0) HorizontalDivider(thickness = 0.5.dp, color = PaperDeep)
                    ExtraRow(extra)
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(progress: CategoryProgress) {
    val category = progress.category
    val wash = washFor(category)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            WatercolorWash(wash, seed = category.key.hashCode(), modifier = Modifier.fillMaxSize())
            Text(category.emoji, fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    category.display,
                    fontFamily = TitleFont,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp,
                    color = Forest,
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
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(PaperDeep),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.fraction)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Forest),
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
                                .background(if (earned) Forest else Paper)
                                .border(1.dp, if (earned) Forest else TextMuted.copy(alpha = 0.5f), CircleShape),
                        )
                        Text(
                            "$tier",
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            color = if (earned) Forest else TextMuted.copy(alpha = 0.7f),
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
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            extra.title,
            color = if (extra.done) Forest else TextMuted,
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
