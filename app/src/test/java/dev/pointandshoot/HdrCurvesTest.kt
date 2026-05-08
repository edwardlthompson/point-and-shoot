package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HdrCurvesTest {

    private val tol = 1e-4f

    // ---------- sRGB ----------

    @Test
    fun `sRGB linear-segment maps zero to zero and small encoded values divide by 12,92`() {
        assertEquals(0f, HdrCurves.srgbToLinear(0f), tol)
        // mid-linear-segment: 0.04 / 12.92
        assertEquals(0.04f / 12.92f, HdrCurves.srgbToLinear(0.04f), tol)
    }

    @Test
    fun `sRGB EOTF then OETF round-trips on a sweep`() {
        for (i in 0..256) {
            val v = i / 256f
            val rt = HdrCurves.linearToSrgb(HdrCurves.srgbToLinear(v))
            assertEquals("v=$v", v, rt, tol)
        }
    }

    @Test
    fun `sRGB OETF then EOTF round-trips on a linear sweep`() {
        for (i in 0..256) {
            val v = i / 256f
            val rt = HdrCurves.srgbToLinear(HdrCurves.linearToSrgb(v))
            assertEquals("v=$v", v, rt, tol)
        }
    }

    @Test
    fun `sRGB at unity is unity`() {
        assertEquals(1f, HdrCurves.srgbToLinear(1f), tol)
        assertEquals(1f, HdrCurves.linearToSrgb(1f), tol)
    }

    @Test
    fun `sRGB middle gray (0,5) encodes to roughly 0,73`() {
        // Standard reference: sRGB(0.5) -> ~0.7353 (canonical mid-gray test).
        val out = HdrCurves.linearToSrgb(0.5f)
        assertTrue("got $out", out in 0.73f..0.74f)
    }

    // ---------- Rec.709 ----------

    @Test
    fun `Rec709 round-trips through both directions`() {
        for (i in 0..256) {
            val v = i / 256f
            val rt1 = HdrCurves.linearToRec709(HdrCurves.rec709ToLinear(v))
            val rt2 = HdrCurves.rec709ToLinear(HdrCurves.linearToRec709(v))
            assertEquals("encoded round-trip v=$v", v, rt1, tol)
            assertEquals("linear round-trip v=$v", v, rt2, tol)
        }
    }

    @Test
    fun `Rec709 at unity is unity`() {
        assertEquals(1f, HdrCurves.rec709ToLinear(1f), tol)
        assertEquals(1f, HdrCurves.linearToRec709(1f), tol)
    }

    @Test
    fun `Rec709 at zero is zero`() {
        assertEquals(0f, HdrCurves.rec709ToLinear(0f), tol)
        assertEquals(0f, HdrCurves.linearToRec709(0f), tol)
    }

    @Test
    fun `Rec709 linear segment matches the BT 709 slope below the break point`() {
        // Below the 0.018 break point, the OETF is exactly 4.5 * linear (BT.709-6 formula 1.2).
        val out = HdrCurves.linearToRec709(0.01f)
        assertEquals(4.5f * 0.01f, out, tol)
    }

    // ---------- PQ (SMPTE ST 2084) ----------

    @Test
    fun `PQ peak nits constant matches spec`() {
        assertEquals(10000f, HdrCurves.PQ_PEAK_NITS)
    }

    @Test
    fun `PQ at zero is zero`() {
        assertEquals(0f, HdrCurves.linearToPq(0f), tol)
        assertEquals(0f, HdrCurves.pqToLinear(0f), tol)
    }

    @Test
    fun `PQ at unity is unity`() {
        assertEquals(1f, HdrCurves.linearToPq(1f), tol)
        assertEquals(1f, HdrCurves.pqToLinear(1f), 0.001f)
    }

    @Test
    fun `PQ round-trips on a sweep`() {
        for (i in 0..256) {
            val v = i / 256f
            val rt1 = HdrCurves.linearToPq(HdrCurves.pqToLinear(v))
            val rt2 = HdrCurves.pqToLinear(HdrCurves.linearToPq(v))
            assertEquals("encoded round-trip v=$v", v, rt1, 0.001f)
            assertEquals("linear round-trip v=$v", v, rt2, 0.001f)
        }
    }

    @Test
    fun `PQ encoded mid value roughly matches Rec2100 reference`() {
        // BT.2100 Annex 4 reference: 100 cd/m^2 (i.e. linear=0.01) -> ~0.5081 PQ encoded.
        val out = HdrCurves.linearToPq(0.01f)
        assertTrue("got $out", out in 0.50f..0.52f)
    }

    // ---------- HLG (BT.2100) ----------

    @Test
    fun `HLG peak nits constant is 1000`() {
        assertEquals(1000f, HdrCurves.HLG_PEAK_NITS)
    }

    @Test
    fun `HLG at zero is zero`() {
        assertEquals(0f, HdrCurves.linearToHlg(0f), tol)
        assertEquals(0f, HdrCurves.hlgToLinear(0f), tol)
    }

    @Test
    fun `HLG at unity is unity`() {
        assertEquals(1f, HdrCurves.linearToHlg(1f), 1e-3f)
        assertEquals(1f, HdrCurves.hlgToLinear(1f), 1e-3f)
    }

    @Test
    fun `HLG round-trips on a sweep`() {
        for (i in 0..256) {
            val v = i / 256f
            val rt1 = HdrCurves.linearToHlg(HdrCurves.hlgToLinear(v))
            val rt2 = HdrCurves.hlgToLinear(HdrCurves.linearToHlg(v))
            assertEquals("encoded round-trip v=$v", v, rt1, 1e-3f)
            assertEquals("linear round-trip v=$v", v, rt2, 1e-3f)
        }
    }

    @Test
    fun `HLG break point at one-twelfth maps to one-half encoded`() {
        // BT.2100 Table 5: linear = 1/12 corresponds to encoded = 0.5.
        val out = HdrCurves.linearToHlg(1f / 12f)
        assertEquals(0.5f, out, 1e-3f)
    }

    // ---------- Identity ----------

    @Test
    fun `identity is identity`() {
        for (i in 0..16) {
            val v = i / 16f
            assertEquals(v, HdrCurves.identity(v), 0f)
        }
    }

    @Test
    fun `schema version is pinned`() {
        assertEquals(1, HdrCurves.SCHEMA_VERSION)
    }

    // ---------- Cross-curve sanity ----------

    @Test
    fun `every curve is monotonically non-decreasing on the unit interval`() {
        val curves = listOf<Pair<String, (Float) -> Float>>(
            "srgbToLinear" to HdrCurves::srgbToLinear,
            "linearToSrgb" to HdrCurves::linearToSrgb,
            "rec709ToLinear" to HdrCurves::rec709ToLinear,
            "linearToRec709" to HdrCurves::linearToRec709,
            "pqToLinear" to HdrCurves::pqToLinear,
            "linearToPq" to HdrCurves::linearToPq,
            "linearToHlg" to HdrCurves::linearToHlg,
            "hlgToLinear" to HdrCurves::hlgToLinear,
        )
        for ((name, fn) in curves) {
            var prev = fn(0f)
            for (i in 1..256) {
                val v = i / 256f
                val out = fn(v)
                assertTrue("$name not monotonic at v=$v: prev=$prev out=$out", out >= prev - 1e-5f)
                prev = out
            }
        }
    }
}
