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

    /** Bilinear resize of a single-channel map. */
    fun resize(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray {
        val out = FloatArray(dw * dh)
        val fx = sw.toFloat() / dw
        val fy = sh.toFloat() / dh
        for (y in 0 until dh) {
            val sy = ((y + 0.5f) * fy - 0.5f).coerceIn(0f, sh - 1f)
            val y0 = sy.toInt()
            val y1 = minOf(y0 + 1, sh - 1)
            val ty = sy - y0
            for (x in 0 until dw) {
                val sx = ((x + 0.5f) * fx - 0.5f).coerceIn(0f, sw - 1f)
                val x0 = sx.toInt()
                val x1 = minOf(x0 + 1, sw - 1)
                val tx = sx - x0
                val top = src[y0 * sw + x0] * (1 - tx) + src[y0 * sw + x1] * tx
                val bottom = src[y1 * sw + x0] * (1 - tx) + src[y1 * sw + x1] * tx
                out[y * dw + x] = top * (1 - ty) + bottom * ty
            }
        }
        return out
    }

    /** Labels 4-connected regions of [mask]; returns the label map (0 = outside) and the region count. */
    fun label(mask: BooleanArray, w: Int, h: Int): Pair<IntArray, Int> {
        val labels = IntArray(w * h)
        val stack = IntArray(w * h)
        var count = 0
        for (start in 0 until w * h) {
            if (!mask[start] || labels[start] != 0) continue
            count++
            var top = 0
            stack[top++] = start
            labels[start] = count
            while (top > 0) {
                val i = stack[--top]
                val x = i % w
                if (x > 0 && mask[i - 1] && labels[i - 1] == 0) { labels[i - 1] = count; stack[top++] = i - 1 }
                if (x < w - 1 && mask[i + 1] && labels[i + 1] == 0) { labels[i + 1] = count; stack[top++] = i + 1 }
                if (i >= w && mask[i - w] && labels[i - w] == 0) { labels[i - w] = count; stack[top++] = i - w }
                if (i < w * (h - 1) && mask[i + w] && labels[i + w] == 0) { labels[i + w] = count; stack[top++] = i + w }
            }
        }
        return labels to count
    }

    private fun salientMask(saliency: FloatArray, peak: Float) =
        BooleanArray(saliency.size) { saliency[it] > maxOf(0.5f * peak, 0.25f) }

    /**
     * The photo's main subject according to a saliency map in 0..1: the strongest
     * salient region, weighted towards the middle of the frame when [centerWeight]
     * is set. Inside a lasso ([roi]) every sizeable salient region is kept, since
     * the loop already says where to look. Null when nothing stands out.
     */
    fun salientSubject(saliency: FloatArray, w: Int, h: Int, roi: BooleanArray? = null, centerWeight: Boolean = true): BooleanArray? {
        val s = if (roi == null) saliency else FloatArray(saliency.size) { if (roi[it]) saliency[it] else 0f }
        val peak = s.max()
        if (peak < 0.2f) return null
        val (labels, count) = label(salientMask(s, peak), w, h)
        if (count == 0) return null
        val mass = DoubleArray(count + 1)
        val size = IntArray(count + 1)
        val sumX = DoubleArray(count + 1)
        val sumY = DoubleArray(count + 1)
        for (i in labels.indices) {
            val l = labels[i]
            if (l == 0) continue
            mass[l] += s[i].toDouble()
            size[l]++
            sumX[l] += (i % w).toDouble()
            sumY[l] += (i / w).toDouble()
        }
        var best = 0
        var bestScore = -1.0
        for (l in 1..count) {
            if (size[l] < w * h / 1000) continue
            var score = mass[l]
            if (centerWeight) {
                val cx = sumX[l] / size[l] / w - 0.5
                val cy = sumY[l] / size[l] / h - 0.5
                score *= kotlin.math.exp(-(cx * cx + cy * cy) / (2 * 0.3 * 0.3))
            }
            if (score > bestScore) {
                bestScore = score
                best = l
            }
        }
        if (best == 0) return null
        val keep = BooleanArray(w * h) { labels[it] == best }
        if (roi != null) {
            for (i in labels.indices) if (labels[i] != 0 && size[labels[i]] > size[best] / 5) keep[i] = true
        }
        return keep
    }

    /** The salient region under a tap, or null if the tap isn't on anything that stands out. */
    fun salientAt(saliency: FloatArray, w: Int, h: Int, seed: PromptPoint): BooleanArray? {
        val peak = saliency.max()
        if (peak < 0.2f) return null
        val mask = salientMask(saliency, peak)
        val x = seed.x.toInt().coerceIn(0, w - 1)
        val y = seed.y.toInt().coerceIn(0, h - 1)
        if (!mask[y * w + x]) return null
        return connectedTo(mask, w, h, listOf(seed))
    }

    /** The point deepest inside [mask] (largest distance to its edge), a safe place for a positive prompt. */
    fun interiorPoint(mask: BooleanArray, w: Int, h: Int): PromptPoint {
        // Two-pass chamfer distance transform.
        val inf = Int.MAX_VALUE / 4
        val d = IntArray(w * h) { if (mask[it]) inf else 0 }
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (d[i] == 0) continue
            var v = d[i]
            if (x > 0) v = minOf(v, d[i - 1] + 3) else v = minOf(v, 3)
            if (y > 0) v = minOf(v, d[i - w] + 3) else v = minOf(v, 3)
            if (x > 0 && y > 0) v = minOf(v, d[i - w - 1] + 4)
            if (x < w - 1 && y > 0) v = minOf(v, d[i - w + 1] + 4)
            d[i] = v
        }
        var best = 0
        for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
            val i = y * w + x
            if (d[i] == 0) continue
            var v = d[i]
            if (x < w - 1) v = minOf(v, d[i + 1] + 3) else v = minOf(v, 3)
            if (y < h - 1) v = minOf(v, d[i + w] + 3) else v = minOf(v, 3)
            if (x < w - 1 && y < h - 1) v = minOf(v, d[i + w + 1] + 4)
            if (x > 0 && y < h - 1) v = minOf(v, d[i + w - 1] + 4)
            d[i] = v
            if (v > d[best]) best = i
        }
        return PromptPoint((best % w).toFloat(), (best / w).toFloat())
    }

    /** Intersection over union between a soft alpha (thresholded at 0.5) and a mask. */
    fun iou(alpha: FloatArray, mask: BooleanArray): Float {
        var inter = 0
        var union = 0
        for (i in alpha.indices) {
            val a = alpha[i] > 0.5f
            if (a && mask[i]) inter++
            if (a || mask[i]) union++
        }
        return if (union == 0) 0f else inter.toFloat() / union
    }
}
