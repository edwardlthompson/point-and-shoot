package dev.pointandshoot

import kotlin.math.abs

/**
 * Pure-data solvers for the calibration mode per BUILD_PLAN §7 ("Phase 4").
 *
 *   * [computeWbGains]: per-channel scaling from neutral grays such that the
 *     average gray patch satisfies `R/G ≈ 1` and `B/G ≈ 1` (BT.709 G-anchor).
 *   * [computeCcm]: 3x3 color correction matrix solved via linear least
 *     squares (`target = M * measured`) over the color patches.
 *
 * No Android types; no I/O; safe for unit testing without instrumentation.
 *
 * Math note on the CCM solve: we want to find a 3x3 matrix `M` such that for
 * each patch `i`, `target_i ≈ M * measured_i` (column-vector convention).
 * Stacking N patches into matrices `T` (N x 3) and `Q` (N x 3, "Q" because
 * "M" is reserved for the answer), the equation in row form is
 * `T = Q * M^T`. Standard least-squares normal equations: `(Q^T Q) * M^T =
 * Q^T T`. We solve for `M^T` via Gaussian elimination on the 3x3 system, then
 * transpose to get `M`.
 */
object CalibrationMath {

    /**
     * Per-channel WB gains from a list of neutral / gray patches (linear-light
     * RGB normalized to `[0, 1]`).
     *
     * @param neutralPatches at least one entry; typical inputs use 6 (the
     *   neutral wedge from white through black on a 24-patch chart).
     * @return [CalibrationProfile.WbGains] with `g = 1` and `r`, `b` chosen so
     *   the average neutral patch satisfies `R*r ≈ G ≈ B*b`.
     */
    fun computeWbGains(neutralPatches: List<FloatArray>): CalibrationProfile.WbGains {
        require(neutralPatches.isNotEmpty()) { "Need at least one neutral patch" }
        var rSum = 0.0
        var gSum = 0.0
        var bSum = 0.0
        for (p in neutralPatches) {
            require(p.size == 3) { "Each neutral patch must be RGB triple (was size ${p.size})" }
            require(p.all { it.isFinite() }) { "Neutral patch contains NaN/Inf: ${p.toList()}" }
            rSum += p[0]
            gSum += p[1]
            bSum += p[2]
        }
        val rAvg = (rSum / neutralPatches.size).toFloat()
        val gAvg = (gSum / neutralPatches.size).toFloat()
        val bAvg = (bSum / neutralPatches.size).toFloat()
        require(gAvg > MIN_AVG_FOR_NEUTRAL_SOLVE) {
            "Average green is too small ($gAvg) - neutrals appear black; aborting WB solve"
        }
        return CalibrationProfile.WbGains(
            r = (gAvg / rAvg.coerceAtLeast(MIN_AVG_FOR_NEUTRAL_SOLVE)),
            g = 1f,
            b = (gAvg / bAvg.coerceAtLeast(MIN_AVG_FOR_NEUTRAL_SOLVE)),
        )
    }

    /**
     * Solves `target = M * measured` for a 3x3 [CalibrationProfile.Ccm] via
     * linear least squares (3x3 normal equations, Gaussian elimination).
     *
     * @param measured list of N RGB samples (after WB gains have been applied
     *   so the input is the same scale as [target]).
     * @param target list of N reference RGB values in the SAME color space as
     *   [measured]. The mapping from published Lab values to RGB happens
     *   upstream (Bradford CAT + sRGB encoding) so this solver stays linear.
     */
    fun computeCcm(
        measured: List<FloatArray>,
        target: List<FloatArray>,
    ): CalibrationProfile.Ccm {
        require(measured.size == target.size) {
            "measured (${measured.size}) and target (${target.size}) must have equal length"
        }
        require(measured.size >= MIN_PATCHES_FOR_CCM) {
            "Need at least $MIN_PATCHES_FOR_CCM patches to solve a 3x3 CCM (got ${measured.size})"
        }
        for (m in measured) {
            require(m.size == 3) { "measured patches must be RGB triples (got size ${m.size})" }
            require(m.all { it.isFinite() }) { "measured patch has NaN/Inf: ${m.toList()}" }
        }
        for (t in target) {
            require(t.size == 3) { "target patches must be RGB triples (got size ${t.size})" }
            require(t.all { it.isFinite() }) { "target patch has NaN/Inf: ${t.toList()}" }
        }

        // Build (Q^T Q) (3x3) and (Q^T T) (3x3) where Q is the measured matrix
        // and T is the target matrix. Both are symmetric in the index sense
        // we use; code below mirrors the algebra above.
        val qtq = Array(3) { DoubleArray(3) }
        val qtt = Array(3) { DoubleArray(3) }
        for (i in measured.indices) {
            val m = measured[i]
            val t = target[i]
            for (a in 0..2) {
                for (b in 0..2) {
                    qtq[a][b] += m[a].toDouble() * m[b].toDouble()
                    qtt[a][b] += m[a].toDouble() * t[b].toDouble()
                }
            }
        }
        // Solve qtq * Y = qtt; Y is M^T.
        val y = solve3x3(qtq, qtt)
        // M[a][b] = Y[b][a].
        return CalibrationProfile.Ccm(
            m00 = y[0][0].toFloat(), m01 = y[1][0].toFloat(), m02 = y[2][0].toFloat(),
            m10 = y[0][1].toFloat(), m11 = y[1][1].toFloat(), m12 = y[2][1].toFloat(),
            m20 = y[0][2].toFloat(), m21 = y[1][2].toFloat(), m22 = y[2][2].toFloat(),
        )
    }

    /**
     * Solve `a * X = b` for X where `a` is 3x3 and `b` is 3x[k]. Uses
     * Gaussian elimination with partial pivoting on the augmented matrix.
     * Throws on a singular `a`.
     */
    private fun solve3x3(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
        val n = 3
        val k = b[0].size
        val aug = Array(n) { row -> DoubleArray(n + k).also { dst ->
            for (j in 0 until n) dst[j] = a[row][j]
            for (j in 0 until k) dst[n + j] = b[row][j]
        } }
        for (col in 0 until n) {
            var pivot = col
            for (row in col + 1 until n) {
                if (abs(aug[row][col]) > abs(aug[pivot][col])) pivot = row
            }
            require(abs(aug[pivot][col]) >= SINGULAR_TOLERANCE) {
                "Singular matrix in CCM solve (pivot below $SINGULAR_TOLERANCE at col=$col)"
            }
            if (pivot != col) {
                val tmp = aug[col]; aug[col] = aug[pivot]; aug[pivot] = tmp
            }
            for (row in col + 1 until n) {
                val factor = aug[row][col] / aug[col][col]
                for (c in col until n + k) {
                    aug[row][c] -= factor * aug[col][c]
                }
            }
        }
        val x = Array(n) { DoubleArray(k) }
        for (row in n - 1 downTo 0) {
            for (kk in 0 until k) {
                var sum = aug[row][n + kk]
                for (c in row + 1 until n) sum -= aug[row][c] * x[c][kk]
                x[row][kk] = sum / aug[row][row]
            }
        }
        return x
    }

    /** Below this we treat the WB denominator as zero and refuse to solve. */
    const val MIN_AVG_FOR_NEUTRAL_SOLVE: Float = 1e-4f

    /** Three patches give a square system; more patches make least-squares meaningful. */
    const val MIN_PATCHES_FOR_CCM: Int = 3

    /** Pivots smaller than this trigger a "singular matrix" failure. */
    private const val SINGULAR_TOLERANCE: Double = 1e-12
}
