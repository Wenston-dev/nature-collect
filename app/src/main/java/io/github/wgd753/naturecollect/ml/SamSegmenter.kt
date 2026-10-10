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
 * [1, 256, 64, 64] embeddings; the decoder is SAM's standard ONNX decoder with
 * a single best mask.
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

    /**
     * Returns a soft alpha mask (0..1) the size of [image]'s bitmap for the
     * object under [points].
     */
    suspend fun segment(image: SamImage, points: List<PromptPoint>): FloatArray = withContext(Dispatchers.Default) {
        val (_, dec) = sessions()
        val w = image.bitmap.width
        val h = image.bitmap.height
        // SAM expects a padding point with label -1 when no box is given.
        val n = points.size + 1
        val coords = FloatArray(n * 2)
        val labels = FloatArray(n)
        points.forEachIndexed { i, p ->
            coords[i * 2] = p.x
            coords[i * 2 + 1] = p.y
            labels[i] = if (p.positive) 1f else 0f
        }
        labels[n - 1] = -1f

        val inputs = mapOf(
            "image_embeddings" to OnnxTensor.createTensor(env, FloatBuffer.wrap(image.embedding), longArrayOf(1, 256, 64, 64)),
            "point_coords" to OnnxTensor.createTensor(env, FloatBuffer.wrap(coords), longArrayOf(1, n.toLong(), 2)),
            "point_labels" to OnnxTensor.createTensor(env, FloatBuffer.wrap(labels), longArrayOf(1, n.toLong())),
            "mask_input" to OnnxTensor.createTensor(env, FloatBuffer.wrap(FloatArray(256 * 256)), longArrayOf(1, 1, 256, 256)),
            "has_mask_input" to OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(0f)), longArrayOf(1)),
            "orig_im_size" to OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(h.toFloat(), w.toFloat())), longArrayOf(2)),
        )
        try {
            dec.run(inputs).use { result ->
                val masks = result[0] as OnnxTensor
                val buffer = masks.floatBuffer
                val logits = FloatArray(w * h).also { buffer.get(it) }
                MaskOps.toAlpha(logits, w, h, points.filter { it.positive })
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    companion object {
        const val INPUT_SIZE = 1024
        private const val ENCODER_ASSET = "sam/mobile_sam_encoder.onnx"
        private const val DECODER_ASSET = "sam/mobile_sam_decoder.onnx"
        private const val ENCODER_INPUT = "image"
        private val MEAN = floatArrayOf(123.675f, 116.28f, 103.53f)
        private val STD = floatArrayOf(58.395f, 57.12f, 57.375f)
    }
}
