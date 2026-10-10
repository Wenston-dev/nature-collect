package io.github.wgd753.naturecollect.ui.board

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.wgd753.naturecollect.ui.EmptyState
import io.github.wgd753.naturecollect.ui.PageHeader

@Composable
fun BoardScreen() {
    Column(Modifier.fillMaxSize()) {
        PageHeader("贴纸板")
        EmptyState("🧩", "把收集到的贴纸自由拼贴成主题画板\n（v0.3 上线）")
    }
}
