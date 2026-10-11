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
import io.github.wgd753.naturecollect.ml.SegmentResult
import io.github.wgd753.naturecollect.ml.StickerMaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The selection being edited on a photo: everything that was circled or tapped,
 * what was erased and which size was picked. Every change is a step that can
 * be undone.
 *
 * The first circle or tap chooses the subject; later ones add to it, so a
 * subject the model splits apart can be gathered up piece by piece. The
 * automatic first guess is the exception: a tap or circle replaces it.
 */
class CutoutState(
    val image: SamImage,
    private val segmenter: SamSegmenter,
    private val scope: CoroutineScope,
    initial: SamPrompt?,
) {
    /** One circle or tap, and the size step picked for it (null = the default). */
    private data class Part(val prompt: SamPrompt, val size: Int? = null)

    private data class Step(
        val parts: List<Part> = emptyList(),
        val erase: List<List<PromptPoint>> = emptyList(),
    )

    private val history = mutableStateListOf(Step())
    // The size options of the newest part, already merged with the earlier parts.
    private var options: List<FloatArray> = emptyList()
    private var job: Job? = null

    // Recent decoder results, so undo and adding a part don't redo the old ones.
    private val cache = object : LinkedHashMap<Pair<SamPrompt, List<List<PromptPoint>>>, SegmentResult>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<SamPrompt, List<List<PromptPoint>>>, SegmentResult>) = size > 6
    }

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

    /** How many times the user has circled or tapped; after a couple, offer the whole photo. */
    var attempts by mutableIntStateOf(0)
        private set

    /** Eraser mode: strokes remove things instead of choosing them. */
    var erasing by mutableStateOf(false)

    val canUndo: Boolean get() = history.size > 1
    val hasSelection: Boolean get() = optionCount > 0 && !busy
    val canErase: Boolean get() = history.last().parts.isNotEmpty()

    /** The next circle or tap adds to the selection instead of replacing it. */
    val adding: Boolean get() = history.last().let { !replaces(it) }

    init {
        if (initial != null) push(Step(listOf(Part(initial))))
    }

    fun select(prompt: SamPrompt) {
        erasing = false
        attempts++
        val current = history.last()
        push(if (replaces(current)) Step(listOf(Part(prompt))) else current.copy(parts = current.parts + Part(prompt)))
    }

    fun erase(stroke: List<PromptPoint>) {
        val current = history.last()
        if (current.parts.isEmpty() || stroke.isEmpty()) return
        push(current.copy(erase = current.erase + listOf(stroke)))
    }

    fun chooseSize(index: Int) {
        val current = history.last()
        if (index == selectedSize || index !in options.indices || current.parts.isEmpty()) return
        val parts = current.parts.toMutableList()
        parts[parts.lastIndex] = parts.last().copy(size = index)
        history.add(current.copy(parts = parts))
        show(index)
    }

    fun undo() {
        if (!canUndo) return
        history.removeAt(history.lastIndex)
        run(history.last())
    }

    /** The sticker for the current selection, or null if nothing is selected. */
    suspend fun sticker(): Bitmap? {
        val alpha = options.getOrNull(selectedSize) ?: return null
        return withContext(Dispatchers.Default) { StickerMaker.make(image.bitmap, alpha) }
    }

    private var runToken: Any? = null

    private fun replaces(step: Step) = step.parts.isEmpty() ||
        (step.parts.singleOrNull()?.prompt == SamPrompt.Auto && step.erase.isEmpty())

    private fun push(step: Step) {
        history.add(step)
        run(step)
    }

    private fun run(step: Step) {
        job?.cancel()
        if (step.parts.isEmpty()) {
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
                val results = step.parts.map { result(it.prompt, step.erase) }
                // Earlier parts keep the size they had; the size steps apply to the newest one.
                val earlier = step.parts.dropLast(1).zip(results).mapNotNull { (part, result) ->
                    result.alphas.getOrNull(part.size ?: result.defaultIndex)
                }
                val last = results.last()
                options = withContext(Dispatchers.Default) {
                    last.alphas.map { alpha -> earlier.fold(alpha, ::union) }
                }
                optionCount = options.size
                show(step.parts.last().size?.takeIf { it in options.indices } ?: last.defaultIndex)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.message ?: e.javaClass.simpleName
            } finally {
                // A newer run may have started; only the latest one clears the spinner.
                if (runToken === token) busy = false
            }
        }
    }

    private suspend fun result(prompt: SamPrompt, erase: List<List<PromptPoint>>): SegmentResult {
        val key = prompt to erase
        cache[key]?.let { return it }
        return segmenter.segmentOptions(image, prompt, erase).also { cache[key] = it }
    }

    private fun union(a: FloatArray, b: FloatArray) = FloatArray(a.size) { maxOf(a[it], b[it]) }

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
