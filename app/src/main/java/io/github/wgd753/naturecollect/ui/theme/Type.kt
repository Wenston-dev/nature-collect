package io.github.wgd753.naturecollect.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.wgd753.naturecollect.R

/** Titles: the system sans at medium weight; space them out with [spaced]. */
val TitleFont = FontFamily.Default

/** Kept for older call sites; this style has no handwriting face. */
val HandFont = FontFamily.Default

/** Data, labels, numbers, coordinates, catalogue numbers. */
val MonoFont = FontFamily(
    Font(R.font.dm_mono, FontWeight.Normal),
    Font(R.font.dm_mono_medium, FontWeight.Medium),
)

val NatureTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 26.sp, letterSpacing = 1.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, letterSpacing = 1.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 17.sp, letterSpacing = 0.5.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontFamily = MonoFont, fontSize = 10.sp, letterSpacing = 1.sp),
)

/** Mono metadata: scientific names, numbers, coordinates. */
val MonoStyle = TextStyle(fontFamily = MonoFont, fontSize = 11.sp, letterSpacing = 0.6.sp)

/** Older name for [MonoStyle]. */
val LatinStyle = MonoStyle

/** Kept for older call sites; titles rely on letterSpacing alone now. */
fun spaced(text: String): String = text
