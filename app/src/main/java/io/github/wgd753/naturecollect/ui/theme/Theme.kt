package io.github.wgd753.naturecollect.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NatureColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    secondary = InkSoft,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Card,
    onSurfaceVariant = TextMuted,
    surfaceContainer = Card,
)

@Composable
fun NatureCollectTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NatureColors,
        typography = NatureTypography,
        content = content,
    )
}
