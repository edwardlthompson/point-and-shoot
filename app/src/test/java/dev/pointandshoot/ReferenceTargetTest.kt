package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ReferenceTargetTest {

    // ---------- ReferenceTarget invariants ----------

    @Test
    fun `constructor rejects mismatched patch count`() {
        val ex = runCatching {
            ReferenceTarget(
                id = "bad", displayName = "Bad", rows = 2, cols = 2,
                patches = listOf(makePatch(0, 0)),
                illuminant = CalibrationProfile.Illuminant.D65,
                source = "test",
            )
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `constructor rejects duplicate (row, col) pairs`() {
        val ex = runCatching {
            ReferenceTarget(
                id = "bad", displayName = "Bad", rows = 1, cols = 2,
                patches = listOf(makePatch(0, 0), makePatch(0, 0)),
                illuminant = CalibrationProfile.Illuminant.D65,
                source = "test",
            )
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `constructor rejects out-of-range patch coords`() {
        val ex = runCatching {
            ReferenceTarget(
                id = "bad", displayName = "Bad", rows = 1, cols = 1,
                patches = listOf(makePatch(5, 5)),
                illuminant = CalibrationProfile.Illuminant.D65,
                source = "test",
            )
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `patchCenter computes uniformly-spaced centers within border`() {
        val target = BundledReferenceTargets.Generic24
        val border = ReferenceTarget.DEFAULT_BORDER_FRAC
        val span = 1f - 2f * border

        // Top-left patch should be at (border + 0.5/cols * span, border + 0.5/rows * span).
        val tl = target.patchCenter(0, 0)
        assertEquals(border + 0.5f / target.cols * span, tl.x, 1e-5f)
        assertEquals(border + 0.5f / target.rows * span, tl.y, 1e-5f)

        // Bottom-right patch.
        val br = target.patchCenter(target.rows - 1, target.cols - 1)
        assertEquals(border + (target.cols - 0.5f) / target.cols * span, br.x, 1e-5f)
        assertEquals(border + (target.rows - 0.5f) / target.rows * span, br.y, 1e-5f)
    }

    @Test
    fun `patchHalfSize is the inner-third of the smaller cell dimension`() {
        val target = BundledReferenceTargets.Generic24
        val half = target.patchHalfSize()
        // For a 4 x 6 chart with default 5% border: span=0.9, cellW=0.15, cellH=0.225, min=0.15.
        // Inner-60% half = 0.15 * 0.6 / 2 = 0.045.
        assertEquals(0.045f, half, 1e-5f)
    }

    @Test
    fun `neutralPatches and colorPatches partition all patches`() {
        for (target in BundledReferenceTargets.All) {
            val total = target.patches.size
            val neutral = target.neutralPatches.size
            val color = target.colorPatches.size
            assertEquals("$target patches partition", total, neutral + color)
        }
    }

    // ---------- ColorChecker Classic 24 ----------

    @Test
    fun `ColorCheckerClassic24 has 24 patches in a 4x6 grid`() {
        val ccc = BundledReferenceTargets.ColorCheckerClassic24
        assertEquals(4, ccc.rows)
        assertEquals(6, ccc.cols)
        assertEquals(24, ccc.patches.size)
    }

    @Test
    fun `ColorCheckerClassic24 row 3 is the neutral wedge (white to black)`() {
        val ccc = BundledReferenceTargets.ColorCheckerClassic24
        val neutralRow = ccc.patches.filter { it.row == 3 }.sortedBy { it.col }
        assertEquals(6, neutralRow.size)
        for (p in neutralRow) {
            assertEquals(p.name + " R==G", p.referenceRgb[0], p.referenceRgb[1], 1e-6f)
            assertEquals(p.name + " G==B", p.referenceRgb[1], p.referenceRgb[2], 1e-6f)
            assertEquals(p.name + " role", ReferenceTarget.PatchRole.Neutral, p.role)
        }
        // Brightness must decrease left-to-right (white at col=0, black at col=5).
        for (i in 0 until neutralRow.size - 1) {
            assertTrue(
                "neutral[$i]=${neutralRow[i].referenceRgb[0]} should be > neutral[${i + 1}]=${neutralRow[i + 1].referenceRgb[0]}",
                neutralRow[i].referenceRgb[0] > neutralRow[i + 1].referenceRgb[0],
            )
        }
    }

    @Test
    fun `ColorCheckerClassic24 row 2 is the saturated primaries`() {
        val ccc = BundledReferenceTargets.ColorCheckerClassic24
        val red = ccc.patches.first { it.row == 2 && it.col == 2 }
        // Red patch: R is dominant.
        assertTrue("red R should dominate (got ${red.referenceRgb.toList()})",
            red.referenceRgb[0] > red.referenceRgb[1] && red.referenceRgb[0] > red.referenceRgb[2])
        val green = ccc.patches.first { it.row == 2 && it.col == 1 }
        assertTrue("green G should dominate (got ${green.referenceRgb.toList()})",
            green.referenceRgb[1] > green.referenceRgb[0] && green.referenceRgb[1] > green.referenceRgb[2])
        val blue = ccc.patches.first { it.row == 2 && it.col == 0 }
        assertTrue("blue B should dominate (got ${blue.referenceRgb.toList()})",
            blue.referenceRgb[2] > blue.referenceRgb[0] && blue.referenceRgb[2] > blue.referenceRgb[1])
    }

    // ---------- Generic 24 ----------

    @Test
    fun `Generic24 has 24 patches with 18 hue swatches and 6 grays`() {
        val gen = BundledReferenceTargets.Generic24
        assertEquals(24, gen.patches.size)
        assertEquals(18, gen.colorPatches.size)
        assertEquals(6, gen.neutralPatches.size)
    }

    @Test
    fun `Generic24 hue swatches are valid and in 0_1 range`() {
        val gen = BundledReferenceTargets.Generic24
        for (patch in gen.colorPatches) {
            for ((ch, v) in patch.referenceRgb.withIndex()) {
                assertTrue(
                    "${patch.name} ch=$ch out of range: $v",
                    v in 0f..1f || abs(v) < 1e-6f,
                )
            }
        }
    }

    // ---------- Catalog ----------

    @Test
    fun `byId resolves bundled targets`() {
        assertNotNull(BundledReferenceTargets.byId("colorchecker24"))
        assertNotNull(BundledReferenceTargets.byId("generic24"))
    }

    @Test
    fun `byId throws on unknown target`() {
        val ex = runCatching { BundledReferenceTargets.byId("does-not-exist") }.exceptionOrNull()
        assertNotNull(ex)
    }

    @Test
    fun `All contains every bundled target with no duplicates`() {
        val all = BundledReferenceTargets.All
        val ids = all.map { it.id }
        assertEquals(ids.distinct().size, ids.size)
        assertTrue(ids.contains("colorchecker24"))
        assertTrue(ids.contains("generic24"))
    }

    // ---------- Patch equality ----------

    @Test
    fun `Patch equality uses contentEquals on referenceRgb`() {
        val p1 = ReferenceTarget.Patch(0, 0, "x", ReferenceTarget.PatchRole.Color,
            floatArrayOf(0.1f, 0.2f, 0.3f))
        val p2 = ReferenceTarget.Patch(0, 0, "x", ReferenceTarget.PatchRole.Color,
            floatArrayOf(0.1f, 0.2f, 0.3f))
        assertEquals(p1, p2)
        assertEquals(p1.hashCode(), p2.hashCode())
    }

    private fun makePatch(row: Int, col: Int): ReferenceTarget.Patch =
        ReferenceTarget.Patch(
            row = row, col = col, name = "p${row}_${col}",
            role = ReferenceTarget.PatchRole.Color,
            referenceRgb = floatArrayOf(0.5f, 0.5f, 0.5f),
        )
}
