package io.github.wgd753.naturecollect.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.wgd753.naturecollect.R

/** Titles and species names: an upright brush-like Song. Never italic. */
val TitleFont = FontFamily(Font(R.font.zcool_xiaowei))

/** Handwriting, only for things the user wrote (notes, board text). */
val HandFont = FontFamily(Font(R.font.long_cang))

val NatureTypography = Typography(
    displaySmall = TextStyle(fontFamily = TitleFont, fontSize = 30.sp, letterSpacing = 1.8.sp),
    headlineSmall = TextStyle(fontFamily = TitleFont, fontSize = 24.sp, letterSpacing = 1.2.sp),
    titleLarge = TextStyle(fontFamily = TitleFont, fontSize = 20.sp, letterSpacing = 1.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.8.sp),
)

/** Scientific / English names: small uppercase sans, widely spaced. */
val LatinStyle = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 2.sp)
