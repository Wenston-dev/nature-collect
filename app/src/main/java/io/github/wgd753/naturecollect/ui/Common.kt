package io.github.wgd753.naturecollect.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.wgd753.naturecollect.AppContainer
import io.github.wgd753.naturecollect.NatureApp
import io.github.wgd753.naturecollect.data.Category
import io.github.wgd753.naturecollect.data.db.CaptureEntity
import io.github.wgd753.naturecollect.ui.theme.Card
import io.github.wgd753.naturecollect.ui.theme.InkSoft
import java.io.File

@Composable
fun rememberContainer(): AppContainer = (LocalContext.current.applicationContext as NatureApp).container

/** Big italic page title with optional actions on the right. */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Centered placeholder shown while a page has nothing in it yet. */
@Composable
fun EmptyState(emoji: String, message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, style = MaterialTheme.typography.displaySmall)
            Text(
                text = message,
                modifier = Modifier.padding(top = 12.dp, start = 32.dp, end = 32.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The die-cut sticker image for a capture. */
@Composable
fun StickerImage(
    capture: CaptureEntity,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val container = rememberContainer()
    AsyncImage(
        model = container.media.file(capture.stickerFile),
        contentDescription = capture.displayName,
        modifier = modifier,
        contentScale = contentScale,
    )
}

@Composable
fun MediaImage(file: File, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    AsyncImage(model = file, contentDescription = null, modifier = modifier, contentScale = contentScale)
}

/** Horizontal "All + categories" filter, like CapLog's emoji chips. */
@Composable
fun CategoryFilterRow(
    selected: Category?,
    onSelect: (Category?) -> Unit,
    modifier: Modifier = Modifier,
    available: Set<Category> = Category.entries.toSet(),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterPill(text = "全部", selected = selected == null) { onSelect(null) }
        Category.entries.filter { it in available }.forEach { category ->
            FilterPill(text = "${category.emoji} ${category.label}", selected = selected == category) {
                onSelect(if (selected == category) null else category)
            }
        }
    }
}

@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else Card)
            .clickable(onClick = onClick)
            .padding(PaddingValues(horizontal = 16.dp, vertical = 9.dp)),
        style = MaterialTheme.typography.titleMedium,
        color = if (selected) Color.White else MaterialTheme.colorScheme.primary,
    )
}

/** Rounded text button used on photo screens. */
@Composable
fun PillButton(text: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (primary) MaterialTheme.colorScheme.primary else Card)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f },
        color = if (primary) Color.White else MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleMedium,
    )
}

/** Small rounded label, e.g. a category or a status. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, color: Color = InkSoft.copy(alpha = 0.25f)) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * Holographic foil: a rainbow band sweeps across the sticker. SrcAtop keeps
 * the colour on the sticker's own pixels, so the transparent cut-out stays clear.
 */
fun Modifier.holographic(intensity: Float = 0.45f, periodMs: Int = 2600): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "holo")
    val phase by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "holoPhase",
    )
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val band = size.width * 0.9f
            val startX = phase * size.width
            val brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFF9AD5).copy(alpha = intensity),
                    Color(0xFFFFF59A).copy(alpha = intensity),
                    Color(0xFF9AFFE0).copy(alpha = intensity),
                    Color(0xFF9AB8FF).copy(alpha = intensity),
                    Color(0xFFFFFFFF).copy(alpha = intensity * 1.2f),
                    Color.Transparent,
                ),
                start = Offset(startX - band, 0f),
                end = Offset(startX, size.height),
            )
            drawRect(brush = brush, blendMode = BlendMode.SrcAtop)
        }
}

fun formatDate(millis: Long): String =
    java.text.SimpleDateFormat("yyyy年M月d日 HH:mm", java.util.Locale.CHINA).format(java.util.Date(millis))

fun formatDay(millis: Long): String =
    java.text.SimpleDateFormat("yyyy年M月d日", java.util.Locale.CHINA).format(java.util.Date(millis))
