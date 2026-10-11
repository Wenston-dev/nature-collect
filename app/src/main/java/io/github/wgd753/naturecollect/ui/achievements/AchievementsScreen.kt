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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import io.github.wgd753.naturecollect.ui.Hairline
import io.github.wgd753.naturecollect.ui.rememberContainer
import io.github.wgd753.naturecollect.ui.specimenCard
import io.github.wgd753.naturecollect.ui.theme.Card
import io.github.wgd753.naturecollect.ui.theme.Divider
import io.github.wgd753.naturecollect.ui.theme.MonoStyle
import io.github.wgd753.naturecollect.ui.theme.Pine
import io.github.wgd753.naturecollect.ui.theme.Rust
import io.github.wgd753.naturecollect.ui.theme.Soft
import io.github.wgd753.naturecollect.ui.theme.spaced
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
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = tr("返回", "Back"), tint = Pine)
            }
            Text(
                tr("成就", "Achievements"),
                style = MaterialTheme.typography.headlineSmall,
                color = Pine,
                modifier = Modifier.weight(1f),
            )
            Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.Bottom) {
                Text("${summary.earned}", style = MonoStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium), color = Rust)
                Text("/${summary.total}", style = MonoStyle.copy(fontSize = 12.sp), color = Soft, modifier = Modifier.padding(bottom = 1.dp))
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.fillMaxWidth().specimenCard()) {
                summary.categories.forEachIndexed { i, progress ->
                    if (i > 0) Hairline(Modifier.padding(horizontal = 14.dp), color = Divider)
                    CategoryRow(progress)
                }
            }
            Column(Modifier.fillMaxWidth().specimenCard()) {
                summary.extras.forEachIndexed { i, extra ->
                    if (i > 0) Hairline(Modifier.padding(horizontal = 14.dp), color = Divider)
                    ExtraRow(extra)
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(progress: CategoryProgress) {
    val category = progress.category
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(category.tint))
            Spacer(Modifier.width(8.dp))
            Text(
                category.display,
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.sp),
                color = Pine,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                tr("${progress.species} 种", "${progress.species} species"),
                style = MonoStyle,
                color = Soft,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Divider),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.fraction)
                    .background(Pine),
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
                            .background(if (earned) Pine else Card)
                            .border(1.dp, if (earned) Pine else Soft.copy(alpha = 0.5f), CircleShape),
                    )
                    Text(
                        "$tier",
                        style = MonoStyle.copy(fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = 0.sp),
                        color = if (earned) Pine else Soft.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
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
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            extra.title,
            color = if (extra.done) Pine else Soft,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (extra.done) {
            Icon(Icons.Filled.Check, contentDescription = tr("已完成", "Done"), tint = Pine, modifier = Modifier.size(18.dp))
        } else {
            Text("${extra.current}/${extra.goal}", style = MonoStyle, color = Soft)
        }
    }
}
