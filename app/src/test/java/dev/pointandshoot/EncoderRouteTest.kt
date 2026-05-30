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
    fun `decide independent raw and tonal bundles when native is available - StandardPro`() {
        val plan =
            ComposedStillIntent(
                raw = ImgMenuTier.Standard,
                jpeg = ImgMenuTier.Ultra,
                hdrWhenJpegOff = ImgMenuTier.Standard,
            ).resolveCapturePlan()
        val rawDecision = EncoderRoute.decide(plan.raw!!, nativeAvailable = true)
        val tonalDecision = EncoderRoute.decide(plan.tonal!!, nativeAvailable = true)
        assertEquals(RawMode.LosslessCompressedDng, rawDecision.rawWritten)
        assertNull(rawDecision.tonalWritten)
        assertEquals(TonalContainer.JpegXl12Bit, tonalDecision.tonalWritten)
        assertEquals(1, rawDecision.fileCountForCapture)
        assertEquals(1, tonalDecision.fileCountForCapture)
    }

    @Test
    fun `decide independent raw and tonal bundles when native is available - UltraMax`() {
        val plan =
            ComposedStillIntent(
                raw = ImgMenuTier.Ultra,
                jpeg = ImgMenuTier.Standard,
                hdrWhenJpegOff = ImgMenuTier.Ultra,
            ).resolveCapturePlan()
        val rawDecision = EncoderRoute.decide(plan.raw!!, nativeAvailable = true)
        val tonalDecision = EncoderRoute.decide(plan.tonal!!, nativeAvailable = true)
        assertEquals(RawMode.UncompressedRaw12Dng, rawDecision.rawWritten)
        assertEquals(TonalContainer.Avif10BitHdr, tonalDecision.tonalWritten)
    }

    @Test
    fun `decide JpegOnly tonal bundle is single file regardless of native encoders`() {
        val bundle =
            ComposedStillIntent(
                raw = ImgMenuTier.Off,
                jpeg = ImgMenuTier.Standard,
                hdrWhenJpegOff = ImgMenuTier.Standard,
            ).resolveCapturePlan().tonal!!
        val on = EncoderRoute.decide(bundle, nativeAvailable = true)
        val off = EncoderRoute.decide(bundle, nativeAvailable = false)
        assertEquals(RawMode.None, on.rawWritten)
        assertEquals(TonalContainer.Avif10BitHdr, on.tonalWritten)
        assertTrue(off.fallbackJpeg)
        assertEquals(1, on.fileCountForCapture)
        assertEquals(1, off.fileCountForCapture)
    }

    @Test
    fun `decide tonal downgrades to JPEG when native is absent`() {
        val plan =
            ComposedStillIntent(
                raw = ImgMenuTier.Standard,
                jpeg = ImgMenuTier.Ultra,
                hdrWhenJpegOff = ImgMenuTier.Standard,
            ).resolveCapturePlan()
        val rawDecision = EncoderRoute.decide(plan.raw!!, nativeAvailable = false)
        val tonalDecision = EncoderRoute.decide(plan.tonal!!, nativeAvailable = false)
        assertEquals(RawMode.LosslessCompressedDng, rawDecision.rawWritten)
        assertNull(rawDecision.tonalWritten)
        assertTrue(tonalDecision.fallbackJpeg)
        assertEquals(EncoderRoute.DOWNGRADE_MESSAGE, tonalDecision.downgradeReason)
    }

    @Test
    fun `decide preserves rawMode on raw bundle regardless of native state`() {
        val plan =
            ComposedStillIntent(
                raw = ImgMenuTier.Ultra,
                jpeg = ImgMenuTier.Off,
                hdrWhenJpegOff = ImgMenuTier.Ultra,
            ).resolveCapturePlan()
        val on = EncoderRoute.decide(plan.raw!!, true).rawWritten
        val off = EncoderRoute.decide(plan.raw!!, false).rawWritten
        assertEquals(on, off)
        assertEquals(RawMode.UncompressedRaw12Dng, on)
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
    fun `decide raw bundle writes DNG only tonal is separate capture`() {
        val bundle =
            StillCaptureBundle(
                rawMode = RawMode.LosslessCompressedDng,
                tonalContainer = TonalContainer.JpegXl12Bit,
                colorSpace = ColorSpaceTarget.Rec2020,
                dngColorSpace = ColorSpaceTarget.DisplayP3,
            )
        val decision = EncoderRoute.decide(bundle, nativeAvailable = true)
        assertEquals(ImagingProfile.StandardPro, decision.profile)
        assertEquals(RawMode.LosslessCompressedDng, decision.rawWritten)
        assertEquals(null, decision.tonalWritten)
        assertEquals(1, decision.fileCountForCapture)
    }

    @Test
    fun `DOWNGRADE_MESSAGE is a single non-blank user-facing line`() {
        val msg = EncoderRoute.DOWNGRADE_MESSAGE
        assertNotNull(msg)
        assertTrue(msg.isNotBlank())
        assertFalse("downgrade message must fit on one line", msg.contains('\n'))
    }
}
