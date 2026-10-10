package io.github.wgd753.naturecollect.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * In-app language switch. Strings are written inline as tr("中文", "English");
 * flipping [Lang.english] recomposes every screen without restarting the app.
 * Names and notes the user typed or the AI returned are shown as stored.
 */
object Lang {
    var english by mutableStateOf(false)
}

fun tr(zh: String, en: String): String = if (Lang.english) en else zh
