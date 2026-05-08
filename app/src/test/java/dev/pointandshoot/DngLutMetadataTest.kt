package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DngLutMetadataTest {

    private val sampleSha = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
    private val otherSha = "1111111122222222333333334444444455555555666666667777777788888888"

    @Test
    fun `software tag without LUT just shows app + version`() {
        val out = DngLutMetadata.formatSoftwareTag(appVersion = "0.1.0")
        assertEquals("Point & Shoot v0.1.0", out)
    }

    @Test
    fun `software tag with bundled LUT appends marker`() {
        val lut = DngLutMetadata.LutIdentity.Bundled(
            catalogId = "PnsCinematic",
            sha256 = sampleSha,
        )
        val out = DngLutMetadata.formatSoftwareTag(appVersion = "0.1.0", activeLut = lut)
        assertEquals(
            "Point & Shoot v0.1.0 / LUT=PnsCinematic SHA256=$sampleSha",
            out,
        )
    }

    @Test
    fun `software tag with cube file LUT appends marker`() {
        val lut = DngLutMetadata.LutIdentity.CubeFile(
            safeFilename = "user_glow.cube",
            sha256 = sampleSha,
        )
        val out = DngLutMetadata.formatSoftwareTag(appVersion = "0.2.0-rc1", activeLut = lut)
        assertEquals(
            "Point & Shoot v0.2.0-rc1 / LUT=user_glow.cube SHA256=$sampleSha",
            out,
        )
    }

    @Test
    fun `formatLutMarker is regex-friendly`() {
        val lut = DngLutMetadata.LutIdentity.Bundled(
            catalogId = "PnsLog2Linear",
            sha256 = sampleSha,
        )
        val marker = DngLutMetadata.formatLutMarker(lut)
        assertEquals("LUT=PnsLog2Linear SHA256=$sampleSha", marker)
        assertTrue(DngLutMetadata.MARKER_REGEX.containsMatchIn(marker))
    }

    @Test
    fun `parseLutMarker round-trips bundled identity`() {
        val lut = DngLutMetadata.LutIdentity.Bundled("PnsCinematic", sampleSha)
        val tag = DngLutMetadata.formatSoftwareTag("0.1.0", lut)
        val parsed = DngLutMetadata.parseLutMarker(tag)
        assertNotNull(parsed)
        assertEquals("PnsCinematic", parsed!!.markerName)
        assertEquals(sampleSha, parsed.sha256)
    }

    @Test
    fun `parseLutMarker round-trips cube file identity`() {
        val lut = DngLutMetadata.LutIdentity.CubeFile("user_glow.cube", otherSha)
        val tag = DngLutMetadata.formatSoftwareTag("9.9.9", lut)
        val parsed = DngLutMetadata.parseLutMarker(tag)
        assertNotNull(parsed)
        assertEquals("user_glow.cube", parsed!!.markerName)
        assertEquals(otherSha, parsed.sha256)
    }

    @Test
    fun `parseLutMarker returns null when marker absent`() {
        val out = DngLutMetadata.parseLutMarker("Some Other Camera App v3.4.5")
        assertNull(out)
    }

    @Test
    fun `parseLutMarker returns null on malformed sha`() {
        val out = DngLutMetadata.parseLutMarker("Point & Shoot v0.1.0 / LUT=PnsCinematic SHA256=tooshort")
        assertNull(out)
    }

    @Test
    fun `parseLutMarker tolerates leading and trailing tool annotations`() {
        val noisy = "Edited in DesktopTool 2.0; Originally: Point & Shoot v0.1.0 / LUT=PnsCinematic SHA256=$sampleSha (modified)"
        val parsed = DngLutMetadata.parseLutMarker(noisy)
        assertNotNull(parsed)
        assertEquals("PnsCinematic", parsed!!.markerName)
        assertEquals(sampleSha, parsed.sha256)
    }

    @Test
    fun `unique camera model defaults to device + cameraId only`() {
        val out = DngLutMetadata.formatUniqueCameraModel(
            deviceModel = "OnePlus 13",
            cameraId = "0",
        )
        assertEquals("OnePlus 13 (cameraId=0)", out)
    }

    @Test
    fun `unique camera model leaves LUT marker out by default even with active LUT`() {
        val lut = DngLutMetadata.LutIdentity.Bundled("PnsCinematic", sampleSha)
        val out = DngLutMetadata.formatUniqueCameraModel(
            deviceModel = "OnePlus 13",
            cameraId = "0",
            activeLut = lut,
        )
        assertEquals("OnePlus 13 (cameraId=0)", out)
    }

    @Test
    fun `unique camera model includes LUT marker when explicitly opted in`() {
        val lut = DngLutMetadata.LutIdentity.Bundled("PnsCinematic", sampleSha)
        val out = DngLutMetadata.formatUniqueCameraModel(
            deviceModel = "OnePlus 13",
            cameraId = "0",
            activeLut = lut,
            includeLutMarkerInUniqueCameraModel = true,
        )
        assertEquals("OnePlus 13 (cameraId=0) / LUT=PnsCinematic SHA256=$sampleSha", out)
    }

    @Test
    fun `bundled identity rejects whitespace catalog id`() {
        assertThrows(IllegalArgumentException::class.java) {
            DngLutMetadata.LutIdentity.Bundled("Pns Cinematic", sampleSha)
        }
    }

    @Test
    fun `bundled identity rejects malformed sha`() {
        assertThrows(IllegalArgumentException::class.java) {
            DngLutMetadata.LutIdentity.Bundled("PnsCinematic", "not-a-sha")
        }
    }

    @Test
    fun `cube file identity rejects whitespace filename`() {
        assertThrows(IllegalArgumentException::class.java) {
            DngLutMetadata.LutIdentity.CubeFile("user glow.cube", sampleSha)
        }
    }

    @Test
    fun `software tag rejects blank app version`() {
        assertThrows(IllegalArgumentException::class.java) {
            DngLutMetadata.formatSoftwareTag(appVersion = "  ")
        }
    }

    @Test
    fun `unique camera model rejects blank device model`() {
        assertThrows(IllegalArgumentException::class.java) {
            DngLutMetadata.formatUniqueCameraModel(deviceModel = "", cameraId = "0")
        }
    }

    @Test
    fun `unique camera model rejects blank cameraId`() {
        assertThrows(IllegalArgumentException::class.java) {
            DngLutMetadata.formatUniqueCameraModel(deviceModel = "OnePlus 13", cameraId = "")
        }
    }

    @Test
    fun `schema version is pinned`() {
        assertEquals(1, DngLutMetadata.SCHEMA_VERSION)
    }

    @Test
    fun `app name is pinned`() {
        assertEquals("Point & Shoot", DngLutMetadata.APP_NAME)
    }
}
