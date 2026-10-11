package io.github.wgd753.naturecollect.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NatureColors = lightColorScheme(
    primary = Forest,
    onPrimary = Card,
    primaryContainer = Divider,
    onPrimaryContainer = Pine,
    secondary = Sun,
    onSecondary = Color.White,
    tertiary = Coral,
    onTertiary = Color.White,
    background = Paper,
    onBackground = Forest,
    surface = Card,
    onSurface = Forest,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = TextMuted,
    surfaceContainer = Card,
    surfaceContainerHigh = Card,
    error = Color(0xFFA8432B),
)

@Composable
fun NatureCollectTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NatureColors,
        typography = NatureTypography,
        content = content,
    )
}
