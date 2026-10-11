package io.github.wgd753.naturecollect.ui.theme

import androidx.compose.ui.graphics.Color

// "标本" palette: each cut-out is mounted on white card stock like a herbarium
// specimen, on a pale celadon page. One accent only: rust.

/** Pine ink: titles, text, active icons, primary buttons. */
val Pine = Color(0xFF22392F)

/** Secondary text, inactive icons, mono metadata. */
val Soft = Color(0xFF6F8277)

/** The single accent: shutter ring, active underlines, stamps, map pins. */
val Rust = Color(0xFFB4532E)

/** Page background. */
val Celadon = Color(0xFFE2EBE3)

/** White card stock for specimen cards and sheets. */
val Card = Color(0xFFFBFBF7)

/** Map water. */
val Water = Color(0xFFC9DCD5)

/** Dividers, and the herbarium label border. */
val Divider = Color(0xFFDCE2DB)
val LabelBorder = Color(0xFF9FB0A4)

/** Linen tape strips at card corners. */
val Tape = Color(0xD9E2DBC4)

/** Near-black green for camera screens. */
val Night = Color(0xFF101C16)

// Older names, mapped so every screen picks up the new palette.
val Forest = Pine
val ForestDeep = Pine
val Ink = Pine
val HandInk = Pine
val Sun = Rust
val Coral = Rust
val Paper = Celadon
val PaperDeep = Divider
val InkSoft = LabelBorder
val TextMuted = Soft
