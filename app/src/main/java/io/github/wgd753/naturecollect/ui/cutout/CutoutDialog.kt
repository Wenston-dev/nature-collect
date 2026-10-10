package io.github.wgd753.naturecollect.ui.cutout

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.wgd753.naturecollect.ml.PromptPoint
import io.github.wgd753.naturecollect.ml.SamImage
import io.github.wgd753.naturecollect.ml.SamPrompt
import io.github.wgd753.naturecollect.ml.StickerMaker
import io.github.wgd753.naturecollect.ui.PillButton
import io.github.wgd753.naturecollect.ui.rememberContainer
import io.github.wgd753.naturecollect.ui.theme.Paper
import kotlinx.coroutines.launch

/**
 * Full-screen picker for turning any photo into a sticker: the middle is cut
 * out automatically, the user can circle something else, or keep the whole
 * photo with a white frame.
 */
@Composable
fun CutoutDialog(photo: Bitmap, onDismiss: () -> Unit, onDone: (Bitmap) -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var image by remember { mutableStateOf<SamImage?>(null) }
    var overlay by remember { mutableStateOf<Bitmap?>(null) }
    var sticker by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun run(prompt: (SamImage) -> SamPrompt) {
        val img = image ?: return
        busy = true
        scope.launch {
            val alpha = container.segmenter.segment(img, prompt(img))
            overlay = maskOverlay(alpha, img.bitmap.width, img.bitmap.height)
            sticker = StickerMaker.make(img.bitmap, alpha)
            busy = false
        }
    }

    LaunchedEffect(photo) {
        try {
            image = container.segmenter.encode(photo)
            run { SamPrompt.Points(listOf(PromptPoint(it.bitmap.width / 2f, it.bitmap.height / 2f))) }
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            busy = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Paper)
                .systemBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "圈出想要的部分，或点一下",
                modifier = Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val img = image
                when {
                    error != null -> Text("抠图失败：$error", color = MaterialTheme.colorScheme.error)
                    img == null -> CircularProgressIndicator()
                    else -> SubjectPicker(img.bitmap, overlay, busy, onPrompt = { prompt -> run { prompt } })
                }
            }
            Row(
                Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PillButton("取消", onClick = onDismiss)
                PillButton("整张贴上") { onDone(StickerMaker.framed(photo)) }
                PillButton("用抠出的", primary = true, enabled = sticker != null && !busy) { sticker?.let(onDone) }
            }
        }
    }
}
