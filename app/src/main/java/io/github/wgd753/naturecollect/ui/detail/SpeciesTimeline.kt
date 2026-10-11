package io.github.wgd753.naturecollect.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.wgd753.naturecollect.data.db.CaptureEntity
import io.github.wgd753.naturecollect.ui.Lang
import io.github.wgd753.naturecollect.ui.StickerImage
import io.github.wgd753.naturecollect.ui.theme.Card
import io.github.wgd753.naturecollect.ui.theme.TitleFont
import io.github.wgd753.naturecollect.ui.tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Every encounter of one species as a quiet vertical timeline, newest first.
 * Tapping a row opens that capture; the one on screen is highlighted.
 */
@Composable
internal fun Timeline(siblings: List<CaptureEntity>, currentId: String, onOpen: (String) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val english = Lang.english
    val format = remember(english) {
        if (english) SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH) else SimpleDateFormat("yyyy.MM.dd", Locale.CHINA)
    }
    // Places count only when at least two finds carry coordinates; ~100 m counts as the same spot.
    val located = siblings.filter { it.latitude != null && it.longitude != null }
    val places = if (located.size >= 2) {
        located.map { (it.latitude!! * 1000).roundToLong() to (it.longitude!! * 1000).roundToLong() }.distinct().size
    } else {
        0
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        Text(
            tr("足迹 · ${siblings.size} 次", "Encounters · ${siblings.size}"),
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = TitleFont),
            color = primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (places > 0) {
            Text(
                tr("$places 个地点", if (places == 1) "1 place" else "$places places"),
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        siblings.forEachIndexed { index, item ->
            val current = item.id == currentId
            val first = index == 0
            val last = index == siblings.lastIndex
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (current) primary.copy(alpha = 0.06f) else Color.Transparent)
                    .clickable(enabled = !current) { onOpen(item.id) }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Rail: a hairline through every row, broken at the ends, with a dot per find.
                Box(
                    Modifier
                        .width(20.dp)
                        .fillMaxHeight()
                        .drawBehind {
                            val x = size.width / 2
                            val mid = size.height / 2
                            val line = primary.copy(alpha = 0.22f)
                            val stroke = 1.dp.toPx()
                            if (!first) drawLine(line, Offset(x, 0f), Offset(x, mid), stroke)
                            if (!last) drawLine(line, Offset(x, mid), Offset(x, size.height), stroke)
                            if (current) {
                                drawCircle(primary, radius = 5.dp.toPx(), center = Offset(x, mid))
                            } else {
                                drawCircle(Card, radius = 4.dp.toPx(), center = Offset(x, mid))
                                drawCircle(primary.copy(alpha = 0.45f), radius = 3.dp.toPx(), center = Offset(x, mid))
                            }
                        },
                )
                Spacer(Modifier.width(8.dp))
                StickerImage(item, Modifier.padding(vertical = 6.dp).size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(
                        format.format(Date(item.createdAt)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (current) primary else MaterialTheme.colorScheme.onSurface,
                    )
                    item.placeName?.takeIf { it.isNotBlank() }?.let { place ->
                        Text(
                            place,
                            style = MaterialTheme.typography.labelMedium,
                            color = muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
