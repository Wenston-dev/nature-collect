package io.github.wgd753.naturecollect.ui.globe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.wgd753.naturecollect.data.db.CaptureEntity
import io.github.wgd753.naturecollect.ui.Hairline
import io.github.wgd753.naturecollect.ui.StickerImage
import io.github.wgd753.naturecollect.ui.theme.Card
import io.github.wgd753.naturecollect.ui.theme.LabelBorder
import io.github.wgd753.naturecollect.ui.theme.MonoStyle
import io.github.wgd753.naturecollect.ui.theme.Pine
import io.github.wgd753.naturecollect.ui.theme.Soft
import io.github.wgd753.naturecollect.ui.tr
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** How far around a tapped sticker the place sheet gathers finds, in metres. */
internal const val NEARBY_RADIUS_M = 300

/** Great-circle distance between two WGS-84 points, in metres. */
internal fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * asin(sqrt(min(1.0, a)))
}

/**
 * Finds within [radiusM] of [anchor] (raw GPS, not the GCJ-02 display shift),
 * nearest first. [candidates] must all carry a location.
 */
internal fun nearbyCaptures(anchor: CaptureEntity, candidates: List<CaptureEntity>, radiusM: Int = NEARBY_RADIUS_M): List<CaptureEntity> {
    val lat = anchor.latitude ?: return listOf(anchor)
    val lon = anchor.longitude ?: return listOf(anchor)
    return candidates
        .mapNotNull { c ->
            val cLat = c.latitude ?: return@mapNotNull null
            val cLon = c.longitude ?: return@mapNotNull null
            val d = haversineMeters(lat, lon, cLat, cLon)
            if (d <= radiusM) c to d else null
        }
        .sortedWith(compareBy<Pair<CaptureEntity, Double>> { it.second }.thenByDescending { it.first.createdAt })
        .map { it.first }
}

/**
 * White specimen sheet listing every sticker found around one spot: place name
 * and a mono count on top, then a grid of stickers with their names.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaceSheet(
    title: String,
    captures: List<CaptureEntity>,
    radiusM: Int,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RectangleShape,
        containerColor = Card,
        dragHandle = { BottomSheetDefaults.DragHandle(color = LabelBorder) },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = Pine,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    tr("${captures.size} 张 · $radiusM m", "${captures.size} · $radiusM m"),
                    style = MonoStyle,
                    color = Soft,
                )
            }
            Hairline(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.heightIn(max = 520.dp),
            ) {
                items(captures, key = { it.id }) { capture ->
                    Column(
                        Modifier.clickable { onOpen(capture.id) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp)) {
                            StickerImage(capture, Modifier.fillMaxSize())
                        }
                        Text(
                            capture.displayName,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Pine,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
