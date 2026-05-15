package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncoderRecipeBuilderTest {

    private fun ok(
        cam: String,
        kind: SessionKind,
        w: Int, h: Int,
        fps: Int,
        mime: String?,
        note: String = "ok",
    ): EncoderAttempt = EncoderAttempt(
        cameraId = cam,
        sessionKind = kind,
        width = w,
        height = h,
        fpsLower = fps,
        fpsUpper = fps,
        mime = mime,
        ok = true,
        measuredFps = fps.toDouble(),
        note = note,
    )

    private fun fail(cam: String, kind: SessionKind, w: Int, h: Int, fps: Int,
                    mime: String?, note: String): EncoderAttempt = EncoderAttempt(
        cameraId = cam, sessionKind = kind, width = w, height = h,
        fpsLower = fps, fpsUpper = fps, mime = mime, ok = false,
        measuredFps = 0.0, note = note,
    )

    // ---------- recipesFromSummary ----------

    @Test
    fun `empty summary yields no recipe rows`() {
        val summary = EncoderResultAggregator.summarize(emptyList())
        assertEquals(emptyList<EncoderRecipeBuilder.Row>(),
            EncoderRecipeBuilder.recipesFromSummary(summary))
    }

    @Test
    fun `picks one HFR and one Regular per camera`() {
        val attempts = listOf(
            ok("0", SessionKind.Hfr, 1920, 1080, 480, "video/avc"),
            ok("0", SessionKind.Hfr, 1280, 720, 240, "video/avc"),       // worse HFR; should be skipped
            ok("0", SessionKind.Regular, 3840, 2160, 60, "video/hevc"),
            ok("0", SessionKind.Regular, 1920, 1080, 30, "video/avc"),  // worse Regular; should be skipped
            ok("2", SessionKind.Hfr, 1920, 1080, 240, "video/avc"),
        )
        val summary = EncoderResultAggregator.summarize(attempts)
        val rows = EncoderRecipeBuilder.recipesFromSummary(summary)
        assertEquals(3, rows.size)
        assertEquals(SessionKind.Hfr, rows[0].sessionKind)
        assertEquals("0", rows[0].cameraId)
        assertEquals("1920x1080", rows[0].sizeLabel)
        assertEquals(SessionKind.Regular, rows[1].sessionKind)
        assertEquals("0", rows[1].cameraId)
        assertEquals(SessionKind.Hfr, rows[2].sessionKind)
        assertEquals("2", rows[2].cameraId)
    }

    @Test
    fun `skips camera with no successes`() {
        val attempts = listOf(
            ok("0", SessionKind.Hfr, 1920, 1080, 240, "video/avc"),
            fail("1", SessionKind.Hfr, 1920, 1080, 240, "video/avc", "errno -38"),
        )
        val summary = EncoderResultAggregator.summarize(attempts)
        val rows = EncoderRecipeBuilder.recipesFromSummary(summary)
        assertEquals(1, rows.size)
        assertEquals("0", rows[0].cameraId)
    }

    @Test
    fun `recipes deterministic across summary reorderings`() {
        val a = listOf(
            ok("0", SessionKind.Hfr, 1920, 1080, 480, "video/avc"),
            ok("2", SessionKind.Regular, 3840, 2160, 60, "video/hevc"),
        )
        val b = a.reversed()
        val rowsA = EncoderRecipeBuilder.recipesFromSummary(
            EncoderResultAggregator.summarize(a),
        )
        val rowsB = EncoderRecipeBuilder.recipesFromSummary(
            EncoderResultAggregator.summarize(b),
        )
        assertEquals(rowsA, rowsB)
    }

    @Test
    fun `recovers mime from note when first-class mime is null`() {
        val summary = EncoderResultAggregator.summarize(listOf(
            ok("0", SessionKind.Hfr, 1920, 1080, 240, mime = null,
                note = "ok mime=video/x-vp9 success"),
        ))
        val rows = EncoderRecipeBuilder.recipesFromSummary(summary)
        assertEquals(1, rows.size)
        assertEquals("video/x-vp9", rows[0].mime)
    }

    @Test
    fun `recipes attach HAL HFR max when map provided`() {
        val summary = EncoderResultAggregator.summarize(
            listOf(ok("2", SessionKind.Hfr, 1920, 1080, 240, "video/avc")),
        )
        val withHal = EncoderRecipeBuilder.recipesFromSummary(summary, mapOf("2" to 480))
        assertEquals(480, withHal[0].halAdvertisedHfrMaxFps)
        val withoutHal = EncoderRecipeBuilder.recipesFromSummary(summary)
        assertNull(withoutHal[0].halAdvertisedHfrMaxFps)
    }

    // ---------- errorRowsFromSummary ----------

    @Test
    fun `errorRows surface the most-common canonical errors first`() {
        val attempts = listOf(
            fail("0", SessionKind.Hfr, 1920, 1080, 240, "video/avc", "errno -38 not implemented"),
            fail("0", SessionKind.Hfr, 1920, 1080, 240, "video/hevc", "errno -38 missing"),
            fail("2", SessionKind.Hfr, 1280, 720, 240, "video/hevc", "errno -22 invalid"),
            fail("0", SessionKind.Regular, 3840, 2160, 60, "video/avc", "encoder configure failed: invalid format"),
        )
        val summary = EncoderResultAggregator.summarize(attempts)
        val rows = EncoderRecipeBuilder.errorRowsFromSummary(summary)
        // -38 appears twice, -22 once, configure-failed once. Most frequent first.
        assertEquals("errno -38", rows[0].canonicalError)
        assertEquals(2, rows[0].count)
        assertTrue(rows.size in 2..3)
    }

    @Test
    fun `errorRows respects the max cap`() {
        val attempts = (1..10).map { idx ->
            fail("0", SessionKind.Hfr, 1920, 1080, 240, "video/avc", "errno -$idx fail")
        }
        val summary = EncoderResultAggregator.summarize(attempts)
        val rows = EncoderRecipeBuilder.errorRowsFromSummary(summary, maxRows = 3)
        assertEquals(3, rows.size)
    }

    @Test
    fun `errorRows on empty input is empty`() {
        val summary = EncoderResultAggregator.summarize(emptyList())
        assertEquals(emptyList<EncoderRecipeBuilder.ErrorRow>(),
            EncoderRecipeBuilder.errorRowsFromSummary(summary))
    }

    @Test
    fun `errorRowsFromSummary rejects non-positive maxRows`() {
        val summary = EncoderResultAggregator.summarize(emptyList())
        val ex = runCatching {
            EncoderRecipeBuilder.errorRowsFromSummary(summary, maxRows = 0)
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    // ---------- headlineCounts ----------

    @Test
    fun `headlineCounts is null for null summary`() {
        assertNull(EncoderRecipeBuilder.headlineCounts(null))
    }

    @Test
    fun `headlineCounts is null for empty summary`() {
        val summary = EncoderResultAggregator.summarize(emptyList())
        assertNull(EncoderRecipeBuilder.headlineCounts(summary))
    }

    @Test
    fun `headlineCounts reports totals and ok percent`() {
        val attempts = listOf(
            ok("0", SessionKind.Hfr, 1920, 1080, 240, "video/avc"),
            ok("0", SessionKind.Regular, 3840, 2160, 60, "video/hevc"),
            fail("2", SessionKind.Hfr, 1920, 1080, 240, "video/hevc", "errno -38"),
        )
        val counts = EncoderRecipeBuilder.headlineCounts(EncoderResultAggregator.summarize(attempts))!!
        assertEquals(3, counts.totalAttempts)
        assertEquals(2, counts.totalOk)
        assertEquals(1, counts.totalFail)
        assertEquals(2, counts.cameraCount)
        assertEquals(67, counts.okPercent)  // 2/3 = 66.67% rounded to 67%
    }

    @Test
    fun `headlineCounts okPercent is 0 when totalAttempts is 0`() {
        val counts = EncoderRecipeBuilder.HeadlineCounts(
            totalAttempts = 0, totalOk = 0, totalFail = 0, cameraCount = 0,
        )
        assertEquals(0, counts.okPercent)
    }
}
