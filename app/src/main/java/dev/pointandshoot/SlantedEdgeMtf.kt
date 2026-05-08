package dev.pointandshoot

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Slanted-edge MTF50 measurement per BUILD_PLAN §7 ("Phase 4 - Calibration
 * mode"). Implements the classical ISO 12233-style algorithm on a single
 * luminance ROI containing a near-vertical (or near-horizontal) edge:
 *
 *   1. Per-row edge centroids via gradient-weighted x.
 *   2. Linear-fit `y -> x` to get edge slope + intercept.
 *   3. Bin pixels by perpendicular distance from the fitted edge into an
 *      oversampled edge-spread function (ESF), default 4x.
 *   4. Differentiate ESF -> line-spread function (LSF).
 *   5. Apply a Hamming window.
 *   6. Direct DFT -> MTF magnitude (small N; an FFT is not worth the
 *      complexity here).
 *   7. Find the frequency where MTF drops to 50 % of MTF(0); linearly
 *      interpolate between adjacent DFT bins.
 *
 * Returns MTF50 in **cycles per pixel** (`0.5` is Nyquist). [cyclesPerPixelToLpph]
 * converts to line-pairs per picture height for callers who want the BUILD_PLAN
 * "lp/ph" flavor.
 *
 * No Android imports; safe for unit testing on the JVM.
 */
object SlantedEdgeMtf {

    /**
     * Measure MTF50 in cycles/pixel from a luminance ROI. Returns `null` when
     * the ROI does not contain a usable edge (no per-row gradient signal,
     * fewer than [MIN_USABLE_ROWS] valid centroid rows, or all-empty ESF).
     *
     * @param oversampleFactor samples per pixel along the edge-perpendicular
     *   axis. Default 4 (the ISO 12233 recommendation).
     * @param esfBins the binned ESF length. Must be a power of 2 for the DFT
     *   step to behave well; default 128.
     * @param orientation `NearVertical` for chart-corner edges typical of the
     *   24-patch slanted-edge ROIs; `NearHorizontal` for edges rotated 90 deg
     *   (we transpose the ROI before processing).
     */
    fun measureMtf50(
        luma: GrayPlane,
        oversampleFactor: Int = DEFAULT_OVERSAMPLE,
        esfBins: Int = DEFAULT_ESF_BINS,
        orientation: EdgeOrientation = EdgeOrientation.NearVertical,
    ): Float? {
        require(oversampleFactor >= 1) { "oversampleFactor must be >= 1 (was $oversampleFactor)" }
        require(esfBins >= 16 && (esfBins and (esfBins - 1)) == 0) {
            "esfBins must be a power of 2 >= 16 (was $esfBins)"
        }
        val effective = if (orientation == EdgeOrientation.NearHorizontal) luma.transpose() else luma
        if (effective.width < 4 || effective.height < MIN_USABLE_ROWS) return null

        val centroids = computeRowCentroids(effective) ?: return null
        if (centroids.size < MIN_USABLE_ROWS) return null

        val (slope, intercept) = linearFit(centroids)
        val esf = buildEsf(effective, slope, intercept, oversampleFactor, esfBins) ?: return null
        val lsf = differentiate(esf)
        val windowed = applyHamming(lsf)
        val mtf = magnitudeDft(windowed)
        return findMtf50CyclesPerPixel(mtf, oversampleFactor, esfBins)
    }

    /**
     * Convert a cycles/pixel measurement to line-pairs per picture height
     * (`lp/ph`) given the full picture height in pixels. BUILD_PLAN §7 quotes
     * the calibration target as `>= 1500 lp/ph` at f/1.6 main wide.
     */
    fun cyclesPerPixelToLpph(cyclesPerPixel: Float, pictureHeightPx: Int): Float =
        cyclesPerPixel * pictureHeightPx

    /** Edge orientation in the supplied ROI. */
    enum class EdgeOrientation { NearVertical, NearHorizontal }

    // ---------- step 1: per-row edge centroids ----------

    private data class CentroidPoint(val y: Int, val x: Float)

    private fun computeRowCentroids(luma: GrayPlane): List<CentroidPoint>? {
        val out = ArrayList<CentroidPoint>(luma.height)
        for (y in 0 until luma.height) {
            var num = 0.0
            var den = 0.0
            for (x in 1 until luma.width - 1) {
                val gx = luma.luma(x + 1, y) - luma.luma(x - 1, y)
                val w = abs(gx).toDouble()
                num += w * x
                den += w
            }
            if (den > MIN_GRADIENT_WEIGHT) {
                out.add(CentroidPoint(y = y, x = (num / den).toFloat()))
            }
        }
        return if (out.isEmpty()) null else out
    }

    // ---------- step 2: linear fit ----------

    private data class LineFit(val slope: Float, val intercept: Float)

    private fun linearFit(points: List<CentroidPoint>): LineFit {
        var sx = 0.0; var sy = 0.0; var sxy = 0.0; var sxx = 0.0
        val n = points.size
        for (p in points) {
            val xi = p.y.toDouble()  // we fit y -> x; treat row index as the independent variable
            val yi = p.x.toDouble()
            sx += xi; sy += yi
            sxy += xi * yi
            sxx += xi * xi
        }
        val denom = n * sxx - sx * sx
        if (abs(denom) < 1e-9) {
            return LineFit(slope = 0f, intercept = (sy / n).toFloat())
        }
        val slope = (n * sxy - sx * sy) / denom
        val intercept = (sy - slope * sx) / n
        return LineFit(slope = slope.toFloat(), intercept = intercept.toFloat())
    }

    // ---------- step 3: build oversampled ESF ----------

    private fun buildEsf(
        luma: GrayPlane,
        slope: Float,
        intercept: Float,
        oversampleFactor: Int,
        esfBins: Int,
    ): FloatArray? {
        // For each pixel, compute perpendicular distance to the fitted edge.
        // For small slopes (which the slanted-edge method assumes - 5..15 deg),
        // sqrt(1 + slope^2) is close to 1; we keep the exact divisor anyway.
        val norm = sqrt(1.0 + slope.toDouble() * slope.toDouble()).toFloat()
        val sums = DoubleArray(esfBins)
        val counts = IntArray(esfBins)
        val center = esfBins / 2
        for (y in 0 until luma.height) {
            val edgeX = slope * y + intercept
            for (x in 0 until luma.width) {
                val d = (x - edgeX) / norm
                val bin = floor(d * oversampleFactor).toInt() + center
                if (bin in 0 until esfBins) {
                    sums[bin] += luma.luma(x, y).toDouble()
                    counts[bin] += 1
                }
            }
        }
        val esf = FloatArray(esfBins)
        var nonEmpty = 0
        for (i in 0 until esfBins) {
            if (counts[i] > 0) {
                esf[i] = (sums[i] / counts[i]).toFloat()
                nonEmpty += 1
            }
        }
        if (nonEmpty < esfBins / 2) return null

        // Linear-interpolate empty bins from their nearest neighbors so the LSF
        // step does not blow up on isolated zeros.
        var i = 0
        while (i < esfBins) {
            if (counts[i] == 0) {
                val left = (i - 1 downTo 0).firstOrNull { counts[it] > 0 }
                val right = (i + 1 until esfBins).firstOrNull { counts[it] > 0 }
                when {
                    left != null && right != null -> {
                        val t = (i - left).toFloat() / (right - left)
                        esf[i] = esf[left] * (1 - t) + esf[right] * t
                    }
                    left != null -> esf[i] = esf[left]
                    right != null -> esf[i] = esf[right]
                }
            }
            i += 1
        }
        return esf
    }

    // ---------- step 4: differentiate ----------

    private fun differentiate(esf: FloatArray): FloatArray {
        val out = FloatArray(esf.size)
        for (i in esf.indices) {
            val left = if (i == 0) esf[i] else esf[i - 1]
            val right = if (i == esf.size - 1) esf[i] else esf[i + 1]
            out[i] = (right - left) * 0.5f
        }
        return out
    }

    // ---------- step 5: Hamming window ----------

    private fun applyHamming(lsf: FloatArray): FloatArray {
        val n = lsf.size
        val out = FloatArray(n)
        for (i in 0 until n) {
            val w = 0.54 - 0.46 * cos(2.0 * PI * i / (n - 1))
            out[i] = (lsf[i] * w).toFloat()
        }
        return out
    }

    // ---------- step 6: direct DFT magnitude ----------

    private fun magnitudeDft(signal: FloatArray): FloatArray {
        val n = signal.size
        val mag = FloatArray(n / 2 + 1)
        for (k in 0..n / 2) {
            var re = 0.0
            var im = 0.0
            val twoPiKOverN = 2.0 * PI * k / n
            for (t in 0 until n) {
                val angle = twoPiKOverN * t
                re += signal[t] * cos(angle)
                im -= signal[t] * kotlin.math.sin(angle)
            }
            mag[k] = sqrt(re * re + im * im).toFloat()
        }
        return mag
    }

    // ---------- step 7: find MTF50 ----------

    private fun findMtf50CyclesPerPixel(
        mtfMag: FloatArray,
        oversampleFactor: Int,
        esfBins: Int,
    ): Float? {
        if (mtfMag.isEmpty() || mtfMag[0] <= 1e-9f) return null
        val mtf0 = mtfMag[0]
        val target = 0.5f * mtf0
        // Walk upward from DC; find first bin whose normalized MTF crosses below target.
        for (k in 1 until mtfMag.size) {
            if (mtfMag[k] <= target) {
                // Linear-interpolate between k-1 and k to estimate the exact crossing.
                val v1 = mtfMag[k - 1]
                val v2 = mtfMag[k]
                val t = if (abs(v1 - v2) < 1e-9f) 0f else (v1 - target) / (v1 - v2)
                val kInterp = (k - 1) + t
                // bin -> cycles per pixel: f = (k/N) samples_per_sample * oversampleFactor samples_per_pixel
                return kInterp * oversampleFactor / esfBins.toFloat()
            }
        }
        // MTF stays above 50 % all the way to bin N/2 - return Nyquist of the oversampled signal.
        return (mtfMag.size - 1).toFloat() * oversampleFactor / esfBins.toFloat()
    }

    // ---------- constants ----------

    /** ISO 12233 default oversampling factor (samples per pixel along the edge-perpendicular axis). */
    const val DEFAULT_OVERSAMPLE: Int = 4

    /** Default binned ESF length; 128 gives ~ 32 px on each side of the edge at 4x oversampling. */
    const val DEFAULT_ESF_BINS: Int = 128

    /** Need at least this many rows with a usable gradient signal to fit an edge. */
    const val MIN_USABLE_ROWS: Int = 8

    /** Below this gradient sum a row is considered edge-free (skipped). */
    private const val MIN_GRADIENT_WEIGHT: Double = 1e-3
}

/**
 * Single-channel luminance plane, row-major, normalized to `[0, 1]`. Used by
 * the slanted-edge MTF50 path so the engine can convert RGB / YUV ROIs to
 * luma without baking the conversion into the MTF math.
 */
class GrayPlane(
    val luma: FloatArray,
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0) { "width must be > 0 (was $width)" }
        require(height > 0) { "height must be > 0 (was $height)" }
        val expected = width * height
        require(luma.size == expected) {
            "luma must have $expected entries for ${width}x${height} (was ${luma.size})"
        }
    }

    fun luma(x: Int, y: Int): Float = luma[y * width + x]

    /** Return a transposed view (allocates a new [GrayPlane]). */
    fun transpose(): GrayPlane {
        val out = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                out[x * height + y] = luma[y * width + x]
            }
        }
        return GrayPlane(out, width = height, height = width)
    }

    companion object {
        /**
         * Build a [GrayPlane] from a single-channel function `(x, y) -> [0, 1]`.
         * Convenience for unit tests.
         */
        fun build(width: Int, height: Int, sampler: (x: Int, y: Int) -> Float): GrayPlane {
            val luma = FloatArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    luma[y * width + x] = sampler(x, y).coerceIn(0f, 1f)
                }
            }
            return GrayPlane(luma, width, height)
        }
    }
}
