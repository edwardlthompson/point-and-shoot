package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-data tests for [EncoderRoute]. Locks the fallback contract from
 * `BUILD_PLAN.md` §4 / `FAILURE_MATRIX.md` "Native encoders unavailable":
 *
 * - DNG always survives (RAW path bypasses the NDK).
 * - Tonal containers (AVIF / JXL) downgrade to JPEG when native is absent.
 * - The downgrade reason is the canonical user-facing message so the HUD
 *   and the diagnostics screen agree.
 */
class EncoderRouteTest {

    @Test
    fun `decide writes both files when native is available - StandardPro`() {
        val decision = EncoderRoute.decide(ImagingProfile.StandardPro, nativeAvailable = true)
        assertEquals(ImagingProfile.StandardPro, decision.profile)
        assertEquals(RawMode.LosslessCompressedDng, decision.rawWritten)
        assertEquals(TonalContainer.Avif10BitHdr, decision.tonalWritten)
        assertFalse(decision.fallbackJpeg)
        assertNull(decision.downgradeReason)
        assertEquals(2, decision.fileCountForCapture)
    }

    @Test
    fun `decide writes both files when native is available - UltraMax`() {
        val decision = EncoderRoute.decide(ImagingProfile.UltraMax, nativeAvailable = true)
        assertEquals(ImagingProfile.UltraMax, decision.profile)
        assertEquals(RawMode.UncompressedRaw12Dng, decision.rawWritten)
        assertEquals(TonalContainer.JpegXl12Bit, decision.tonalWritten)
        assertFalse(decision.fallbackJpeg)
        assertNull(decision.downgradeReason)
        assertEquals(2, decision.fileCountForCapture)
    }

    @Test
    fun `decide JpegOnly is single hardware JPEG file regardless of native encoders`() {
        val on = EncoderRoute.decide(ImagingProfile.JpegOnly, nativeAvailable = true)
        val off = EncoderRoute.decide(ImagingProfile.JpegOnly, nativeAvailable = false)
        assertEquals(RawMode.None, on.rawWritten)
        assertEquals(RawMode.None, off.rawWritten)
        assertNull(on.tonalWritten)
        assertNull(off.tonalWritten)
        assertFalse(on.fallbackJpeg)
        assertFalse(off.fallbackJpeg)
        assertEquals(1, on.fileCountForCapture)
        assertEquals(1, off.fileCountForCapture)
    }
    @Test
    fun `decide downgrades to JPEG when native is absent - StandardPro`() {
        val decision = EncoderRoute.decide(ImagingProfile.StandardPro, nativeAvailable = false)
        assertEquals(ImagingProfile.StandardPro, decision.profile)
        assertEquals(RawMode.LosslessCompressedDng, decision.rawWritten)
        assertNull("Tonal container substituted by JPEG fallback", decision.tonalWritten)
        assertTrue(decision.fallbackJpeg)
        assertEquals(EncoderRoute.DOWNGRADE_MESSAGE, decision.downgradeReason)
        assertEquals(2, decision.fileCountForCapture)
    }

    @Test
    fun `decide downgrades to JPEG when native is absent - UltraMax`() {
        val decision = EncoderRoute.decide(ImagingProfile.UltraMax, nativeAvailable = false)
        assertEquals(ImagingProfile.UltraMax, decision.profile)
        assertEquals(RawMode.UncompressedRaw12Dng, decision.rawWritten)
        assertNull(decision.tonalWritten)
        assertTrue(decision.fallbackJpeg)
        assertEquals(EncoderRoute.DOWNGRADE_MESSAGE, decision.downgradeReason)
    }

    @Test
    fun `decide preserves the rawMode for both profiles regardless of native state`() {
        val onWithStdPro = EncoderRoute.decide(ImagingProfile.StandardPro, true).rawWritten
        val offWithStdPro = EncoderRoute.decide(ImagingProfile.StandardPro, false).rawWritten
        assertEquals(onWithStdPro, offWithStdPro)
        assertEquals(RawMode.LosslessCompressedDng, onWithStdPro)

        val onWithUltra = EncoderRoute.decide(ImagingProfile.UltraMax, true).rawWritten
        val offWithUltra = EncoderRoute.decide(ImagingProfile.UltraMax, false).rawWritten
        assertEquals(onWithUltra, offWithUltra)
        assertEquals(RawMode.UncompressedRaw12Dng, onWithUltra)
    }

    @Test
    fun `downgradedProfiles is empty when native is available`() {
        assertTrue(EncoderRoute.downgradedProfiles(nativeAvailable = true).isEmpty())
    }

    @Test
    fun `downgradedProfiles enumerates every native-dependent profile when native is absent`() {
        val downgraded = EncoderRoute.downgradedProfiles(nativeAvailable = false)
        assertEquals(2, downgraded.size)
        assertTrue(downgraded.contains(ImagingProfile.StandardPro))
        assertTrue(downgraded.contains(ImagingProfile.UltraMax))
    }

    @Test
    fun `every HDR tonal container requires the native encoder`() {
        assertTrue(TonalContainer.Avif10BitHdr.requiresNativeEncoder)
        assertTrue(TonalContainer.JpegXl12Bit.requiresNativeEncoder)
        assertFalse(TonalContainer.JpegSdr8.requiresNativeEncoder)
    }

    @Test
    fun `DOWNGRADE_MESSAGE is a single non-blank user-facing line`() {
        val msg = EncoderRoute.DOWNGRADE_MESSAGE
        assertNotNull(msg)
        assertTrue(msg.isNotBlank())
        assertFalse("downgrade message must fit on one line", msg.contains('\n'))
    }
}
