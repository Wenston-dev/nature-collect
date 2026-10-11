package io.github.wgd753.naturecollect.ui

import io.github.wgd753.naturecollect.ui.theme.TextMuted
import io.github.wgd753.naturecollect.ui.theme.Sun
import androidx.compose.animation.core.animateFloat
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** Page title with optional actions on the right. */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Round icon button on a soft tinted disc, used in page headers. */
@Composable
fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .padding(start = 8.dp)
            .size(42.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary)
    }
}

/** Centered placeholder shown while a page has nothing in it yet. */
@Composable
fun EmptyState(emoji: String, message: String, modifier: Modifier = Modifier, @DrawableRes art: Int? = null) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (art != null) {
                Mascot(art, Modifier.size(150.dp))
            } else {
                Text(emoji, style = MaterialTheme.typography.displaySmall)
            }
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

/** A mascot illustration that bobs gently, so empty screens feel alive. */
@Composable
fun Mascot(@DrawableRes art: Int, modifier: Modifier = Modifier, bob: Boolean = true) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "bob")
    val y by t.animateFloat(
        initialValue = 0f,
        targetValue = if (bob) 1f else 0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "y",
    )
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(art),
        contentDescription = null,
        modifier = modifier.graphicsLayer {
            translationY = -8.dp.toPx() * y
            rotationZ = (y - 0.5f) * 3f
        },
    )
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
    available: Set<String>? = null,
    onManage: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterPill(text = tr("全部", "All"), selected = selected == null) { onSelect(null) }
        Category.entries.filter { available == null || it.key in available }.forEach { category ->
            val isSelected = selected?.key == category.key
            FilterPill(text = "${category.emoji} ${category.display}", selected = isSelected, tint = category.tint) {
                onSelect(if (isSelected) null else category)
            }
        }
        if (onManage != null) FilterPill(text = tr("＋ 分类", "＋ Category"), selected = false, onClick = onManage)
    }
}

@Composable
fun FilterPill(text: String, selected: Boolean, tint: Color? = null, onClick: () -> Unit) {
    // Plain text; the active one gets a small watercolour wash behind it.
    val wash = tint?.let { washFor(it) } ?: Sun
    val fg by androidx.compose.animation.animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else TextMuted, label = "pillFg")
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .then(if (selected) Modifier.watercolorWash(wash, text.hashCode(), strength = 1.4f) else Modifier)
            .clickable(onClick = onClick)
            .padding(PaddingValues(horizontal = 12.dp, vertical = 7.dp)),
        style = MaterialTheme.typography.titleMedium,
        color = fg,
    )
}

/** Rounded text button used on photo screens. */
@Composable
fun PillButton(text: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (primary) MaterialTheme.colorScheme.primary else Card)
            .then(if (primary) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(16.dp)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
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
 * Holographic foil: a rainbow band sweeps across the sticker a couple of
 * times when it appears (or when [trigger] changes), then stops so idle
 * screens don't redraw every frame. SrcAtop keeps the colour on the
 * sticker's own pixels, so the transparent cut-out stays clear.
 */
fun Modifier.holographic(
    intensity: Float = 0.45f,
    periodMs: Int = 2600,
    sweeps: Int = 2,
    trigger: Any? = Unit,
): Modifier = composed {
    val phase = remember { Animatable(-1f) }
    var active by remember { mutableStateOf(true) }
    LaunchedEffect(trigger) {
        active = true
        repeat(sweeps) {
            phase.snapTo(-1f)
            phase.animateTo(2f, tween(periodMs, easing = LinearEasing))
        }
        active = false
    }
    if (!active) return@composed this
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val band = size.width * 0.9f
            val startX = phase.value * size.width
            val brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFF9AD5).copy(alpha = intensity),
                    Color(0xFFFFF59A).copy(alpha = intensity),
                    Color(0xFF9AFFE0).copy(alpha = intensity),
                    Color(0xFF9AB8FF).copy(alpha = intensity),
                    Color(0xFFFFFFFF).copy(alpha = (intensity * 1.2f).coerceAtMost(1f)),
                    Color.Transparent,
                ),
                start = Offset(startX - band, 0f),
                end = Offset(startX, size.height),
            )
            drawRect(brush = brush, blendMode = BlendMode.SrcAtop)
        }
}

/** Accent colour for a category, used for chips, badges and poster labels. */
val Category.tint: Color get() = Color(color)

fun formatDate(millis: Long): String =
    java.text.SimpleDateFormat(tr("yyyy年M月d日 HH:mm", "MMM d, yyyy HH:mm"), if (Lang.english) java.util.Locale.ENGLISH else java.util.Locale.CHINA).format(java.util.Date(millis))

fun formatDay(millis: Long): String =
    java.text.SimpleDateFormat(tr("yyyy年M月d日", "MMM d, yyyy"), if (Lang.english) java.util.Locale.ENGLISH else java.util.Locale.CHINA).format(java.util.Date(millis))
