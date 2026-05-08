package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationProfileJsonAdapterTest {

    private fun nonTrivialProfile(mtf50: Float? = 1647.5f): CalibrationProfile = CalibrationProfile(
        wbGains = CalibrationProfile.WbGains(r = 1.18f, g = 1.0f, b = 0.92f),
        ccm = CalibrationProfile.Ccm(
            m00 = 1.05f, m01 = 0.04f, m02 = -0.03f,
            m10 = 0.02f, m11 = 0.95f, m12 = 0.02f,
            m20 = -0.04f, m21 = 0.06f, m22 = 1.02f,
        ),
        bias = CalibrationProfile.Bias(r = 0.001f, g = -0.002f, b = 0.0005f),
        mtf50Lpph = mtf50,
        illuminant = CalibrationProfile.Illuminant.D65,
        capturedAtMs = 1714760000000L,
        cameraId = "0:back:LYT-808",
        targetId = "colorchecker24",
    )

    // ---------- round-trip ----------

    @Test
    fun `encode then decode round-trips a non-trivial profile`() {
        val original = nonTrivialProfile()
        val json = CalibrationProfileJsonAdapter.encode(original)
        val parsed = CalibrationProfileJsonAdapter.decode(json)
        assertEquals(original, parsed)
    }

    @Test
    fun `encode then decode round-trips an identity profile`() {
        val original = CalibrationProfile.identity(cameraId = "front", targetId = "generic24")
        val json = CalibrationProfileJsonAdapter.encode(original)
        val parsed = CalibrationProfileJsonAdapter.decode(json)
        assertEquals(original, parsed)
    }

    @Test
    fun `encode omits mtf50Lpph when null`() {
        val original = nonTrivialProfile(mtf50 = null)
        val json = CalibrationProfileJsonAdapter.encode(original)
        assertTrue("expected mtf50Lpph to be absent in:\n$json", !json.contains("mtf50Lpph"))
        val parsed = CalibrationProfileJsonAdapter.decode(json)
        assertNull(parsed.mtf50Lpph)
    }

    @Test
    fun `encoded JSON contains the documented top-level keys`() {
        val json = CalibrationProfileJsonAdapter.encode(nonTrivialProfile())
        for (key in listOf("version", "cameraId", "targetId", "illuminant",
            "capturedAtMs", "wbGains", "ccm", "bias", "mtf50Lpph")) {
            assertTrue("expected key '$key' in:\n$json", json.contains("\"$key\""))
        }
    }

    @Test
    fun `encoded version matches SCHEMA_VERSION`() {
        val json = CalibrationProfileJsonAdapter.encode(nonTrivialProfile())
        assertTrue("expected version=${CalibrationProfileJsonAdapter.SCHEMA_VERSION} in:\n$json",
            json.contains("\"version\": ${CalibrationProfileJsonAdapter.SCHEMA_VERSION}"))
    }

    // ---------- decode failures ----------

    @Test
    fun `decode rejects malformed JSON`() {
        val ex = runCatching { CalibrationProfileJsonAdapter.decode("not json {") }.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `decode rejects unknown version`() {
        val json = """
            {"version": 99, "cameraId": "x", "targetId": "y", "illuminant": "D65",
             "capturedAtMs": 0, "wbGains": {"r":1,"g":1,"b":1},
             "ccm": [[1,0,0],[0,1,0],[0,0,1]], "bias": [0,0,0]}
        """.trimIndent()
        val ex = runCatching { CalibrationProfileJsonAdapter.decode(json) }.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex!!.message!!.contains("version"))
    }

    @Test
    fun `decode rejects unknown illuminant`() {
        val json = """
            {"version": 1, "cameraId": "x", "targetId": "y", "illuminant": "MoonGlow",
             "capturedAtMs": 0, "wbGains": {"r":1,"g":1,"b":1},
             "ccm": [[1,0,0],[0,1,0],[0,0,1]], "bias": [0,0,0]}
        """.trimIndent()
        val ex = runCatching { CalibrationProfileJsonAdapter.decode(json) }.exceptionOrNull()
        assertNotNull(ex)
        assertTrue("expected illuminant in error: ${ex!!.message}",
            ex.message!!.contains("illuminant"))
    }

    @Test
    fun `decode rejects missing wbGains`() {
        val json = """
            {"version": 1, "cameraId": "x", "targetId": "y", "illuminant": "D65",
             "capturedAtMs": 0,
             "ccm": [[1,0,0],[0,1,0],[0,0,1]], "bias": [0,0,0]}
        """.trimIndent()
        val ex = runCatching { CalibrationProfileJsonAdapter.decode(json) }.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex!!.message!!.contains("wbGains"))
    }

    @Test
    fun `decode rejects malformed ccm shape`() {
        val json = """
            {"version": 1, "cameraId": "x", "targetId": "y", "illuminant": "D65",
             "capturedAtMs": 0, "wbGains": {"r":1,"g":1,"b":1},
             "ccm": [[1,0,0],[0,1,0]], "bias": [0,0,0]}
        """.trimIndent()
        val ex = runCatching { CalibrationProfileJsonAdapter.decode(json) }.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex!!.message!!.contains("ccm"))
    }

    @Test
    fun `decode rejects malformed ccm row width`() {
        val json = """
            {"version": 1, "cameraId": "x", "targetId": "y", "illuminant": "D65",
             "capturedAtMs": 0, "wbGains": {"r":1,"g":1,"b":1},
             "ccm": [[1,0],[0,1,0],[0,0,1]], "bias": [0,0,0]}
        """.trimIndent()
        val ex = runCatching { CalibrationProfileJsonAdapter.decode(json) }.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex!!.message!!.contains("ccm row"))
    }

    @Test
    fun `decode rejects negative capturedAtMs`() {
        val json = """
            {"version": 1, "cameraId": "x", "targetId": "y", "illuminant": "D65",
             "capturedAtMs": -5, "wbGains": {"r":1,"g":1,"b":1},
             "ccm": [[1,0,0],[0,1,0],[0,0,1]], "bias": [0,0,0]}
        """.trimIndent()
        val ex = runCatching { CalibrationProfileJsonAdapter.decode(json) }.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex!!.message!!.contains("capturedAtMs"))
    }

    @Test
    fun `decode rejects blank cameraId`() {
        val json = """
            {"version": 1, "cameraId": "", "targetId": "y", "illuminant": "D65",
             "capturedAtMs": 0, "wbGains": {"r":1,"g":1,"b":1},
             "ccm": [[1,0,0],[0,1,0],[0,0,1]], "bias": [0,0,0]}
        """.trimIndent()
        val ex = runCatching { CalibrationProfileJsonAdapter.decode(json) }.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex!!.message!!.contains("cameraId"))
    }

    // ---------- filename helper ----------

    @Test
    fun `filenameFor concatenates illuminant and utc with the json extension`() {
        val profile = nonTrivialProfile()
        val name = CalibrationProfileJsonAdapter.filenameFor(profile, "20260508T1830Z")
        assertEquals("D65_20260508T1830Z.json", name)
    }

    @Test
    fun `filenameFor handles each illuminant constant`() {
        for (ill in CalibrationProfile.Illuminant.entries) {
            val profile = nonTrivialProfile().copy(illuminant = ill)
            val name = CalibrationProfileJsonAdapter.filenameFor(profile, "TS")
            assertTrue("expected name to contain ${ill.name}: $name", name.startsWith("${ill.name}_"))
            assertTrue("expected .json extension: $name", name.endsWith(".json"))
        }
    }

    // ---------- pretty-printing sanity ----------

    @Test
    fun `encoded JSON is multi-line for human-friendly diffing`() {
        val json = CalibrationProfileJsonAdapter.encode(nonTrivialProfile())
        val lineCount = json.lines().size
        assertTrue("expected multi-line JSON, got $lineCount lines:\n$json", lineCount > 5)
    }
}
