package dev.pointandshoot

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LensInfoSummaryTest {

    @Test
    fun `macro inference fires only when min focus is at or above the super-macro threshold`() {
        val superMacroUw = sample(minFocusDiopters = 25f) // Legacy device S5KJN5
        val borderlineMacro = sample(minFocusDiopters = 15f) // exactly at threshold
        val mainWideClose = sample(minFocusDiopters = 10f) // Legacy device LYT-808 (close, not super)
        val mainWide = sample(minFocusDiopters = 4f)
        val tele = sample(minFocusDiopters = 1.5f)
        val fixedFocus = sample(minFocusDiopters = 0f)

        assertTrue("UW @ 25 diopters (4 cm) - true super macro", superMacroUw.isMacroCapable)
        assertTrue("borderline @ 15 diopters (6.7 cm)", borderlineMacro.isMacroCapable)
        assertFalse("main wide @ 10 diopters (10 cm) - close focus, not super macro",
            mainWideClose.isMacroCapable)
        assertFalse("main wide @ 4 diopters (25 cm)", mainWide.isMacroCapable)
        assertFalse("tele @ 1.5 diopters (66 cm)", tele.isMacroCapable)
        assertFalse("fixed-focus camera", fixedFocus.isMacroCapable)
    }

    @Test
    fun `macro threshold matches the documented constant`() {
        assertEquals(15f, LensInfoSummary.MACRO_MIN_DIOPTERS_THRESHOLD, 0f)
    }

    @Test
    fun `OIS inference flags non-OFF modes`() {
        assertTrue(sample(oisModes = listOf("OFF", "ON")).hasOpticalStabilization)
        assertTrue(sample(oisModes = listOf("ON")).hasOpticalStabilization)
        assertFalse(sample(oisModes = listOf("OFF")).hasOpticalStabilization)
        assertFalse(sample(oisModes = emptyList()).hasOpticalStabilization)
        assertFalse(sample(oisModes = listOf("", " ")).hasOpticalStabilization)
    }

    @Test
    fun `minimumFocusDistanceMeters inverts diopters and is null when fixed-focus`() {
        assertEquals(0.10f, sample(minFocusDiopters = 10f).minimumFocusDistanceMeters!!, 1e-4f)
        assertEquals(0.25f, sample(minFocusDiopters = 4f).minimumFocusDistanceMeters!!, 1e-4f)
        assertNull(sample(minFocusDiopters = 0f).minimumFocusDistanceMeters)
    }

    @Test
    fun `describe contains the salient metadata`() {
        val s = sample(
            cameraId = "2",
            facing = "BACK",
            apertures = listOf(1.6f),
            oisModes = listOf("OFF", "ON"),
            minFocusDiopters = 10f,
            focalLengths = listOf(6.06f),
            sensorPhysicalSize = SensorPhysicalSize(7.6f, 5.7f),
            sensorOrientationDegrees = 90,
        )
        val text = s.describe()
        assertTrue(text, text.contains("cameraId=2"))
        assertTrue(text, text.contains("facing=BACK"))
        assertTrue(text, text.contains("f/1.60"))
        assertTrue(text, text.contains("6.06mm"))
        assertTrue(text, text.contains("0.100m"))
        assertTrue(text, text.contains("ois=OFF,ON"))
        assertTrue(text, text.contains("rot=90°"))
    }

    @Test
    fun `JSON round-trip preserves every field`() {
        val original = sample(
            cameraId = "0",
            facing = "BACK",
            apertures = listOf(1.6f, 2.8f),
            oisModes = listOf("OFF", "ON"),
            minFocusDiopters = 4.0f,
            hyperfocalDiopters = 0.5f,
            focalLengths = listOf(6.06f),
            sensorPhysicalSize = SensorPhysicalSize(7.6f, 5.7f),
            sensorActiveArray = SensorActiveArray(8160, 6144),
            sensorOrientationDegrees = 90,
        )
        val text = LensInfoSummaryJson.encode(original).toString()
        val decoded = LensInfoSummaryJson.decode(JSONObject(text))

        assertEquals(original, decoded)
    }

    @Test
    fun `JSON encoder emits the documented schema version key`() {
        val obj = LensInfoSummaryJson.encode(sample())
        assertEquals(LensInfoSummaryJson.SCHEMA_VERSION, obj.optInt("schemaVersion"))
    }

    @Test
    fun `decode rejects schema mismatch`() {
        val payload = JSONObject().apply {
            put("schemaVersion", 999)
            put("cameraId", "2")
        }
        try {
            LensInfoSummaryJson.decode(payload)
            fail("decode should reject schemaVersion=999")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message ?: "", (e.message ?: "").contains("schemaVersion=999"))
        }
    }

    @Test
    fun `decode rejects missing cameraId`() {
        val payload = JSONObject().apply {
            put("schemaVersion", LensInfoSummaryJson.SCHEMA_VERSION)
        }
        try {
            LensInfoSummaryJson.decode(payload)
            fail("decode should reject missing cameraId")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message ?: "", (e.message ?: "").contains("cameraId"))
        }
    }

    @Test
    fun `decode tolerates absent optional sections`() {
        val payload = JSONObject().apply {
            put("schemaVersion", LensInfoSummaryJson.SCHEMA_VERSION)
            put("cameraId", "2")
        }
        val decoded = LensInfoSummaryJson.decode(payload)

        assertEquals("2", decoded.cameraId)
        assertNull(decoded.lensFacing)
        assertTrue(decoded.availableApertures.isEmpty())
        assertTrue(decoded.opticalStabilizationModes.isEmpty())
        assertEquals(0f, decoded.minimumFocusDistanceDiopters, 0f)
        assertEquals(0f, decoded.hyperfocalDistanceDiopters, 0f)
        assertTrue(decoded.availableFocalLengthsMm.isEmpty())
        assertNull(decoded.sensorPhysicalSizeMm)
        assertNull(decoded.sensorActiveArrayPx)
        assertNull(decoded.sensorOrientationDegrees)
    }

    @Test
    fun `decode skips blank entries in the OIS modes array`() {
        val payload = JSONObject().apply {
            put("schemaVersion", LensInfoSummaryJson.SCHEMA_VERSION)
            put("cameraId", "2")
            put("opticalStabilizationModes", JSONArray().apply {
                put("OFF")
                put("")
                put("ON")
                put("   ")
            })
        }
        val decoded = LensInfoSummaryJson.decode(payload)
        assertEquals(listOf("OFF", "ON"), decoded.opticalStabilizationModes)
    }

    @Test
    fun `SensorPhysicalSize rejects non-positive dimensions`() {
        try {
            SensorPhysicalSize(0f, 5f); fail("zero width should throw")
        } catch (_: IllegalArgumentException) { /* expected */ }
        try {
            SensorPhysicalSize(5f, -1f); fail("negative height should throw")
        } catch (_: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun `SensorActiveArray rejects non-positive dimensions`() {
        try {
            SensorActiveArray(0, 100); fail("zero width should throw")
        } catch (_: IllegalArgumentException) { /* expected */ }
        try {
            SensorActiveArray(100, -10); fail("negative height should throw")
        } catch (_: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun `decoder reads sensor physical size when present`() {
        val original = sample(sensorPhysicalSize = SensorPhysicalSize(7.6f, 5.7f))
        val decoded = LensInfoSummaryJson.decode(LensInfoSummaryJson.encode(original))
        assertNotNull(decoded.sensorPhysicalSizeMm)
        assertEquals(7.6f, decoded.sensorPhysicalSizeMm!!.widthMm, 1e-4f)
        assertEquals(5.7f, decoded.sensorPhysicalSizeMm!!.heightMm, 1e-4f)
    }

    @Test
    fun `apertures and focal lengths preserve order across round-trip`() {
        val original = sample(
            apertures = listOf(1.6f, 2.0f, 2.8f, 4.0f),
            focalLengths = listOf(2.3f, 6.06f, 13.85f),
        )
        val decoded = LensInfoSummaryJson.decode(LensInfoSummaryJson.encode(original))
        assertEquals(original.availableApertures, decoded.availableApertures)
        assertEquals(original.availableFocalLengthsMm, decoded.availableFocalLengthsMm)
    }

    private fun sample(
        cameraId: String = "0",
        facing: String? = "BACK",
        apertures: List<Float> = emptyList(),
        oisModes: List<String> = emptyList(),
        minFocusDiopters: Float = 0f,
        hyperfocalDiopters: Float = 0f,
        focalLengths: List<Float> = emptyList(),
        sensorPhysicalSize: SensorPhysicalSize? = null,
        sensorActiveArray: SensorActiveArray? = null,
        sensorOrientationDegrees: Int? = null,
    ): LensInfoSummary = LensInfoSummary(
        cameraId = cameraId,
        lensFacing = facing,
        availableApertures = apertures,
        opticalStabilizationModes = oisModes,
        minimumFocusDistanceDiopters = minFocusDiopters,
        hyperfocalDistanceDiopters = hyperfocalDiopters,
        availableFocalLengthsMm = focalLengths,
        sensorPhysicalSizeMm = sensorPhysicalSize,
        sensorActiveArrayPx = sensorActiveArray,
        sensorOrientationDegrees = sensorOrientationDegrees,
    )
}
