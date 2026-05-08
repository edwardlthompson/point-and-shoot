package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncoderResultAggregatorTest {

    private fun attempt(
        camera: String,
        kind: SessionKind = SessionKind.Hfr,
        w: Int = 1920,
        h: Int = 1080,
        lo: Int = 240,
        hi: Int = 240,
        mime: String? = "video/avc",
        ok: Boolean = true,
        fps: Double = 240.0,
        note: String = "ok",
    ) = EncoderAttempt(
        cameraId = camera,
        sessionKind = kind,
        width = w,
        height = h,
        fpsLower = lo,
        fpsUpper = hi,
        mime = mime,
        ok = ok,
        measuredFps = fps,
        note = note,
    )

    @Test
    fun `empty input yields empty summary`() {
        val s = EncoderResultAggregator.summarize(emptyList())
        assertEquals(0, s.totalAttempts)
        assertEquals(0, s.totalOk)
        assertEquals(0, s.totalFail)
        assertTrue(s.knownGood.isEmpty())
        assertTrue(s.knownBad.isEmpty())
        assertTrue(s.byCamera.isEmpty())
        assertTrue(s.canonicalErrors.isEmpty())
    }

    @Test
    fun `mixed batch totals are correct`() {
        val s = EncoderResultAggregator.summarize(
            listOf(
                attempt("0", ok = true),
                attempt("0", ok = false, note = "errno -38 Function not implemented"),
                attempt("2", ok = true, hi = 480, fps = 480.0),
                attempt("2", ok = false, note = "mime=video/hevc unsupported"),
                attempt("2", ok = false, note = "mime=video/avc unsupported"),
            ),
        )
        assertEquals(5, s.totalAttempts)
        assertEquals(2, s.totalOk)
        assertEquals(3, s.totalFail)
    }

    @Test
    fun `knownGood is sorted by camera then descending fps then descending area`() {
        val a = attempt("0", hi = 240, w = 1280, h = 720)
        val b = attempt("0", hi = 480, w = 1280, h = 720)        // higher fps -> first within "0"
        val c = attempt("0", hi = 480, w = 1920, h = 1080)       // higher fps + larger area -> first
        val d = attempt("2", hi = 240)                           // separate camera
        val s = EncoderResultAggregator.summarize(listOf(d, a, b, c))
        assertEquals(listOf(c, b, a, d), s.knownGood)
    }

    @Test
    fun `byCamera roll-up reports per-camera best fps for each session kind`() {
        val s = EncoderResultAggregator.summarize(
            listOf(
                attempt("0", kind = SessionKind.Hfr, hi = 480),
                attempt("0", kind = SessionKind.Hfr, hi = 240),
                attempt("0", kind = SessionKind.Regular, hi = 60),
                attempt("0", kind = SessionKind.Regular, hi = 30, ok = false, note = "errno -22 Invalid argument"),
                attempt("3", kind = SessionKind.Hfr, hi = 240),
            ),
        )
        val cam0 = s.byCamera["0"]
        assertNotNull(cam0)
        assertEquals(3, cam0!!.ok)
        assertEquals(1, cam0.fail)
        assertEquals(480, cam0.bestHfrFps)
        assertEquals(60, cam0.bestRegularFps)

        val cam3 = s.byCamera["3"]
        assertNotNull(cam3)
        assertEquals(240, cam3!!.bestHfrFps)
        assertEquals(0, cam3.bestRegularFps) // no regular successes
    }

    @Test
    fun `canonicalErrors groups errno-tail notes by errno code`() {
        val s = EncoderResultAggregator.summarize(
            listOf(
                attempt("0", ok = false, note = "errno -38 Function not implemented at line 41"),
                attempt("2", ok = false, note = "errno -38 Function not implemented somewhere else"),
                attempt("3", ok = false, note = "mime=video/hevc unsupported"),
            ),
        )
        // Different message tails collapse to the same errno key so the failure mode is one row.
        assertEquals(2, s.canonicalErrors["errno -38"])
        assertEquals(1, s.canonicalErrors["mime unsupported"])
    }

    @Test
    fun `bestHfrRecipe returns the highest-fps known-good HFR row for a camera`() {
        val s = EncoderResultAggregator.summarize(
            listOf(
                attempt("4", kind = SessionKind.Hfr, hi = 120, w = 1920, h = 1080),
                attempt("4", kind = SessionKind.Hfr, hi = 240, w = 1280, h = 720),
                attempt("4", kind = SessionKind.Regular, hi = 60),
            ),
        )
        val best = EncoderResultAggregator.bestHfrRecipe(s, cameraId = "4")
        assertNotNull(best)
        assertEquals(240, best!!.fpsUpper)
        assertEquals(1280, best.width)
    }

    @Test
    fun `bestHfrRecipe returns null when the camera has no HFR successes`() {
        val s = EncoderResultAggregator.summarize(
            listOf(
                attempt("4", kind = SessionKind.Regular, hi = 60),
                attempt("4", kind = SessionKind.Hfr, hi = 240, ok = false, note = "errno -38 Function not implemented"),
            ),
        )
        assertNull(EncoderResultAggregator.bestHfrRecipe(s, cameraId = "4"))
    }

    @Test
    fun `canonicalize handles common patterns`() {
        // Errno-tagged notes collapse to just the errno code (different tails group together).
        assertEquals("errno -38", EncoderResultAggregator.canonicalize("errno -38 Function not implemented"))
        assertEquals("errno -38", EncoderResultAggregator.canonicalize("errno -38 Function not implemented at line 41 of foo.c"))
        assertEquals("errno -22", EncoderResultAggregator.canonicalize("errno -22 invalid argument"))
        // Mime-unsupported strips the mime token.
        assertEquals("mime unsupported", EncoderResultAggregator.canonicalize("mime=video/avc unsupported on this codec"))
        // Empty / whitespace-only stays empty.
        assertEquals("", EncoderResultAggregator.canonicalize("   "))
        // Free-form first sentence is preserved (capped at 120 chars).
        assertEquals("encoder configure failed: invalid format", EncoderResultAggregator.canonicalize("encoder configure failed: invalid format\nadditional details"))
    }

    @Test
    fun `extractMimeFromNote pulls a mime token when present`() {
        assertEquals("video/avc", EncoderAttempt.extractMimeFromNote("mime=video/avc unsupported"))
        assertEquals("video/hevc", EncoderAttempt.extractMimeFromNote("starting mime=video/hevc encoder ..."))
        assertNull(EncoderAttempt.extractMimeFromNote("encoder configure failed"))
    }
}
