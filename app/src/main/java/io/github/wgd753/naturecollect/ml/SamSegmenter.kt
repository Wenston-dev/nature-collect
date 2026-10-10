package io.github.wgd753.naturecollect.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/** A tap on the image, in the coordinates of [SamImage.bitmap]. */
data class PromptPoint(val x: Float, val y: Float, val positive: Boolean = true)

/** What the user pointed at: taps, or a loop drawn around the subject. */
sealed interface SamPrompt {
    data class Points(val points: List<PromptPoint>) : SamPrompt

    /** A freehand loop, in bitmap coordinates. Its bounding box becomes SAM's box prompt. */
    data class Lasso(val path: List<PromptPoint>) : SamPrompt
}

/** Candidate masks from one prompt, smallest first, and which one to show by default. */
class SegmentResult(val alphas: List<FloatArray>, val defaultIndex: Int)

/** An image that has been run through the encoder, ready for fast prompting. */
class SamImage internal constructor(
    /** The image scaled so its longest side is [SamSegmenter.INPUT_SIZE]. */
    val bitmap: Bitmap,
    internal val embedding: FloatArray,
)

/**
 * MobileSAM on ONNX Runtime. The image encoder runs once per photo (the slow
 * part); each tap then only runs the small prompt decoder.
 *
 * The models are exported by tools/export_mobilesam_onnx.py: the encoder takes
 * a normalised, zero-padded [1, 3, 1024, 1024] tensor and returns
 * [1, 256, 64, 64] embeddings; the decoder is SAM's standard ONNX decoder
 * returning all four candidate masks with their predicted scores.
 */
class SamSegmenter(private val context: Context) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val mutex = Mutex()
    private var encoder: OrtSession? = null
    private var decoder: OrtSession? = null

    private suspend fun sessions(): Pair<OrtSession, OrtSession> = mutex.withLock {
        val enc = encoder ?: createSession(ENCODER_ASSET).also { encoder = it }
        val dec = decoder ?: createSession(DECODER_ASSET).also { decoder = it }
        enc to dec
    }

    /** Loads the models in the background so the first photo is faster. */
    suspend fun warmUp() {
        withContext(Dispatchers.Default) { runCatching { sessions() } }
    }

    private fun createSession(asset: String): OrtSession {
        // ONNX Runtime wants a file path to memory-map; copy the asset once.
        val file = File(context.noBackupFilesDir, asset.substringAfterLast('/'))
        if (!file.exists() || file.length() == 0L) {
            val tmp = File(file.path + ".tmp")
            context.assets.open(asset).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(file)
        }
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(max(1, Runtime.getRuntime().availableProcessors() / 2))
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        return env.createSession(file.path, options)
    }

    /** Runs the image encoder. Takes about a second or two on a recent phone. */
    suspend fun encode(source: Bitmap): SamImage = withContext(Dispatchers.Default) {
        val (enc, _) = sessions()
        val scale = INPUT_SIZE.toFloat() / max(source.width, source.height)
        val w = (source.width * scale).roundToInt().coerceIn(1, INPUT_SIZE)
        val h = (source.height * scale).roundToInt().coerceIn(1, INPUT_SIZE)
        val scaled = Bitmap.createScaledBitmap(source, w, h, true).copy(Bitmap.Config.ARGB_8888, false)

        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        val plane = INPUT_SIZE * INPUT_SIZE
        // Padding stays 0, matching SAM's normalise-then-pad preprocessing.
        val input = FloatArray(3 * plane)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = pixels[y * w + x]
                val i = y * INPUT_SIZE + x
                input[i] = (((p shr 16) and 0xFF) - MEAN[0]) / STD[0]
                input[plane + i] = (((p shr 8) and 0xFF) - MEAN[1]) / STD[1]
                input[2 * plane + i] = ((p and 0xFF) - MEAN[2]) / STD[2]
            }
        }
        val embedding = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(input),
            longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()),
        ).use { tensor ->
            enc.run(mapOf(ENCODER_INPUT to tensor)).use { result ->
                val out = result[0] as OnnxTensor
                val buffer = out.floatBuffer
                FloatArray(buffer.remaining()).also { buffer.get(it) }
            }
        }
        SamImage(scaled, embedding)
    }

    /** Shorthand for a tap prompt. */
    suspend fun segment(image: SamImage, points: List<PromptPoint>): FloatArray =
        segment(image, SamPrompt.Points(points))

    /** The default (best) mask for [prompt] as a soft alpha the size of [image]'s bitmap. */
    suspend fun segment(image: SamImage, prompt: SamPrompt): FloatArray {
        val result = segmentOptions(image, prompt)
        return result.alphas.getOrNull(result.defaultIndex) ?: FloatArray(image.bitmap.width * image.bitmap.height)
    }

    /**
     * Runs the decoder and returns SAM's candidate masks, smallest to largest,
     * so the user can widen or narrow the selection. The default leans towards
     * the larger candidates, because SAM's single "best" mask often grabs just
     * one part of an object (the cap of a bottle, one petal).
     *
     * [erase] strokes remove things: their points go to SAM as negative
     * prompts, and the pixels under each stroke are cleared like an eraser.
     */
    suspend fun segmentOptions(
        image: SamImage,
        prompt: SamPrompt,
        erase: List<List<PromptPoint>> = emptyList(),
    ): SegmentResult = withContext(Dispatchers.Default) {
        val (_, dec) = sessions()
        val w = image.bitmap.width
        val h = image.bitmap.height
        val negatives = erase.flatMap { sample(it, 6) }.map { it.copy(positive = false) }
        val coordList = ArrayList<Float>()
        val labelList = ArrayList<Float>()
        val singleTap: Boolean
        when (prompt) {
            is SamPrompt.Points -> {
                (prompt.points + negatives).forEach { p ->
                    coordList += p.x
                    coordList += p.y
                    labelList += if (p.positive) 1f else 0f
                }
                // SAM expects a padding point with label -1 when no box is given.
                coordList += 0f
                coordList += 0f
                labelList += -1f
                singleTap = prompt.points.size == 1 && negatives.isEmpty()
            }
            is SamPrompt.Lasso -> {
                // Box corners use labels 2 (top-left) and 3 (bottom-right); no padding point.
                val xs = prompt.path.map { it.x.coerceIn(0f, w - 1f) }
                val ys = prompt.path.map { it.y.coerceIn(0f, h - 1f) }
                coordList += listOf(xs.min(), ys.min(), xs.max(), ys.max())
                labelList += listOf(2f, 3f)
                negatives.forEach { p ->
                    coordList += p.x
                    coordList += p.y
                    labelList += 0f
                }
                singleTap = false
            }
        }
        val coords = coordList.toFloatArray()
        val labels = labelList.toFloatArray()
        val n = labels.size

        val inputs = mapOf(
            "image_embeddings" to OnnxTensor.createTensor(env, FloatBuffer.wrap(image.embedding), longArrayOf(1, 256, 64, 64)),
            "point_coords" to OnnxTensor.createTensor(env, FloatBuffer.wrap(coords), longArrayOf(1, n.toLong(), 2)),
            "point_labels" to OnnxTensor.createTensor(env, FloatBuffer.wrap(labels), longArrayOf(1, n.toLong())),
            "mask_input" to OnnxTensor.createTensor(env, FloatBuffer.wrap(FloatArray(256 * 256)), longArrayOf(1, 1, 256, 256)),
            "has_mask_input" to OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(0f)), longArrayOf(1)),
            "orig_im_size" to OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(h.toFloat(), w.toFloat())), longArrayOf(2)),
        )
        val eraseMask = if (erase.isEmpty()) null else strokeMask(erase, w, h)
        val lasso = (prompt as? SamPrompt.Lasso)?.let { lassoMask(it.path, w, h) }
        val seeds = (prompt as? SamPrompt.Points)?.points?.filter { it.positive }.orEmpty()
        try {
            dec.run(inputs).use { result ->
                val masks = (result[0] as OnnxTensor).floatBuffer
                val scores = (result[1] as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } }
                val count = scores.size
                val plane = w * h
                // With one tap SAM's own advice is to use the three multi-mask outputs (1..3);
                // with a box or several points the first output is meant to be used too.
                val indices = if (singleTap && count == 4) 1 until 4 else 0 until count
                val candidates = indices.map { index ->
                    val logits = FloatArray(plane)
                    masks.position(index * plane)
                    masks.get(logits)
                    val alpha = MaskOps.toAlpha(logits, w, h, seeds)
                    for (i in alpha.indices) {
                        if ((lasso != null && !lasso[i]) || (eraseMask != null && eraseMask[i])) alpha[i] = 0f
                    }
                    Candidate(alpha, scores[index], alpha.count { it > 0.5f } / plane.toFloat())
                }
                pick(candidates, pointPrompt = prompt is SamPrompt.Points)
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    private class Candidate(val alpha: FloatArray, val score: Float, val area: Float)

    private fun pick(all: List<Candidate>, pointPrompt: Boolean): SegmentResult {
        val best = all.maxOfOrNull { it.score } ?: return SegmentResult(emptyList(), 0)
        var usable = all.filter { it.area > 0.002f && it.score >= best - 0.15f }
        // A tap that "selects" almost the whole photo has picked the background.
        if (pointPrompt) usable = usable.filter { it.area < 0.85f }.ifEmpty { usable }
        if (usable.isEmpty()) usable = all.sortedByDescending { it.score }.take(1)
        val sorted = usable.sortedBy { it.area }
        // Merge near-duplicates, keeping the higher-scoring one.
        val distinct = ArrayList<Candidate>()
        for (c in sorted) {
            val last = distinct.lastOrNull()
            if (last != null && c.area < last.area * 1.08f + 0.002f) {
                if (c.score > last.score) distinct[distinct.lastIndex] = c
            } else {
                distinct += c
            }
        }
        val preferred = distinct.indices.filter { distinct[it].score >= best - 0.06f && (!pointPrompt || distinct[it].area < 0.7f) }
        val default = preferred.maxOrNull() ?: distinct.indices.maxBy { distinct[it].score }
        return SegmentResult(distinct.map { it.alpha }, default)
    }

    /** Up to [count] points spread evenly along a stroke. */
    private fun sample(stroke: List<PromptPoint>, count: Int): List<PromptPoint> {
        if (stroke.size <= count) return stroke
        return List(count) { i -> stroke[(i * (stroke.size - 1)) / (count - 1)] }
    }

    /** Pixels under the eraser strokes. */
    private fun strokeMask(strokes: List<List<PromptPoint>>, w: Int, h: Int): BooleanArray {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = max(w, h) * ERASER_WIDTH
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        for (stroke in strokes) {
            if (stroke.size == 1) {
                canvas.drawCircle(stroke[0].x, stroke[0].y, paint.strokeWidth / 2, paint.apply { style = android.graphics.Paint.Style.FILL })
                paint.style = android.graphics.Paint.Style.STROKE
                continue
            }
            val path = android.graphics.Path().apply {
                stroke.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            }
            canvas.drawPath(path, paint)
        }
        return readMask(bitmap, w, h)
    }

    private fun readMask(bitmap: Bitmap, w: Int, h: Int): BooleanArray {
        val bytes = java.nio.ByteBuffer.allocate(bitmap.rowBytes * h)
        bitmap.copyPixelsToBuffer(bytes)
        val row = bitmap.rowBytes
        bitmap.recycle()
        return BooleanArray(w * h) { i -> bytes.get((i / w) * row + i % w).toInt() != 0 }
    }

    /** Rasterises the loop, grown by a few percent so a loose circle doesn't clip the subject. */
    private fun lassoMask(path: List<PromptPoint>, w: Int, h: Int): BooleanArray {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val canvas = android.graphics.Canvas(bitmap)
        val shape = android.graphics.Path().apply {
            path.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            close()
        }
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL_AND_STROKE
            strokeWidth = max(w, h) * 0.06f
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        canvas.drawPath(shape, paint)
        return readMask(bitmap, w, h)
    }

    companion object {
        const val INPUT_SIZE = 1024

        /** Eraser brush width as a fraction of the image's longer side. */
        const val ERASER_WIDTH = 0.045f
        private const val ENCODER_ASSET = "sam/mobile_sam_encoder.onnx"
        private const val DECODER_ASSET = "sam/mobile_sam_decoder.onnx"
        private const val ENCODER_INPUT = "image"
        private val MEAN = floatArrayOf(123.675f, 116.28f, 103.53f)
        private val STD = floatArrayOf(58.395f, 57.12f, 57.375f)
    }
}
