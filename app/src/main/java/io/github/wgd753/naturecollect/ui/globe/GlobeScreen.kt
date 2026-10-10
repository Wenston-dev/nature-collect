package io.github.wgd753.naturecollect.ui.globe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.wgd753.naturecollect.ui.EmptyState
import io.github.wgd753.naturecollect.ui.PageHeader

@Composable
fun GlobeScreen() {
    Column(Modifier.fillMaxSize()) {
        PageHeader("地图")
        EmptyState("🗺️", "在地图上看你在哪里遇见了它们\n（v0.2 上线）")
    }
}
