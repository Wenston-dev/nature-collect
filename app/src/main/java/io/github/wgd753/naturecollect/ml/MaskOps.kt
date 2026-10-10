package io.github.wgd753.naturecollect.ml

import kotlin.math.exp

/** Pure mask utilities, kept free of Android types so they can be unit tested. */
object MaskOps {
    /**
     * Converts SAM logits to a soft alpha mask and drops islands that are not
     * connected to any prompt point, so a stray blob doesn't end up on the sticker.
     */
    fun toAlpha(logits: FloatArray, w: Int, h: Int, seeds: List<PromptPoint>): FloatArray {
        val inside = BooleanArray(w * h) { logits[it] > 0f }
        val keep = connectedTo(inside, w, h, seeds)
        return FloatArray(w * h) { i ->
            if (!keep[i] && inside[i]) 0f else sigmoid(logits[i] * EDGE_SHARPNESS).let { if (it < 0.02f) 0f else it }
        }
    }

    /** Flood-fills from each seed; falls back to the whole mask if no seed lands inside it. */
    fun connectedTo(inside: BooleanArray, w: Int, h: Int, seeds: List<PromptPoint>): BooleanArray {
        val keep = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var anySeed = false
        for (seed in seeds) {
            val sx = seed.x.toInt().coerceIn(0, w - 1)
            val sy = seed.y.toInt().coerceIn(0, h - 1)
            val start = nearestInside(inside, w, h, sx, sy) ?: continue
            if (keep[start]) continue
            anySeed = true
            var top = 0
            stack[top++] = start
            keep[start] = true
            while (top > 0) {
                val i = stack[--top]
                val x = i % w
                val y = i / w
                if (x > 0) push(i - 1, inside, keep, stack, top).also { top = it }
                if (x < w - 1) push(i + 1, inside, keep, stack, top).also { top = it }
                if (y > 0) push(i - w, inside, keep, stack, top).also { top = it }
                if (y < h - 1) push(i + w, inside, keep, stack, top).also { top = it }
            }
        }
        return if (anySeed) keep else inside
    }

    private fun push(i: Int, inside: BooleanArray, keep: BooleanArray, stack: IntArray, top: Int): Int {
        if (!inside[i] || keep[i]) return top
        keep[i] = true
        stack[top] = i
        return top + 1
    }

    /** The seed itself if inside the mask, else the closest inside pixel within a small radius. */
    private fun nearestInside(inside: BooleanArray, w: Int, h: Int, sx: Int, sy: Int): Int? {
        if (inside[sy * w + sx]) return sy * w + sx
        val radius = maxOf(w, h) / 20
        var best: Int? = null
        var bestDist = Int.MAX_VALUE
        for (y in maxOf(0, sy - radius)..minOf(h - 1, sy + radius)) {
            for (x in maxOf(0, sx - radius)..minOf(w - 1, sx + radius)) {
                val d = (x - sx) * (x - sx) + (y - sy) * (y - sy)
                if (d < bestDist && inside[y * w + x]) {
                    bestDist = d
                    best = y * w + x
                }
            }
        }
        return best
    }

    /** Bounding box [left, top, right, bottom] (exclusive) of pixels above [threshold], or null if empty. */
    fun bounds(alpha: FloatArray, w: Int, h: Int, threshold: Float = 0.5f): IntArray? {
        var left = w
        var top = h
        var right = -1
        var bottom = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (alpha[y * w + x] > threshold) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right < 0) null else intArrayOf(left, top, right + 1, bottom + 1)
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))

    /** SAM logits are steep around 0; scaling keeps about a pixel of feathering. */
    private const val EDGE_SHARPNESS = 1.5f
}
