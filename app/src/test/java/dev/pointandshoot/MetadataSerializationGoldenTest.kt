package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Closes the host-side portion of BUILD_PLAN.md §9 "Add correctness tests where feasible":
 *
 *     [HOST] Golden-file tests for metadata serialization (GroupingID, crop metadata)
 *
 * Pure-data plans ([BracketPlan], [CropPlan]) get a stable text projection
 * that the capture engine will eventually wrap when stamping DNG / AVIF tags.
 * Locking the projection here means future refactors in the plan classes
 * cannot silently change the on-disk metadata that desktop tooling
 * (`exiftool`, `darktable`) consumes - any change to the projection requires
 * a deliberate update to the golden string here, with a CHANGELOG entry.
 *
 * Why pure-text projections instead of binary DNG / AVIF goldens? The actual
 * DNG/AVIF byte stream depends on `DngCreator` (Android-only) and the chosen
 * encoder (libavif / libjxl). The plan-level golden is the lowest stable
 * surface the engine can serialize *from*, and it is fully testable on the
 * JVM without any Android stubs. The end-to-end byte-level golden lands
 * with the encoder pipeline (Phase 1).
 */
class MetadataSerializationGoldenTest {

    // ---------- BracketPlan: GroupingID + EV stops ----------

    @Test
    fun `BracketPlan_3-shot golden text projection (1 EV step)`() {
        val plan = BracketPlan.build(
            pattern = BracketPattern.Three,
            evStep = 1.0,
            groupingId = "bkt-fixed-3",
        )
        val golden = """
            BracketPlan v1
            groupingId=bkt-fixed-3
            pattern=Three
            evStep=1.000
            stops=3
            [0] index=0 ev=-1.000 ref=false grp=bkt-fixed-3
            [1] index=1 ev=+0.000 ref=true grp=bkt-fixed-3
            [2] index=2 ev=+1.000 ref=false grp=bkt-fixed-3
        """.trimIndent()
        assertEquals(golden, formatBracketPlan(plan))
    }

    @Test
    fun `BracketPlan_5-shot golden text projection (0_667 EV step)`() {
        val plan = BracketPlan.build(
            pattern = BracketPattern.Five,
            evStep = 2.0 / 3.0,
            groupingId = "bkt-fixed-5",
        )
        val golden = """
            BracketPlan v1
            groupingId=bkt-fixed-5
            pattern=Five
            evStep=0.667
            stops=5
            [0] index=0 ev=-1.333 ref=false grp=bkt-fixed-5
            [1] index=1 ev=-0.667 ref=false grp=bkt-fixed-5
            [2] index=2 ev=+0.000 ref=true grp=bkt-fixed-5
            [3] index=3 ev=+0.667 ref=false grp=bkt-fixed-5
            [4] index=4 ev=+1.333 ref=false grp=bkt-fixed-5
        """.trimIndent()
        assertEquals(golden, formatBracketPlan(plan))
    }

    @Test
    fun `BracketPlan_7-shot golden text projection (1 EV step)`() {
        val plan = BracketPlan.build(
            pattern = BracketPattern.Seven,
            evStep = 1.0,
            groupingId = "bkt-fixed-7",
        )
        val golden = """
            BracketPlan v1
            groupingId=bkt-fixed-7
            pattern=Seven
            evStep=1.000
            stops=7
            [0] index=0 ev=-3.000 ref=false grp=bkt-fixed-7
            [1] index=1 ev=-2.000 ref=false grp=bkt-fixed-7
            [2] index=2 ev=-1.000 ref=false grp=bkt-fixed-7
            [3] index=3 ev=+0.000 ref=true grp=bkt-fixed-7
            [4] index=4 ev=+1.000 ref=false grp=bkt-fixed-7
            [5] index=5 ev=+2.000 ref=false grp=bkt-fixed-7
            [6] index=6 ev=+3.000 ref=false grp=bkt-fixed-7
        """.trimIndent()
        assertEquals(golden, formatBracketPlan(plan))
    }

    @Test
    fun `BracketPlan default groupingId is bkt-prefixed and stable across stops`() {
        val plan = BracketPlan.build(BracketPattern.Three)
        assertEquals(true, plan.groupingId.startsWith("bkt-"))
        for (stop in plan.stops) {
            assertEquals(plan.groupingId, stop.bracketGroupingId)
        }
    }

    @Test
    fun `BracketPlan two builds with default ids do not collide`() {
        val a = BracketPlan.build(BracketPattern.Three)
        val b = BracketPlan.build(BracketPattern.Three)
        assertNotEquals("two default builds must yield distinct grouping ids", a.groupingId, b.groupingId)
    }

    // ---------- CropPlan: per-mode golden projection ----------

    @Test
    fun `CropPlan_LYT-808_50MP_Street35 golden`() {
        val plan = CropPlan.centeredCrop(FocalMode.Street35, sensorWidth = 8160, sensorHeight = 6144)
        val golden = """
            CropPlan v1
            mode=Street35 displayName=35mm
            zoom=1.500 effectiveZoomX=1.500
            sensor=8160x6144 -> crop=5440x4096 @ (1360,1024)
            metering=Average af=SinglePoint
        """.trimIndent()
        assertEquals(golden, formatCropPlan(plan, sensorWidth = 8160, sensorHeight = 6144))
    }

    @Test
    fun `CropPlan_LYT-808_50MP_Standard50 golden`() {
        val plan = CropPlan.centeredCrop(FocalMode.Standard50, sensorWidth = 8160, sensorHeight = 6144)
        val golden = """
            CropPlan v1
            mode=Standard50 displayName=50mm
            zoom=2.200 effectiveZoomX=2.199
            sensor=8160x6144 -> crop=3709x2793 @ (2225,1675)
            metering=CenterWeighted af=SinglePoint
        """.trimIndent()
        assertEquals(golden, formatCropPlan(plan, sensorWidth = 8160, sensorHeight = 6144))
    }

    @Test
    fun `CropPlan_LYT-600_50MP_Portrait85 golden`() {
        val plan = CropPlan.centeredCrop(FocalMode.Portrait85, sensorWidth = 8160, sensorHeight = 6144)
        val golden = """
            CropPlan v1
            mode=Portrait85 displayName=85mm
            zoom=1.160 effectiveZoomX=1.160
            sensor=8160x6144 -> crop=7034x5297 @ (563,423)
            metering=CenterWeighted af=EyeAf
        """.trimIndent()
        assertEquals(golden, formatCropPlan(plan, sensorWidth = 8160, sensorHeight = 6144))
    }

    @Test
    fun `CropPlan_LYT-600_50MP_LongTele150 golden`() {
        val plan = CropPlan.centeredCrop(FocalMode.LongTele150, sensorWidth = 8160, sensorHeight = 6144)
        val golden = """
            CropPlan v1
            mode=LongTele150 displayName=150mm
            zoom=2.040 effectiveZoomX=2.040
            sensor=8160x6144 -> crop=4000x3012 @ (2080,1566)
            metering=CenterWeighted af=EyeAf
        """.trimIndent()
        assertEquals(golden, formatCropPlan(plan, sensorWidth = 8160, sensorHeight = 6144))
    }

    // ---------- helpers ----------

    /**
     * Stable text projection of a [BracketPlan]. Versioned (`v1`) so a
     * breaking change forces a CHANGELOG entry and the version bump.
     */
    private fun formatBracketPlan(plan: BracketPlan): String {
        val sb = StringBuilder()
        sb.append("BracketPlan v1").append('\n')
        sb.append("groupingId=").append(plan.groupingId).append('\n')
        sb.append("pattern=").append(plan.pattern.name).append('\n')
        sb.append("evStep=").append(formatFixed3(plan.evStep)).append('\n')
        sb.append("stops=").append(plan.stops.size).append('\n')
        for (s in plan.stops) {
            sb.append('[').append(s.indexInBurst).append(']')
                .append(" index=").append(s.indexInBurst)
                .append(" ev=").append(formatSignedFixed3(s.evOffset))
                .append(" ref=").append(s.isReference)
                .append(" grp=").append(s.bracketGroupingId)
                .append('\n')
        }
        return sb.toString().trimEnd('\n')
    }

    private fun formatCropPlan(plan: CropPlan, sensorWidth: Int, sensorHeight: Int): String {
        return buildString {
            append("CropPlan v1").append('\n')
            append("mode=").append(plan.mode.name)
                .append(" displayName=").append(plan.mode.displayName).append('\n')
            append("zoom=").append(formatFixed3(plan.zoomFactor))
                .append(" effectiveZoomX=").append(formatFixed3(plan.effectiveZoomX)).append('\n')
            append("sensor=").append(sensorWidth).append('x').append(sensorHeight)
                .append(" -> crop=").append(plan.cropWidth).append('x').append(plan.cropHeight)
                .append(" @ (").append(plan.cropLeft).append(',').append(plan.cropTop).append(')').append('\n')
            append("metering=").append(plan.meteringHint.name)
                .append(" af=").append(plan.afHint.name)
        }
    }

    private fun formatFixed3(v: Double): String {
        // Locale-independent fixed-3 decimal: format manually to avoid Locale surprises.
        val rounded = kotlin.math.round(v * 1000.0) / 1000.0
        return java.util.Formatter(StringBuilder(), java.util.Locale.ROOT).format("%.3f", rounded).toString()
    }

    private fun formatSignedFixed3(v: Double): String {
        val rounded = kotlin.math.round(v * 1000.0) / 1000.0
        val absText = formatFixed3(kotlin.math.abs(rounded))
        return if (rounded < 0) "-$absText" else "+$absText"
    }
}
