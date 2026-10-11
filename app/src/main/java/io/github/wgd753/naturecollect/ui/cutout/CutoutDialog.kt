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
import io.github.wgd753.naturecollect.ui.theme.Celadon
import io.github.wgd753.naturecollect.ui.theme.Pine
import io.github.wgd753.naturecollect.ui.theme.Rust
import kotlinx.coroutines.launch
import io.github.wgd753.naturecollect.ui.tr

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
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(photo) {
        try {
            image = container.segmenter.encode(photo)
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Celadon)
                .systemBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val img = image
            val state = img?.let {
                rememberCutoutState(it, container.segmenter, SamPrompt.Points(listOf(PromptPoint(it.bitmap.width / 2f, it.bitmap.height / 2f))))
            }
            if (state == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (error != null) Text(tr("抠图失败：$error", "Cutout failed: $error"), color = Rust) else CircularProgressIndicator(color = Pine, strokeWidth = 2.dp)
                }
            } else {
                CutoutEditor(state)
            }
            Row(
                Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PillButton(tr("取消", "Cancel"), onClick = onDismiss)
                PillButton(tr("整张贴上", "Use whole photo")) { onDone(StickerMaker.framed(photo)) }
                PillButton(tr("用抠出的", "Use cutout"), primary = true, enabled = state?.hasSelection == true) {
                    scope.launch { state?.sticker()?.let(onDone) }
                }
            }
        }
    }
}
