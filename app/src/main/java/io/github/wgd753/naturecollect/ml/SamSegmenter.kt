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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** A tap on the image, in the coordinates of [SamImage.bitmap]. */
data class PromptPoint(val x: Float, val y: Float, val positive: Boolean = true)

/** What the user pointed at: nothing yet, taps, or a loop drawn around the subject. */
sealed interface SamPrompt {
    /** No input: find the photo's main subject. */
    data object Auto : SamPrompt

    data class Points(val points: List<PromptPoint>) : SamPrompt

    /** A freehand loop, in bitmap coordinates; the subject is looked for inside it. */
    data class Lasso(val path: List<PromptPoint>) : SamPrompt
}

/** Candidate masks from one prompt, smallest first, and which one to show by default. */
class SegmentResult(val alphas: List<FloatArray>, val defaultIndex: Int)

/** An image that has been run through the encoder, ready for fast prompting. */
class SamImage internal constructor(
    /** The image scaled so its longest side is [SamSegmenter.INPUT_SIZE]. */
    val bitmap: Bitmap,
    internal val embedding: FloatArray,
    /** The decoder's two high-resolution feature maps, [1, 32, 256, 256] and [1, 64, 128, 128]. */
    internal val highRes0: FloatArray,
    internal val highRes1: FloatArray,
    /** How much each pixel of [bitmap] stands out as a subject, 0..1. */
    internal val saliency: FloatArray,
)

/**
 * EdgeTAM (an on-device SAM 2) on ONNX Runtime. The image encoder runs once per
 * photo (the slow part); each tap then only runs the small prompt decoder.
 *
 * The models are exported by tools/export_edgetam_onnx.py: the encoder takes
 * the photo stretched to a normalised [1, 3, 1024, 1024] tensor and returns
 * [1, 256, 64, 64] embeddings plus two high-resolution feature maps; the
 * decoder takes SAM's usual prompts in image pixels and returns all four
 * candidate masks with their predicted scores.
 *
 * SAM-style models only segment what they are pointed at, and a lone tap or a
 * loose box often lands on a petal or the background. So each photo also goes
 * through U²-Netp, a small salient-object detector: its map says where the
 * subject is, becomes a tight box plus an inner point for EdgeTAM, and decides
 * which of EdgeTAM's candidate masks to show.
 */
class SamSegmenter(private val context: Context) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val mutex = Mutex()
    private var encoder: OrtSession? = null
    private var decoder: OrtSession? = null
    private var salient: OrtSession? = null

    private suspend fun sessions(): Triple<OrtSession, OrtSession, OrtSession> = mutex.withLock {
        val enc = encoder ?: createSession(ENCODER_ASSET).also { encoder = it }
        val dec = decoder ?: createSession(DECODER_ASSET).also { decoder = it }
        val sal = salient ?: createSession(SALIENCY_ASSET).also { salient = it }
        Triple(enc, dec, sal)
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

    /** Runs the image encoder. Takes well under a second on a recent phone. */
    suspend fun encode(source: Bitmap): SamImage = withContext(Dispatchers.Default) {
        val (enc, _, sal) = sessions()
        val scale = INPUT_SIZE.toFloat() / max(source.width, source.height)
        val w = (source.width * scale).roundToInt().coerceIn(1, INPUT_SIZE)
        val h = (source.height * scale).roundToInt().coerceIn(1, INPUT_SIZE)
        val scaled = Bitmap.createScaledBitmap(source, w, h, true).copy(Bitmap.Config.ARGB_8888, false)

        // SAM 2 stretches the image to a square rather than padding it.
        val square = Bitmap.createScaledBitmap(source, INPUT_SIZE, INPUT_SIZE, true).copy(Bitmap.Config.ARGB_8888, false)
        val plane = INPUT_SIZE * INPUT_SIZE
        val pixels = IntArray(plane)
        square.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        square.recycle()
        val input = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val p = pixels[i]
            input[i] = (((p shr 16) and 0xFF) - MEAN[0]) / STD[0]
            input[plane + i] = (((p shr 8) and 0xFF) - MEAN[1]) / STD[1]
            input[2 * plane + i] = ((p and 0xFF) - MEAN[2]) / STD[2]
        }
        OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(input),
            longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()),
        ).use { tensor ->
            enc.run(mapOf(ENCODER_INPUT to tensor)).use { result ->
                fun output(name: String): FloatArray {
                    val buffer = (result[name].get() as OnnxTensor).floatBuffer
                    return FloatArray(buffer.remaining()).also { buffer.get(it) }
                }
                SamImage(
                    scaled,
                    output("image_embeddings"),
                    output("high_res_feats_0"),
                    output("high_res_feats_1"),
                    saliency(sal, scaled),
                )
            }
        }
    }

    /** U²-Netp's saliency map for [image], min-max normalised and resized to the image. */
    private fun saliency(session: OrtSession, image: Bitmap): FloatArray {
        val size = SALIENCY_SIZE
        val small = Bitmap.createScaledBitmap(image, size, size, true)
        val plane = size * size
        val pixels = IntArray(plane)
        small.getPixels(pixels, 0, size, 0, 0, size, size)
        if (small !== image) small.recycle()
        // As in U²-Net's own preprocessing: scale by the brightest channel value, then ImageNet mean/std.
        var peak = 1
        for (p in pixels) peak = maxOf(peak, (p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
        val input = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val p = pixels[i]
            input[i] = (((p shr 16) and 0xFF).toFloat() / peak - 0.485f) / 0.229f
            input[plane + i] = (((p shr 8) and 0xFF).toFloat() / peak - 0.456f) / 0.224f
            input[2 * plane + i] = ((p and 0xFF).toFloat() / peak - 0.406f) / 0.225f
        }
        val map = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, size.toLong(), size.toLong())).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                val buffer = (result[0] as OnnxTensor).floatBuffer
                FloatArray(plane).also { buffer.get(it) }
            }
        }
        val lo = map.min()
        val range = (map.max() - lo).coerceAtLeast(1e-6f)
        for (i in map.indices) map[i] = (map[i] - lo) / range
        return MaskOps.resize(map, size, size, image.width, image.height)
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
     * Runs the decoder and returns its candidate masks, smallest to largest,
     * so the user can widen or narrow the selection.
     *
     * With no prompt or a lasso, the salient subject (in the whole photo, or
     * inside the loop) is handed to EdgeTAM as a tight box plus an inner point,
     * and the candidate that best matches it is the default. A loose box on its
     * own makes SAM-style models pick the background, so the loop's outline is
     * only used to clip the result. A tap that lands on a salient region picks
     * the candidate matching that region; otherwise the default leans towards the
     * larger candidates, because the model's "best" mask often grabs just one
     * part of an object (the cap of a bottle, one petal).
     *
     * [erase] strokes remove things: their points go to SAM as negative
     * prompts, and the pixels under each stroke are cleared like an eraser.
     */
    suspend fun segmentOptions(
        image: SamImage,
        prompt: SamPrompt,
        erase: List<List<PromptPoint>> = emptyList(),
    ): SegmentResult = withContext(Dispatchers.Default) {
        val (_, dec, _) = sessions()
        val w = image.bitmap.width
        val h = image.bitmap.height
        val negatives = erase.flatMap { sample(it, 6) }.map { it.copy(positive = false) }
        val lasso = (prompt as? SamPrompt.Lasso)?.let { lassoMask(it.path, w, h) }
        // The salient region that should be cut out, if the saliency map has one.
        val guide = when (prompt) {
            SamPrompt.Auto -> MaskOps.salientSubject(image.saliency, w, h)
            is SamPrompt.Lasso -> MaskOps.salientSubject(image.saliency, w, h, roi = lasso, centerWeight = false)
            is SamPrompt.Points -> prompt.points.singleOrNull()?.let { MaskOps.salientAt(image.saliency, w, h, it) }
        }
        val coordList = ArrayList<Float>()
        val labelList = ArrayList<Float>()
        val singleTap: Boolean
        var seeds: List<PromptPoint> = emptyList()
        if (guide != null && prompt !is SamPrompt.Points) {
            // Box corners use labels 2 (top-left) and 3 (bottom-right), then a positive point deep inside.
            val box = MaskOps.bounds(FloatArray(w * h) { if (guide[it]) 1f else 0f }, w, h)!!
            // One point deep inside each salient blob, so a loop around several pieces keeps them all.
            val (blobs, count) = MaskOps.label(guide, w, h)
            val sizes = IntArray(count + 1).also { c -> blobs.forEach { c[it]++ } }
            val inner = (1..count).sortedByDescending { sizes[it] }.take(4).map { l ->
                MaskOps.interiorPoint(BooleanArray(w * h) { blobs[it] == l }, w, h)
            }
            coordList += listOf(box[0].toFloat(), box[1].toFloat(), box[2] - 1f, box[3] - 1f)
            labelList += listOf(2f, 3f)
            inner.forEach { p ->
                coordList += p.x
                coordList += p.y
                labelList += 1f
            }
            negatives.forEach { p ->
                coordList += p.x
                coordList += p.y
                labelList += 0f
            }
            coordList += 0f
            coordList += 0f
            labelList += -1f
            singleTap = false
            seeds = inner
        } else when (prompt) {
            SamPrompt.Auto -> {
                // Nothing stands out: fall back to whatever is in the middle of the frame.
                val center = PromptPoint(w / 2f, h / 2f)
                listOf(center).plus(negatives).forEach { p ->
                    coordList += p.x
                    coordList += p.y
                    labelList += if (p.positive) 1f else 0f
                }
                coordList += 0f
                coordList += 0f
                labelList += -1f
                singleTap = negatives.isEmpty()
                seeds = listOf(center)
            }
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
                seeds = prompt.points.filter { it.positive }
            }
            is SamPrompt.Lasso -> {
                // Box corners use labels 2 (top-left) and 3 (bottom-right).
                val xs = prompt.path.map { it.x.coerceIn(0f, w - 1f) }
                val ys = prompt.path.map { it.y.coerceIn(0f, h - 1f) }
                coordList += listOf(xs.min(), ys.min(), xs.max(), ys.max())
                labelList += listOf(2f, 3f)
                negatives.forEach { p ->
                    coordList += p.x
                    coordList += p.y
                    labelList += 0f
                }
                // Unlike SAM, SAM 2 always ends the prompt with a padding point, boxes included.
                coordList += 0f
                coordList += 0f
                labelList += -1f
                singleTap = false
            }
        }
        val coords = coordList.toFloatArray()
        val labels = labelList.toFloatArray()
        val n = labels.size

        val inputs = mapOf(
            "image_embeddings" to OnnxTensor.createTensor(env, FloatBuffer.wrap(image.embedding), longArrayOf(1, 256, 64, 64)),
            "high_res_feats_0" to OnnxTensor.createTensor(env, FloatBuffer.wrap(image.highRes0), longArrayOf(1, 32, 256, 256)),
            "high_res_feats_1" to OnnxTensor.createTensor(env, FloatBuffer.wrap(image.highRes1), longArrayOf(1, 64, 128, 128)),
            "point_coords" to OnnxTensor.createTensor(env, FloatBuffer.wrap(coords), longArrayOf(1, n.toLong(), 2)),
            "point_labels" to OnnxTensor.createTensor(env, FloatBuffer.wrap(labels), longArrayOf(1, n.toLong())),
            "mask_input" to OnnxTensor.createTensor(env, FloatBuffer.wrap(FloatArray(256 * 256)), longArrayOf(1, 1, 256, 256)),
            "has_mask_input" to OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(0f)), longArrayOf(1)),
            "orig_im_size" to OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(h.toFloat(), w.toFloat())), longArrayOf(2)),
        )
        val eraseMask = if (erase.isEmpty()) null else strokeMask(erase, w, h)
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
                    // EdgeTAM's own scores are poorly calibrated (whole objects often score
                    // lowest), so with a guide the overlap with it decides instead.
                    val score = if (guide != null) MaskOps.iou(alpha, guide) else scores[index]
                    Candidate(alpha, score, alpha.count { it > 0.5f } / plane.toFloat())
                }
                pick(candidates, pointPrompt = prompt !is SamPrompt.Lasso, guided = guide != null)
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    private class Candidate(val alpha: FloatArray, val score: Float, val area: Float)

    private fun pick(all: List<Candidate>, pointPrompt: Boolean, guided: Boolean): SegmentResult {
        val best = all.maxOfOrNull { it.score } ?: return SegmentResult(emptyList(), 0)
        // Unguided taps ignore the scores: EdgeTAM often scores the whole object lowest.
        var usable = all.filter { it.area > 0.002f && (guided || pointPrompt || it.score >= best - 0.15f) }
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
        val default = when {
            guided -> distinct.indices.maxBy { distinct[it].score }
            pointPrompt -> distinct.indices.filter { distinct[it].area < 0.7f }.maxOrNull() ?: 0
            else -> distinct.indices.filter { distinct[it].score >= best - 0.06f }.maxOrNull()
                ?: distinct.indices.maxBy { distinct[it].score }
        }
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

    /**
     * Rasterises the loop, grown by a few percent so a loose circle doesn't clip the subject.
     * A loop twisted into a figure eight pinches the subject at the crossing, so then its
     * convex hull is used: the whole area the gesture went around.
     */
    private fun lassoMask(path: List<PromptPoint>, w: Int, h: Int): BooleanArray {
        val filled = fillPolygon(path, w, h, grow = 0f)
        val area = filled.count { it }
        // A twisted loop's lobes have opposite orientation, so its signed area mostly cancels out.
        var signed = 0.0
        for (i in path.indices) {
            val a = path[i]
            val b = path[(i + 1) % path.size]
            signed += a.x.toDouble() * b.y - b.x.toDouble() * a.y
        }
        val twisted = abs(signed) / 2 < 0.6 * area
        return fillPolygon(if (twisted) convexHull(path) else path, w, h, grow = max(w, h) * 0.06f)
    }

    private fun fillPolygon(path: List<PromptPoint>, w: Int, h: Int, grow: Float): BooleanArray {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val canvas = android.graphics.Canvas(bitmap)
        val shape = android.graphics.Path().apply {
            // Non-zero winding: every part of the loop counts as inside, even where it crosses itself.
            fillType = android.graphics.Path.FillType.WINDING
            path.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            close()
        }
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = if (grow > 0f) android.graphics.Paint.Style.FILL_AND_STROKE else android.graphics.Paint.Style.FILL
            strokeWidth = grow
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        canvas.drawPath(shape, paint)
        return readMask(bitmap, w, h)
    }

    /** Andrew's monotone chain. */
    private fun convexHull(points: List<PromptPoint>): List<PromptPoint> {
        val sorted = points.sortedWith(compareBy({ it.x }, { it.y }))
        if (sorted.size < 3) return sorted
        fun cross(o: PromptPoint, a: PromptPoint, b: PromptPoint) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val hull = ArrayList<PromptPoint>()
        for (pass in 0..1) {
            val start = hull.size
            for (p in if (pass == 0) sorted else sorted.asReversed()) {
                while (hull.size >= start + 2 && cross(hull[hull.size - 2], hull[hull.size - 1], p) <= 0) hull.removeAt(hull.lastIndex)
                hull += p
            }
            hull.removeAt(hull.lastIndex)
        }
        return hull
    }

    companion object {
        const val INPUT_SIZE = 1024

        /** Eraser brush width as a fraction of the image's longer side. */
        const val ERASER_WIDTH = 0.045f
        private const val ENCODER_ASSET = "sam/edgetam_encoder.onnx"
        private const val DECODER_ASSET = "sam/edgetam_decoder.onnx"
        private const val SALIENCY_ASSET = "sam/u2netp.onnx"
        private const val SALIENCY_SIZE = 320
        private const val ENCODER_INPUT = "image"
        private val MEAN = floatArrayOf(123.675f, 116.28f, 103.53f)
        private val STD = floatArrayOf(58.395f, 57.12f, 57.375f)
    }
}
