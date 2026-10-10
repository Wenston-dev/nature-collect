package io.github.wgd753.naturecollect.ui.cutout

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.wgd753.naturecollect.ml.PromptPoint
import io.github.wgd753.naturecollect.ml.SamImage
import io.github.wgd753.naturecollect.ml.SamPrompt
import io.github.wgd753.naturecollect.ml.SamSegmenter
import io.github.wgd753.naturecollect.ml.StickerMaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The selection being edited on a photo: what was circled or tapped, what was
 * erased and which size was picked. Every change is a step that can be undone.
 */
class CutoutState(
    val image: SamImage,
    private val segmenter: SamSegmenter,
    private val scope: CoroutineScope,
    initial: SamPrompt?,
) {
    private data class Step(
        val prompt: SamPrompt?,
        val erase: List<List<PromptPoint>> = emptyList(),
        val size: Int? = null,
    )

    private val history = mutableStateListOf(Step(null))
    private var options: List<FloatArray> = emptyList()
    private var job: Job? = null

    var overlay by mutableStateOf<Bitmap?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var optionCount by mutableIntStateOf(0)
        private set
    var selectedSize by mutableIntStateOf(0)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Eraser mode: strokes remove things instead of choosing them. */
    var erasing by mutableStateOf(false)

    val canUndo: Boolean get() = history.size > 1
    val hasSelection: Boolean get() = optionCount > 0 && !busy
    val canErase: Boolean get() = history.last().prompt != null

    init {
        if (initial != null) push(Step(initial))
    }

    fun select(prompt: SamPrompt) {
        erasing = false
        push(Step(prompt))
    }

    fun erase(stroke: List<PromptPoint>) {
        val current = history.last()
        if (current.prompt == null || stroke.isEmpty()) return
        push(current.copy(erase = current.erase + listOf(stroke), size = null))
    }

    fun chooseSize(index: Int) {
        if (index == selectedSize || index !in options.indices) return
        history.add(history.last().copy(size = index))
        show(index)
    }

    fun undo() {
        if (!canUndo) return
        val removed = history.removeAt(history.lastIndex)
        val step = history.last()
        // Only the size changed: no need to run the model again.
        if (removed.copy(size = step.size) == step && options.isNotEmpty()) {
            show(step.size ?: defaultIndex)
        } else {
            run(step)
        }
    }

    /** The sticker for the current selection, or null if nothing is selected. */
    suspend fun sticker(): Bitmap? {
        val alpha = options.getOrNull(selectedSize) ?: return null
        return withContext(Dispatchers.Default) { StickerMaker.make(image.bitmap, alpha) }
    }

    private var defaultIndex = 0
    private var runToken: Any? = null

    private fun push(step: Step) {
        history.add(step)
        run(step)
    }

    private fun run(step: Step) {
        job?.cancel()
        val prompt = step.prompt
        if (prompt == null) {
            options = emptyList()
            optionCount = 0
            overlay = null
            busy = false
            return
        }
        busy = true
        error = null
        val token = Any()
        runToken = token
        job = scope.launch {
            try {
                val result = segmenter.segmentOptions(image, prompt, step.erase)
                options = result.alphas
                defaultIndex = result.defaultIndex
                optionCount = options.size
                show(step.size?.takeIf { it in options.indices } ?: result.defaultIndex)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.message ?: e.javaClass.simpleName
            } finally {
                // A newer run may have started; only the latest one clears the spinner.
                if (runToken === token) busy = false
            }
        }
    }

    private fun show(index: Int) {
        selectedSize = index
        val alpha = options.getOrNull(index)
        overlay = alpha?.let { maskOverlay(it, image.bitmap.width, image.bitmap.height) }
    }
}

@Composable
fun rememberCutoutState(image: SamImage, segmenter: SamSegmenter, initial: SamPrompt?): CutoutState {
    val scope = rememberCoroutineScope()
    return remember(image) { CutoutState(image, segmenter, scope, initial) }
}
