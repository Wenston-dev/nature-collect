package io.github.wgd753.naturecollect.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.github.wgd753.naturecollect.data.groupBySpecies
import io.github.wgd753.naturecollect.ui.EmptyState
import io.github.wgd753.naturecollect.ui.StickerImage
import io.github.wgd753.naturecollect.ui.Tag
import io.github.wgd753.naturecollect.ui.formatDate
import io.github.wgd753.naturecollect.ui.formatDay
import io.github.wgd753.naturecollect.ui.holographic
import io.github.wgd753.naturecollect.ui.rememberContainer
import io.github.wgd753.naturecollect.ui.theme.Card

@Composable
fun SpeciesDetailScreen(speciesKey: String, onBack: () -> Unit, onOpenCapture: (String) -> Unit) {
    val container = rememberContainer()
    val captures by container.captures.observeAll().collectAsState(initial = null)
    val group = captures?.groupBySpecies()?.firstOrNull { it.key == speciesKey }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (group == null) {
            if (captures != null) EmptyState("🍃", "这个物种已经不在图鉴里了")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    StickerImage(group.cover, Modifier.size(240.dp).holographic(0.35f, 3000))
                    Spacer(Modifier.height(12.dp))
                    Text(group.name, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                    group.nameSci?.let {
                        Text(it, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tag("${group.category.emoji} ${group.category.label}")
                        Tag("遇见 ${group.captures.size} 次")
                        Tag("初遇 ${formatDay(group.firstSeen)}")
                    }
                    group.captures.firstNotNullOfOrNull { it.description }?.let {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            it,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Card)
                                .padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "每一次遇见",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            items(group.captures, key = { it.id }) { capture ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOpenCapture(capture.id) }
                        .background(Card)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(64.dp)) { StickerImage(capture, Modifier.fillMaxSize()) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(formatDate(capture.createdAt), style = MaterialTheme.typography.titleMedium)
                        Text(
                            capture.placeName ?: capture.latitude?.let { "%.4f, %.4f".format(it, capture.longitude) } ?: "没有位置信息",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
