package dev.pointandshoot

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncoderAttemptJsonAdapterTest {

    private fun attemptObj(
        ok: Boolean,
        sessionKind: String,
        w: Int,
        h: Int,
        lo: Int,
        hi: Int,
        note: String,
        measuredFps: Double = if (ok) hi.toDouble() else 0.0,
    ): JSONObject = JSONObject()
        .put("ok", ok)
        .put("measuredFps", measuredFps)
        .put("note", note)
        .put("sessionKind", sessionKind)
        .put("size", JSONObject().put("w", w).put("h", h))
        .put("fpsRange", JSONObject().put("lower", lo).put("upper", hi))

    @Test
    fun `decode flattens hfrAttempts and regularAttempts across cameras`() {
        val cam0 = JSONObject()
            .put("cameraId", "0")
            .put(
                "hfrAttempts",
                JSONArray()
                    .put(attemptObj(ok = true, sessionKind = "hfr", w = 1920, h = 1080, lo = 240, hi = 240, note = "starting mime=video/avc"))
                    .put(attemptObj(ok = false, sessionKind = "hfr", w = 1920, h = 1080, lo = 480, hi = 480, note = "errno -38 Function not implemented")),
            )
            .put(
                "regularAttempts",
                JSONArray()
                    .put(attemptObj(ok = true, sessionKind = "regular", w = 3840, h = 2160, lo = 30, hi = 30, note = "starting mime=video/hevc")),
            )
        val cam2 = JSONObject()
            .put("cameraId", "2")
            .put("hfrAttempts", JSONArray().put(attemptObj(ok = true, sessionKind = "hfr", w = 1280, h = 720, lo = 240, hi = 240, note = "ok mime=video/avc")))
            .put("regularAttempts", JSONArray())
        val root = JSONObject().put("cameras", JSONArray().put(cam0).put(cam2))

        val attempts = EncoderAttemptJsonAdapter.decode(root)
        assertEquals(4, attempts.size)
        // First three are camera 0 in array order; last is camera 2.
        assertEquals("0", attempts[0].cameraId)
        assertEquals(SessionKind.Hfr, attempts[0].sessionKind)
        assertEquals(1920, attempts[0].width)
        assertEquals("video/avc", attempts[0].mime)
        assertEquals(true, attempts[0].ok)

        assertEquals(SessionKind.Hfr, attempts[1].sessionKind)
        assertEquals(false, attempts[1].ok)
        assertNull(attempts[1].mime) // note is "errno ..." with no mime token

        assertEquals(SessionKind.Regular, attempts[2].sessionKind)
        assertEquals(3840, attempts[2].width)

        assertEquals("2", attempts[3].cameraId)
    }

    @Test
    fun `decode tolerates missing fields by skipping the offending row`() {
        val cam = JSONObject()
            .put("cameraId", "0")
            .put(
                "hfrAttempts",
                JSONArray()
                    .put(attemptObj(ok = true, sessionKind = "hfr", w = 1920, h = 1080, lo = 240, hi = 240, note = "ok"))
                    .put(JSONObject().put("ok", true)) // missing size + fpsRange -> skipped
                    .put(attemptObj(ok = true, sessionKind = "hfr", w = 1280, h = 720, lo = 240, hi = 240, note = "ok")),
            )
        val root = JSONObject().put("cameras", JSONArray().put(cam))
        val attempts = EncoderAttemptJsonAdapter.decode(root)
        assertEquals(2, attempts.size)
        assertEquals(1920, attempts[0].width)
        assertEquals(1280, attempts[1].width)
    }

    @Test
    fun `decode returns empty list when cameras key is missing`() {
        val root = JSONObject().put("generatedAt", "2026-05-08T00:00:00Z")
        assertTrue(EncoderAttemptJsonAdapter.decode(root).isEmpty())
    }

    @Test
    fun `decode falls back to defaultKind when sessionKind is missing or unknown`() {
        val cam = JSONObject()
            .put("cameraId", "0")
            .put(
                "hfrAttempts",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("ok", true)
                            .put("note", "")
                            .put("size", JSONObject().put("w", 1920).put("h", 1080))
                            .put("fpsRange", JSONObject().put("lower", 240).put("upper", 240))
                            // no sessionKind field -> falls back to defaultKind = Hfr
                    )
                    .put(
                        JSONObject()
                            .put("ok", true)
                            .put("note", "")
                            .put("sessionKind", "totally-bogus")
                            .put("size", JSONObject().put("w", 1280).put("h", 720))
                            .put("fpsRange", JSONObject().put("lower", 240).put("upper", 240))
                    ),
            )
            .put("regularAttempts", JSONArray())
        val root = JSONObject().put("cameras", JSONArray().put(cam))
        val attempts = EncoderAttemptJsonAdapter.decode(root)
        assertEquals(2, attempts.size)
        assertEquals(SessionKind.Hfr, attempts[0].sessionKind)
        assertEquals(SessionKind.Hfr, attempts[1].sessionKind)
    }

    @Test
    fun `decode + summarize end-to-end produces the expected per-camera best-fps`() {
        val cam0 = JSONObject()
            .put("cameraId", "0")
            .put(
                "hfrAttempts",
                JSONArray()
                    .put(attemptObj(ok = true, sessionKind = "hfr", w = 1280, h = 720, lo = 480, hi = 480, note = "ok mime=video/avc"))
                    .put(attemptObj(ok = true, sessionKind = "hfr", w = 1920, h = 1080, lo = 240, hi = 240, note = "ok mime=video/avc")),
            )
            .put("regularAttempts", JSONArray())
        val root = JSONObject().put("cameras", JSONArray().put(cam0))

        val attempts = EncoderAttemptJsonAdapter.decode(root)
        val summary = EncoderResultAggregator.summarize(attempts)
        val cam0Summary = summary.byCamera["0"]!!
        assertEquals(480, cam0Summary.bestHfrFps)
        // bestHfrRecipe should be the 480 fps row even though the 240 fps row has larger area.
        val best = EncoderResultAggregator.bestHfrRecipe(summary, "0")
        assertEquals(480, best?.fpsUpper)
    }
}
