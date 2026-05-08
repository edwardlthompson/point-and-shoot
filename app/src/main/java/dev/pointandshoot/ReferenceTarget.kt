package dev.pointandshoot

/**
 * Reference calibration chart per BUILD_PLAN §7 ("Phase 4 - Reference targets
 * supported"). Pure-data; no Android imports; safe for unit testing.
 *
 * The patches are laid out in chart-relative normalized coordinates (`[0, 1]`
 * along each axis with origin at top-left). The calibration sampler uses
 * [patches] to know where each patch lives within the user's chart corners and
 * what reference values to compare measured RGB against when solving the CCM.
 *
 * The chart **image** is intentionally NOT bundled (trademark / image rights
 * for the X-Rite variants); only the patch coordinates + published reference
 * RGB values are bundled. The values themselves are facts (CIE measurements
 * under D50 / D65), not copyrightable.
 */
data class ReferenceTarget(
    val id: String,
    val displayName: String,
    val rows: Int,
    val cols: Int,
    val patches: List<Patch>,
    val illuminant: CalibrationProfile.Illuminant,
    val source: String,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(rows > 0) { "rows must be > 0 (was $rows)" }
        require(cols > 0) { "cols must be > 0 (was $cols)" }
        require(patches.size == rows * cols) {
            "patches must have rows*cols = ${rows * cols} entries (was ${patches.size})"
        }
        for ((i, p) in patches.withIndex()) {
            require(p.row in 0 until rows) {
                "patch[$i] row=${p.row} out of range [0, $rows)"
            }
            require(p.col in 0 until cols) {
                "patch[$i] col=${p.col} out of range [0, $cols)"
            }
        }
        // Each (row, col) pair must appear exactly once.
        val seen = HashSet<Pair<Int, Int>>(rows * cols)
        for (p in patches) {
            require(seen.add(p.row to p.col)) {
                "duplicate patch at row=${p.row} col=${p.col}"
            }
        }
    }

    /**
     * Patches whose [Patch.role] is [PatchRole.Neutral] - used by
     * [CalibrationMath.computeWbGains].
     */
    val neutralPatches: List<Patch> get() = patches.filter { it.role == PatchRole.Neutral }

    /**
     * Patches whose [Patch.role] is [PatchRole.Color] - used by
     * [CalibrationMath.computeCcm] (we exclude pure neutrals from the CCM
     * solve so the matrix is not biased by samples that are already on the
     * gray axis).
     */
    val colorPatches: List<Patch> get() = patches.filter { it.role == PatchRole.Color }

    /**
     * Normalized chart coordinates of the center of patch (row, col), with
     * a small uniform border around the chart (defaults to 5% of each axis
     * so the calibration sampler has room to drop pixels near the chart edge
     * without hitting paper or shadow).
     */
    fun patchCenter(row: Int, col: Int, borderFrac: Float = DEFAULT_BORDER_FRAC): Point2 {
        require(row in 0 until rows) { "row=$row out of range [0, $rows)" }
        require(col in 0 until cols) { "col=$col out of range [0, $cols)" }
        require(borderFrac in 0f..0.4f) { "borderFrac must be in [0, 0.4] (was $borderFrac)" }
        val span = 1f - 2f * borderFrac
        val cx = borderFrac + (col + 0.5f) / cols * span
        val cy = borderFrac + (row + 0.5f) / rows * span
        return Point2(cx, cy)
    }

    /**
     * Suggested half-size (in normalized chart units) of the sampling window
     * around each patch center. Defaults to 30 % of one cell width so we sample
     * the inner third of each patch and avoid printing seams / borders.
     */
    fun patchHalfSize(borderFrac: Float = DEFAULT_BORDER_FRAC): Float {
        val span = 1f - 2f * borderFrac
        val cellW = span / cols
        val cellH = span / rows
        val cell = minOf(cellW, cellH)
        return cell * DEFAULT_PATCH_INNER_FRAC * 0.5f
    }

    /**
     * One patch in the chart. [referenceRgb] holds the expected linear-light
     * sRGB value of the patch under [ReferenceTarget.illuminant], normalized
     * to `[0, 1]` per channel.
     */
    data class Patch(
        val row: Int,
        val col: Int,
        val name: String,
        val role: PatchRole,
        val referenceRgb: FloatArray,
    ) {
        init {
            require(name.isNotBlank()) { "patch name must not be blank" }
            require(referenceRgb.size == 3) {
                "referenceRgb must be length 3 (was ${referenceRgb.size})"
            }
            require(referenceRgb.all { it in 0f..1f }) {
                "referenceRgb values must be in [0, 1] (was ${referenceRgb.toList()})"
            }
        }

        // Override equals/hashCode because FloatArray identity equality is wrong.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Patch) return false
            return row == other.row && col == other.col && name == other.name &&
                role == other.role && referenceRgb.contentEquals(other.referenceRgb)
        }

        override fun hashCode(): Int {
            var result = row
            result = 31 * result + col
            result = 31 * result + name.hashCode()
            result = 31 * result + role.hashCode()
            result = 31 * result + referenceRgb.contentHashCode()
            return result
        }
    }

    enum class PatchRole {
        /** Color patch - included in the CCM solve. */
        Color,
        /** Neutral / gray patch - used for WB gain solve. */
        Neutral,
    }

    companion object {
        /** Default border around the chart for patch-center sampling (5%). */
        const val DEFAULT_BORDER_FRAC: Float = 0.05f

        /** Sample only the inner 60% of each patch (avoids printing seams). */
        const val DEFAULT_PATCH_INNER_FRAC: Float = 0.60f
    }
}

/**
 * Two-dimensional point in normalized chart coordinates `[0, 1]` (or in pixel
 * coordinates `[0, width)` x `[0, height)` depending on the call site).
 */
data class Point2(val x: Float, val y: Float)
