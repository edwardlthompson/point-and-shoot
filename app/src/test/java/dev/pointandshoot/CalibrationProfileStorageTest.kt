package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Exercises the **Context-free** surface of [CalibrationProfileStorage].
 *
 * The Android [android.content.Context]-bound methods (`directory`,
 * `save`, `list`, `latestFor`) require either Robolectric or
 * instrumentation; we deliberately do not ship those test runtimes
 * (BUILD_PLAN.md §0 - keep the toolchain footprint minimal). This suite
 * covers everything that is testable without a Context, plus a real
 * round-trip through [load] using a JUnit-managed temp dir.
 */
class CalibrationProfileStorageTest {

    // ---------- timestamp ----------

    @Test
    fun `nowUtcTimestamp matches the documented yyyyMMdd_HHmmss UTC pattern`() {
        val ts = CalibrationProfileStorage.nowUtcTimestamp()
        // 8 digits, underscore, 6 digits = 15 chars total.
        assertEquals(15, ts.length)
        assertEquals('_', ts[8])
        for (i in 0 until 8) {
            assertTrue("char $i not a digit in $ts", ts[i].isDigit())
        }
        for (i in 9 until 15) {
            assertTrue("char $i not a digit in $ts", ts[i].isDigit())
        }
    }

    @Test
    fun `nowUtcTimestamp two calls are monotonic non-decreasing`() {
        val a = CalibrationProfileStorage.nowUtcTimestamp()
        val b = CalibrationProfileStorage.nowUtcTimestamp()
        assertTrue("expected $a <= $b", a <= b)
    }

    // ---------- subdir + pattern constants ----------

    @Test
    fun `documented constants are stable`() {
        assertEquals("calibration", CalibrationProfileStorage.SUBDIR_NAME)
        assertEquals("yyyyMMdd_HHmmss", CalibrationProfileStorage.TIMESTAMP_PATTERN)
    }

    // ---------- load round-trip via temp dir (no Context) ----------

    @Test
    fun `load round-trips a profile written via the JSON adapter`() {
        val tmpDir = java.nio.file.Files.createTempDirectory("pns-calib-test-").toFile()
        try {
            val profile = sampleProfile()
            val utc = "20260508_001234"
            val filename = CalibrationProfileJsonAdapter.filenameFor(profile, utc)
            val path = File(tmpDir, filename)
            path.writeText(CalibrationProfileJsonAdapter.encode(profile), Charsets.UTF_8)

            val loaded = CalibrationProfileStorage.load(path)
            assertEquals(profile.cameraId, loaded.cameraId)
            assertEquals(profile.targetId, loaded.targetId)
            assertEquals(profile.illuminant, loaded.illuminant)
            assertEquals(profile.capturedAtMs, loaded.capturedAtMs)
            assertEquals(profile.wbGains, loaded.wbGains)
            assertEquals(profile.ccm, loaded.ccm)
            assertEquals(profile.bias, loaded.bias)
            assertEquals(profile.mtf50Lpph, loaded.mtf50Lpph)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `load rejects malformed JSON via the adapter`() {
        val tmpDir = java.nio.file.Files.createTempDirectory("pns-calib-test-").toFile()
        try {
            val path = File(tmpDir, "D65_garbage.json")
            path.writeText("{not json", Charsets.UTF_8)
            CalibrationProfileStorage.load(path)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    // ---------- filename convention ----------

    @Test
    fun `filename convention uses illuminant name + utc + json extension`() {
        val profile = sampleProfile()
        val name = CalibrationProfileJsonAdapter.filenameFor(profile, "20260508_001234")
        assertEquals("D65_20260508_001234.json", name)
        // Storage's latestFor key uses this exact prefix:
        assertTrue(name.startsWith("${profile.illuminant.name}_"))
        assertTrue(name.endsWith(".json"))
    }

    // ---------- helpers ----------

    private fun sampleProfile(): CalibrationProfile = CalibrationProfile(
        wbGains = CalibrationProfile.WbGains(r = 1.18f, g = 1.0f, b = 0.92f),
        ccm = CalibrationProfile.Ccm(
            m00 = 1.05f, m01 = 0.04f, m02 = -0.03f,
            m10 = 0.02f, m11 = 0.95f, m12 = 0.02f,
            m20 = -0.04f, m21 = 0.06f, m22 = 1.02f,
        ),
        bias = CalibrationProfile.Bias(0f, 0f, 0f),
        mtf50Lpph = 1647.5f,
        illuminant = CalibrationProfile.Illuminant.D65,
        capturedAtMs = 1_714_760_000_000L,
        cameraId = "0",
        targetId = "generic-24",
    )
}
