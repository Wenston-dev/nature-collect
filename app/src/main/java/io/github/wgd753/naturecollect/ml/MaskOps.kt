package io.github.wgd753.naturecollect.ml

/** Pure mask utilities, kept free of Android types so they can be unit tested. */
object MaskOps {
    /**
     * Turns SAM logits into a clean sticker alpha. SAM's mask is upsampled from
     * 256², so around uncertain areas (sky between branches) its logits hover
     * near zero and a soft sigmoid leaves translucent, blocky smears. Instead:
     * hard threshold, keep only what's connected to the prompt, fill pinholes,
     * open away wisps and the upsampling grid, then re-antialias the contour.
     */
    fun toAlpha(logits: FloatArray, w: Int, h: Int, seeds: List<PromptPoint>): FloatArray {
        val n = w * h
        var mask = BooleanArray(n) { logits[it] > 0f }
        mask = connectedTo(mask, w, h, seeds)
        // Opening by 1px: drops specks and the upsampling grid but keeps twigs and antennae.
        val r = 1
        mask = threshold(boxBlur(toFloat(mask), w, h, r), 0.999f)
        mask = threshold(boxBlur(toFloat(mask), w, h, r), 0.001f)
        mask = connectedTo(mask, w, h, seeds)
        fillHoles(mask, w, h, maxHole = n / 600)
        // Smooth, ~2px antialiased edge instead of stair steps.
        val soft = boxBlur(boxBlur(toFloat(mask), w, h, 1), w, h, 1)
        return FloatArray(n) { smoothstep(0.3f, 0.7f, soft[it]) }
    }

    /** Separable box blur with clamped edges; two passes approximate a gaussian. */
    fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        if (r <= 0) return src.copyOf()
        val tmp = FloatArray(w * h)
        val out = FloatArray(w * h)
        val win = (2 * r + 1).toFloat()
        for (y in 0 until h) {
            val row = y * w
            var sum = 0f
            for (k in -r..r) sum += src[row + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[row + x] = sum / win
                sum += src[row + (x + r + 1).coerceAtMost(w - 1)] - src[row + (x - r).coerceAtLeast(0)]
            }
        }
        for (x in 0 until w) {
            var sum = 0f
            for (k in -r..r) sum += tmp[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = sum / win
                sum += tmp[(y + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x]
            }
        }
        return out
    }

    /** Fills background regions not reachable from the image border that are smaller than [maxHole] pixels. */
    fun fillHoles(mask: BooleanArray, w: Int, h: Int, maxHole: Int) {
        val seen = BooleanArray(w * h)
        val stack = IntArray(w * h)
        val region = IntArray(w * h)
        for (start in 0 until w * h) {
            if (mask[start] || seen[start]) continue
            var top = 0
            var count = 0
            var touchesEdge = false
            stack[top++] = start
            seen[start] = true
            while (top > 0) {
                val i = stack[--top]
                region[count++] = i
                val x = i % w
                val y = i / w
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) touchesEdge = true
                if (x > 0 && !mask[i - 1] && !seen[i - 1]) { seen[i - 1] = true; stack[top++] = i - 1 }
                if (x < w - 1 && !mask[i + 1] && !seen[i + 1]) { seen[i + 1] = true; stack[top++] = i + 1 }
                if (y > 0 && !mask[i - w] && !seen[i - w]) { seen[i - w] = true; stack[top++] = i - w }
                if (y < h - 1 && !mask[i + w] && !seen[i + w]) { seen[i + w] = true; stack[top++] = i + w }
            }
            if (!touchesEdge && count <= maxHole) {
                for (k in 0 until count) mask[region[k]] = true
            }
        }
    }

    private fun toFloat(mask: BooleanArray) = FloatArray(mask.size) { if (mask[it]) 1f else 0f }

    private fun threshold(values: FloatArray, t: Float) = BooleanArray(values.size) { values[it] > t }

    fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
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

}
