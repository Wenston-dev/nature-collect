package io.github.wgd753.naturecollect.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NatureColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE5D6),
    onPrimaryContainer = ForestDeep,
    secondary = Sun,
    onSecondary = ForestDeep,
    tertiary = Coral,
    onTertiary = Color.White,
    background = Paper,
    onBackground = Color(0xFF1C2A23),
    surface = Paper,
    onSurface = Color(0xFF1C2A23),
    surfaceVariant = Color(0xFFE2EBE0),
    onSurfaceVariant = TextMuted,
    surfaceContainer = Card,
    surfaceContainerHigh = Card,
    error = Color(0xFFD64B3B),
)

@Composable
fun NatureCollectTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NatureColors,
        typography = NatureTypography,
        content = content,
    )
}
