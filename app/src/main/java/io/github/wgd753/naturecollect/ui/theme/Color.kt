package io.github.wgd753.naturecollect.ui.theme

import androidx.compose.ui.graphics.Color

// "晕染" palette: a field sketchbook. White paper, ink-green text, and colour
// only as watercolour washes behind the stickers.

/** Ink green: titles, text, active icons, primary buttons. */
val Forest = Color(0xFF2F4639)
val ForestDeep = Color(0xFF24372D)

/** Ochre wash: the shutter, highlights, the active filter. */
val Sun = Color(0xFFE3B25B)

/** Coral wash: badges and warm accents. */
val Coral = Color(0xFFE39A88)

/** Watercolour washes; see [io.github.wgd753.naturecollect.ui.washFor]. */
val WashSap = Color(0xFF8FB07A)
val WashWater = Color(0xFF8DB8C6)
val WashOchre = Sun
val WashCoral = Coral
val WashLilac = Color(0xFFB4A7CF)
val WashStone = Color(0xFFB9BFAF)

/** Paper page background. */
val Paper = Color(0xFFF8F9F4)
val PaperDeep = Color(0xFFEDEFE6)

/** No white cards in this style: surfaces are the paper itself. */
val Card = Paper

/** Near-black green for camera screens. */
val Night = Color(0xFF101C16)

/** Handwriting ink for notes and board text. */
val HandInk = Color(0xFF3F5A48)

// Older names, kept so every screen picks up the new palette.
val Ink = Forest
val InkSoft = Color(0xFF9FB0A5)

val TextMuted = Color(0xFF7F8F84)
