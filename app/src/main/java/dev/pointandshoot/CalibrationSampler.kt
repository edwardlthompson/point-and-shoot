package dev.pointandshoot

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Pure-data patch sampler per BUILD_PLAN §7 ("Phase 4 - Calibration mode").
 *
 *   * [sample] takes a linear-light [RgbPlane] + a [ReferenceTarget] + the
 *     four user-tapped chart corners and returns one [PatchSample] per chart
 *     patch. Each sample carries the mean RGB + per-channel variance + a
 *     boolean rejection flag.
 *   * [sampleAt] samples a single rectangular ROI in pixel coordinates.
 *
 * The mapping from chart-relative `(u, v) in [0, 1]^2` to pixel coordinates
 * is **bilinear** between the four corners (i.e., we treat the chart as a
 * planar quad held roughly perpendicular to the camera). For perspective-
 * correct sampling the engine can wrap this with a homography step later;
 * bilinear is sufficient for the typical "phone held flat over a chart"
 * calibration pose and keeps the math allocation-free in the hot path.
 *
 * No Android imports; safe for unit testing on the JVM.
 */
object CalibrationSampler {

    /**
     * Sample every patch in [target] using the four user-tapped chart
     * [corners]. Returns one [PatchSample] per patch (in the same order as
     * `target.patches`).
     *
     * Patches whose per-channel variance exceeds [maxVariance] are flagged
     * `rejected = true`; the caller decides whether to surface a toast,
     * exclude the patch from the CCM solve, or both.
     */
    fun sample(
        plane: RgbPlane,
        target: ReferenceTarget,
        corners: ChartCorners,
        maxVariance: Float = DEFAULT_MAX_VARIANCE,
        borderFrac: Float = ReferenceTarget.DEFAULT_BORDER_FRAC,
    ): List<PatchSample> {
        val halfNorm = target.patchHalfSize(borderFrac)
        return target.patches.map { patch ->
            val centerNorm = target.patchCenter(patch.row, patch.col, borderFrac)
            sampleNormalized(
                plane = plane,
                centerNorm = centerNorm,
                halfNorm = halfNorm,
                corners = corners,
                maxVariance = maxVariance,
                patchRef = patch,
            )
        }
    }

    /**
     * Sample one ROI defined in normalized chart coordinates `[0, 1]^2`. The
     * normalized rect is mapped to pixel coordinates via bilinear
     * interpolation over [corners], then [sampleAt] handles the rest.
     */
    fun sampleNormalized(
        plane: RgbPlane,
        centerNorm: Point2,
        halfNorm: Float,
        corners: ChartCorners,
        maxVariance: Float = DEFAULT_MAX_VARIANCE,
        patchRef: ReferenceTarget.Patch? = null,
    ): PatchSample {
        require(halfNorm > 0f && halfNorm < 0.5f) { "halfNorm must be in (0, 0.5) (was $halfNorm)" }

        val centerPx = corners.bilinearMap(centerNorm.x, centerNorm.y)
        // Compute pixel half-extents from the four edge midpoints so the ROI
        // adapts to a non-square chart projection.
        val rightPx = corners.bilinearMap((centerNorm.x + halfNorm).coerceAtMost(1f), centerNorm.y)
        val downPx = corners.bilinearMap(centerNorm.x, (centerNorm.y + halfNorm).coerceAtMost(1f))
        val halfW = max(1f, kotlin.math.abs(rightPx.x - centerPx.x))
        val halfH = max(1f, kotlin.math.abs(downPx.y - centerPx.y))

        return sampleAt(
            plane = plane,
            cx = centerPx.x,
            cy = centerPx.y,
            halfW = halfW,
            halfH = halfH,
            maxVariance = maxVariance,
            patchRef = patchRef,
        )
    }

    /**
     * Sample one rectangular ROI in pixel coordinates. Returns the per-channel
     * mean + variance over the ROI. The ROI is clipped to the plane's bounds;
     * if the clipped ROI is empty the sample is flagged rejected.
     */
    fun sampleAt(
        plane: RgbPlane,
        cx: Float,
        cy: Float,
        halfW: Float,
        halfH: Float,
        maxVariance: Float = DEFAULT_MAX_VARIANCE,
        patchRef: ReferenceTarget.Patch? = null,
    ): PatchSample {
        require(halfW > 0f && halfH > 0f) { "halfW and halfH must be > 0" }
        val x0 = max(0, floor(cx - halfW).toInt())
        val y0 = max(0, floor(cy - halfH).toInt())
        val x1 = min(plane.width - 1, floor(cx + halfW).toInt())
        val y1 = min(plane.height - 1, floor(cy + halfH).toInt())
        if (x1 < x0 || y1 < y0) {
            return PatchSample(
                mean = floatArrayOf(0f, 0f, 0f),
                variance = floatArrayOf(0f, 0f, 0f),
                samples = 0,
                rejected = true,
                rejectReason = "ROI off-plane",
                patchRef = patchRef,
            )
        }
        // Two-pass mean + variance (numerically stable for low N; pixel counts
        // are typically a few hundred so we stay below the precision floor).
        var rSum = 0.0; var gSum = 0.0; var bSum = 0.0
        var n = 0
        for (y in y0..y1) {
            val rowBase = y * plane.width
            for (x in x0..x1) {
                val idx = (rowBase + x) * 3
                rSum += plane.rgb[idx]
                gSum += plane.rgb[idx + 1]
                bSum += plane.rgb[idx + 2]
                n += 1
            }
        }
        val rAvg = (rSum / n).toFloat()
        val gAvg = (gSum / n).toFloat()
        val bAvg = (bSum / n).toFloat()
        var rVar = 0.0; var gVar = 0.0; var bVar = 0.0
        for (y in y0..y1) {
            val rowBase = y * plane.width
            for (x in x0..x1) {
                val idx = (rowBase + x) * 3
                val dr = plane.rgb[idx] - rAvg
                val dg = plane.rgb[idx + 1] - gAvg
                val db = plane.rgb[idx + 2] - bAvg
                rVar += dr * dr; gVar += dg * dg; bVar += db * db
            }
        }
        val rV = (rVar / n).toFloat()
        val gV = (gVar / n).toFloat()
        val bV = (bVar / n).toFloat()
        val maxV = max(rV, max(gV, bV))
        val rejected = maxV > maxVariance
        return PatchSample(
            mean = floatArrayOf(rAvg, gAvg, bAvg),
            variance = floatArrayOf(rV, gV, bV),
            samples = n,
            rejected = rejected,
            rejectReason = if (rejected) "variance $maxV > $maxVariance (chart not flat / out of focus)" else null,
            patchRef = patchRef,
        )
    }

    /**
     * Default rejection threshold for per-channel variance over a patch, in
     * normalized linear-light units squared. A patch on a flat printed chart
     * sampled at moderate ISO typically registers variance well under 1e-3.
     */
    const val DEFAULT_MAX_VARIANCE: Float = 5e-3f
}

/**
 * 2D RGB image plane in normalized linear-light units `[0, 1]` per channel,
 * stored row-major with three floats per pixel:
 * `rgb[(y * width + x) * 3 + channel]`.
 */
class RgbPlane(
    val rgb: FloatArray,
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0) { "width must be > 0 (was $width)" }
        require(height > 0) { "height must be > 0 (was $height)" }
        val expected = width * height * 3
        require(rgb.size == expected) {
            "rgb must have $expected entries for ${width}x${height} (was ${rgb.size})"
        }
    }

    /** Sample one pixel by clamping (x, y) to the plane bounds. */
    fun pixel(x: Int, y: Int): FloatArray {
        val xc = x.coerceIn(0, width - 1)
        val yc = y.coerceIn(0, height - 1)
        val idx = (yc * width + xc) * 3
        return floatArrayOf(rgb[idx], rgb[idx + 1], rgb[idx + 2])
    }

    companion object {
        /**
         * Build an [RgbPlane] of the given size where every pixel has the
         * supplied RGB triple. Convenience for unit tests and synthetic
         * patches.
         */
        fun uniform(width: Int, height: Int, r: Float, g: Float, b: Float): RgbPlane {
            val rgb = FloatArray(width * height * 3)
            for (i in 0 until width * height) {
                val idx = i * 3
                rgb[idx] = r; rgb[idx + 1] = g; rgb[idx + 2] = b
            }
            return RgbPlane(rgb, width, height)
        }
    }
}

/**
 * Four chart corners in pixel coordinates, ordered top-left -> top-right ->
 * bottom-right -> bottom-left (clockwise from `tl`).
 */
data class ChartCorners(val tl: Point2, val tr: Point2, val br: Point2, val bl: Point2) {
    /**
     * Bilinear interpolation from chart-relative `(u, v) in [0, 1]^2` to pixel
     * coordinates. `(0, 0) -> tl`, `(1, 0) -> tr`, `(1, 1) -> br`, `(0, 1) -> bl`.
     */
    fun bilinearMap(u: Float, v: Float): Point2 {
        val x = (1 - u) * (1 - v) * tl.x + u * (1 - v) * tr.x +
            u * v * br.x + (1 - u) * v * bl.x
        val y = (1 - u) * (1 - v) * tl.y + u * (1 - v) * tr.y +
            u * v * br.y + (1 - u) * v * bl.y
        return Point2(x, y)
    }
}

/**
 * One patch sample produced by [CalibrationSampler]. Contains the per-channel
 * mean and variance over the sampled pixels, the count of pixels actually
 * averaged (post-clipping), and a rejection flag + reason for callers that
 * need to surface a toast.
 */
data class PatchSample(
    val mean: FloatArray,
    val variance: FloatArray,
    val samples: Int,
    val rejected: Boolean,
    val rejectReason: String?,
    val patchRef: ReferenceTarget.Patch?,
) {
    init {
        require(mean.size == 3) { "mean must be length 3 (was ${mean.size})" }
        require(variance.size == 3) { "variance must be length 3 (was ${variance.size})" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PatchSample) return false
        return mean.contentEquals(other.mean) &&
            variance.contentEquals(other.variance) &&
            samples == other.samples &&
            rejected == other.rejected &&
            rejectReason == other.rejectReason &&
            patchRef == other.patchRef
    }

    override fun hashCode(): Int {
        var result = mean.contentHashCode()
        result = 31 * result + variance.contentHashCode()
        result = 31 * result + samples
        result = 31 * result + rejected.hashCode()
        result = 31 * result + (rejectReason?.hashCode() ?: 0)
        result = 31 * result + (patchRef?.hashCode() ?: 0)
        return result
    }
}
