package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkingSpaceTest {

    private val tol = 1e-3f

    @Test
    fun `every shipped preset is in ALL`() {
        val expected = listOf(
            WorkingSpace.SRGB,
            WorkingSpace.REC709_SDR,
            WorkingSpace.REC2020_PQ,
            WorkingSpace.REC2020_HLG,
            WorkingSpace.DCI_P3,
            WorkingSpace.ACES_AP1_LINEAR,
        )
        assertEquals(expected, WorkingSpace.ALL)
    }

    @Test
    fun `every preset has a unique id`() {
        val ids = WorkingSpace.ALL.map { it.id }
        assertEquals("duplicate ids: $ids", ids.size, ids.toSet().size)
    }

    @Test
    fun `every preset has a non-blank displayName`() {
        for (ws in WorkingSpace.ALL) {
            assertTrue("blank displayName for ${ws.id}", ws.displayName.isNotBlank())
        }
    }

    @Test
    fun `byId resolves every shipped preset`() {
        for (ws in WorkingSpace.ALL) {
            assertEquals(ws, WorkingSpace.byId(ws.id))
        }
    }

    @Test
    fun `byId returns null for unknown id`() {
        assertNull(WorkingSpace.byId("unknown-space"))
        assertNull(WorkingSpace.byId(""))
    }

    @Test
    fun `sRGB CICP is BT 709 sRGB identity full-range`() {
        val c = WorkingSpace.SRGB.cicp
        assertEquals(1, c.colourPrimaries)
        assertEquals(13, c.transferCharacteristics)
        assertEquals(0, c.matrixCoefficients)
        assertTrue(c.videoFullRangeFlag)
    }

    @Test
    fun `Rec2020 PQ CICP is BT 2020 PQ NCL full-range`() {
        val c = WorkingSpace.REC2020_PQ.cicp
        assertEquals(9, c.colourPrimaries)
        assertEquals(16, c.transferCharacteristics)
        assertEquals(9, c.matrixCoefficients)
        assertTrue(c.videoFullRangeFlag)
    }

    @Test
    fun `Rec2020 HLG CICP is BT 2020 HLG NCL full-range`() {
        val c = WorkingSpace.REC2020_HLG.cicp
        assertEquals(9, c.colourPrimaries)
        assertEquals(18, c.transferCharacteristics)
        assertEquals(9, c.matrixCoefficients)
        assertTrue(c.videoFullRangeFlag)
    }

    @Test
    fun `Display P3 CICP is RP 431-2 D65 sRGB identity full-range`() {
        val c = WorkingSpace.DCI_P3.cicp
        assertEquals(12, c.colourPrimaries)
        assertEquals(13, c.transferCharacteristics)
        assertEquals(0, c.matrixCoefficients)
        assertTrue(c.videoFullRangeFlag)
    }

    @Test
    fun `Rec709 SDR CICP is BT 709 narrow-range BT 709 YCbCr`() {
        val c = WorkingSpace.REC709_SDR.cicp
        assertEquals(1, c.colourPrimaries)
        assertEquals(1, c.transferCharacteristics)
        assertEquals(1, c.matrixCoefficients)
        assertEquals(false, c.videoFullRangeFlag)
    }

    @Test
    fun `ACES AP1 linear CICP is unspecified-linear-RGB`() {
        val c = WorkingSpace.ACES_AP1_LINEAR.cicp
        assertEquals(2, c.colourPrimaries)
        assertEquals(8, c.transferCharacteristics)
        assertEquals(0, c.matrixCoefficients)
    }

    // ---------- Transfer-function plumbing ----------

    @Test
    fun `sRGB toLinear and fromLinear round-trip`() {
        val ws = WorkingSpace.SRGB
        for (i in 0..32) {
            val v = i / 32f
            val rt = ws.fromLinear(ws.toLinear(v))
            assertEquals(v, rt, tol)
        }
    }

    @Test
    fun `PQ toLinear and fromLinear round-trip`() {
        val ws = WorkingSpace.REC2020_PQ
        for (i in 0..32) {
            val v = i / 32f
            val rt = ws.fromLinear(ws.toLinear(v))
            assertEquals(v, rt, tol)
        }
    }

    @Test
    fun `HLG toLinear and fromLinear round-trip`() {
        val ws = WorkingSpace.REC2020_HLG
        for (i in 0..32) {
            val v = i / 32f
            val rt = ws.fromLinear(ws.toLinear(v))
            assertEquals(v, rt, tol)
        }
    }

    @Test
    fun `ACES linear toLinear is identity`() {
        val ws = WorkingSpace.ACES_AP1_LINEAR
        for (i in 0..16) {
            val v = i / 16f
            assertEquals(v, ws.toLinear(v), 0f)
            assertEquals(v, ws.fromLinear(v), 0f)
        }
    }

    // ---------- linearToXyz / toXyzD65 ----------

    @Test
    fun `linearToXyz on white returns the working-space whitepoint XYZ`() {
        val one = floatArrayOf(1f, 1f, 1f)
        for (ws in WorkingSpace.ALL) {
            val xyz = ws.linearToXyz(one)
            val wp = ColorSpaceMatrix.whitepointXyz(ws.primaries.whitepoint)
            assertEquals("${ws.id} X", wp[0], xyz[0], 1e-2f)
            assertEquals("${ws.id} Y", wp[1], xyz[1], 1e-3f)
            assertEquals("${ws.id} Z", wp[2], xyz[2], 1e-2f)
        }
    }

    @Test
    fun `toXyzD65 on a D65 working space matches linearToXyz exactly`() {
        val rgb = floatArrayOf(0.5f, 0.25f, 0.75f)
        for (ws in listOf(WorkingSpace.SRGB, WorkingSpace.REC2020_PQ, WorkingSpace.REC2020_HLG, WorkingSpace.DCI_P3)) {
            val direct = ws.linearToXyz(rgb)
            val adapted = ws.toXyzD65(rgb)
            assertEquals("${ws.id} X", direct[0], adapted[0], 1e-6f)
            assertEquals("${ws.id} Y", direct[1], adapted[1], 1e-6f)
            assertEquals("${ws.id} Z", direct[2], adapted[2], 1e-6f)
        }
    }

    @Test
    fun `toXyzD65 on ACES AP1 differs from linearToXyz (D60 source)`() {
        val rgb = floatArrayOf(0.5f, 0.25f, 0.75f)
        val direct = WorkingSpace.ACES_AP1_LINEAR.linearToXyz(rgb)
        val adapted = WorkingSpace.ACES_AP1_LINEAR.toXyzD65(rgb)
        // Bradford D60 -> D65 should change at least one component noticeably.
        val deltaSum = kotlin.math.abs(direct[0] - adapted[0]) +
            kotlin.math.abs(direct[1] - adapted[1]) +
            kotlin.math.abs(direct[2] - adapted[2])
        assertTrue("delta=$deltaSum", deltaSum > 1e-3f)
    }

    @Test
    fun `linearToXyz rejects wrong-length input`() {
        try {
            WorkingSpace.SRGB.linearToXyz(floatArrayOf(1f, 2f))
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    // ---------- Cicp ----------

    @Test
    fun `Cicp init rejects out-of-range fields`() {
        try {
            Cicp(colourPrimaries = -1, transferCharacteristics = 0, matrixCoefficients = 0, videoFullRangeFlag = true)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        try {
            Cicp(colourPrimaries = 0, transferCharacteristics = 256, matrixCoefficients = 0, videoFullRangeFlag = true)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        try {
            Cicp(colourPrimaries = 0, transferCharacteristics = 0, matrixCoefficients = 9999, videoFullRangeFlag = true)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `Cicp describe formats sRGB correctly`() {
        val c = WorkingSpace.SRGB.cicp
        assertEquals("BT.709 / sRGB / RGB-identity / full-range", c.describe())
    }

    @Test
    fun `Cicp describe formats Rec2020 PQ correctly`() {
        val c = WorkingSpace.REC2020_PQ.cicp
        assertEquals("BT.2020 / PQ / BT.2020-NCL / full-range", c.describe())
    }

    @Test
    fun `Cicp describe formats Rec709 SDR with narrow-range`() {
        val c = WorkingSpace.REC709_SDR.cicp
        assertEquals("BT.709 / BT.709 / BT.709 / narrow-range", c.describe())
    }

    @Test
    fun `Cicp describe falls back to literal codes for unknown values`() {
        val c = Cicp(colourPrimaries = 99, transferCharacteristics = 99, matrixCoefficients = 99, videoFullRangeFlag = true)
        assertEquals("primaries=99 / transfer=99 / matrix=99 / full-range", c.describe())
    }

    @Test
    fun `every preset's primaries match its declared primaries`() {
        assertEquals(ColorSpaceMatrix.SRGB_PRIMARIES, WorkingSpace.SRGB.primaries)
        assertEquals(ColorSpaceMatrix.SRGB_PRIMARIES, WorkingSpace.REC709_SDR.primaries)
        assertEquals(ColorSpaceMatrix.REC2020_PRIMARIES, WorkingSpace.REC2020_PQ.primaries)
        assertEquals(ColorSpaceMatrix.REC2020_PRIMARIES, WorkingSpace.REC2020_HLG.primaries)
        assertEquals(ColorSpaceMatrix.DCI_P3_PRIMARIES, WorkingSpace.DCI_P3.primaries)
        assertEquals(ColorSpaceMatrix.ACES_AP1_PRIMARIES, WorkingSpace.ACES_AP1_LINEAR.primaries)
    }

    @Test
    fun `schema version is pinned`() {
        assertEquals(1, WorkingSpace.SCHEMA_VERSION)
    }

    @Test
    fun `every preset's CICP is constructible (no init failures)`() {
        for (ws in WorkingSpace.ALL) {
            assertNotNull(ws.cicp)
        }
    }
}
