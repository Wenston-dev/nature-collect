package io.github.wgd753.naturecollect.ui.collection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.wgd753.naturecollect.ui.EmptyState
import io.github.wgd753.naturecollect.ui.PageHeader

@Composable
fun CollectionScreen() {
    Column(Modifier.fillMaxSize()) {
        PageHeader("图鉴") {
            Text(
                text = "0 种",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        EmptyState("🌿", "还没有收集到任何物种\n去「拍照」拍下第一只小鸟或一朵花吧")
    }
}
