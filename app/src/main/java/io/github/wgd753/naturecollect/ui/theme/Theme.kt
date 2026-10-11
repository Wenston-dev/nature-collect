package io.github.wgd753.naturecollect.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NatureColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE7D6),
    onPrimaryContainer = ForestDeep,
    secondary = Sun,
    onSecondary = ForestDeep,
    tertiary = Coral,
    onTertiary = Color.White,
    background = Paper,
    onBackground = Forest,
    surface = Paper,
    onSurface = Forest,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = TextMuted,
    surfaceContainer = Card,
    surfaceContainerHigh = Card,
    error = Color(0xFFC0533F),
)

@Composable
fun NatureCollectTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NatureColors,
        typography = NatureTypography,
        content = content,
    )
}
