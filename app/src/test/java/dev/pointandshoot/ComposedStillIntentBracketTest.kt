package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Independent RAW vs tonal capture plan (no companion surfaces). */
class ComposedStillIntentBracketTest {

    @Test
    fun rawPlusJpeg_matchingTier_usesSidecarPlan() {
        val intent =
            ComposedStillIntent(
                raw = ImgMenuTier.Standard,
                jpeg = ImgMenuTier.Standard,
                hdrWhenJpegOff = ImgMenuTier.Standard,
            )
        assertTrue(intent.wantsMatchedTierJpegSidecar())
        val plan = intent.resolveCapturePlan()
        assertEquals(RawMode.LosslessCompressedDng, plan.raw!!.rawMode)
        assertEquals(null, plan.tonal)
        assertEquals(92, plan.jpegSidecarPreset!!.softwareJpegCompanionQuality)
    }

    @Test
    fun rawPlusJpeg_mixedTier_keepsIndependentTonal() {
        val intent =
            ComposedStillIntent(
                raw = ImgMenuTier.Standard,
                jpeg = ImgMenuTier.Ultra,
                hdrWhenJpegOff = ImgMenuTier.Standard,
            )
        assertFalse(intent.wantsMatchedTierJpegSidecar())
        val plan = intent.resolveCapturePlan()
        assertEquals(RawMode.LosslessCompressedDng, plan.raw!!.rawMode)
        assertEquals(TonalContainer.JpegXl12Bit, plan.tonal!!.tonalContainer)
        assertEquals(null, plan.jpegSidecarPreset)
    }

    @Test
    fun rawOnly_jpegOff_noTonalPlan() {
        val intent =
            ComposedStillIntent(
                raw = ImgMenuTier.Ultra,
                jpeg = ImgMenuTier.Off,
                hdrWhenJpegOff = ImgMenuTier.Ultra,
            )
        val plan = intent.resolveCapturePlan()
        assertEquals(RawMode.UncompressedRaw12Dng, plan.raw!!.rawMode)
        assertNull(plan.tonal)
        assertFalse(intent.wantsTonalStill())
    }

    @Test
    fun jpegOnly_primary_hasNoDngKind() {
        val intent =
            ComposedStillIntent(
                raw = ImgMenuTier.Off,
                jpeg = ImgMenuTier.Standard,
                hdrWhenJpegOff = ImgMenuTier.Standard,
            )
        val plan = intent.resolveCapturePlan()
        assertNull(plan.raw)
        assertEquals(RawMode.None, plan.tonal!!.rawMode)
        assertEquals(ImagingProfile.JpegOnly, intent.storageProfile())
        val decision = EncoderRoute.decide(plan.tonal!!, nativeAvailable = true)
        assertEquals(1, decision.fileCountForCapture)
        assertFalse(decision.fallbackJpeg)
    }

    @Test
    fun ultraRawUltraJpeg_sidecarNotDualTonal() {
        val intent =
            ComposedStillIntent(
                raw = ImgMenuTier.Ultra,
                jpeg = ImgMenuTier.Ultra,
                hdrWhenJpegOff = ImgMenuTier.Ultra,
            )
        val plan = intent.resolveCapturePlan()
        assertEquals(RawMode.UncompressedRaw12Dng, plan.raw!!.rawMode)
        assertEquals(null, plan.tonal)
        assertEquals(100, plan.jpegSidecarPreset!!.softwareJpegCompanionQuality)
        assertTrue(intent.wantsRawDng())
        assertTrue(intent.wantsTonalStill())
    }

    @Test
    fun jpegEncodePresets_matchMenuHints() {
        val ultra = ImgMenuJpegEncodePresets.forTier(ImgMenuTier.Ultra)
        assertEquals(2, ultra.hardwareJpegIspBias)
        assertEquals(100, ultra.softwareJpegCompanionQuality)
        val std = ImgMenuJpegEncodePresets.forTier(ImgMenuTier.Standard)
        assertEquals(0, std.hardwareJpegIspBias)
        assertEquals(92, std.softwareJpegCompanionQuality)
    }

    @Test
    fun rawWithJpeg_usesJpegTierForEncodePreset() {
        val intent =
            ComposedStillIntent(
                raw = ImgMenuTier.Standard,
                jpeg = ImgMenuTier.Ultra,
                hdrWhenJpegOff = ImgMenuTier.Standard,
            )
        assertEquals(ImgMenuTier.Ultra, intent.jpegHardwareEncodeTier())
        assertEquals(100, intent.jpegEncodePreset()!!.softwareJpegCompanionQuality)
    }

    @Test
    fun rawWithJpegOff_hasNoJpegEncodePreset() {
        val intent =
            ComposedStillIntent(
                raw = ImgMenuTier.Standard,
                jpeg = ImgMenuTier.Off,
                hdrWhenJpegOff = ImgMenuTier.Standard,
            )
        assertEquals(null, intent.jpegEncodePreset())
    }

    @Test
    fun coerceForStillColorSpace_rec2020_forcesUltraMatrix() {
        val intent =
            ComposedStillIntent(
                raw = ImgMenuTier.Standard,
                jpeg = ImgMenuTier.Standard,
                hdrWhenJpegOff = ImgMenuTier.Standard,
            )
        val coerced = intent.coerceForStillColorSpace(ColorSpaceTarget.Rec2020)
        assertEquals(ImgMenuTier.Ultra, coerced.raw)
        assertEquals(ImgMenuTier.Ultra, coerced.jpeg)
        assertEquals(ImgMenuTier.Ultra, coerced.hdrWhenJpegOff)
    }

    @Test
    fun maxPhotoIntent_selectsHighestPresets() {
        val max = StillPhotoPickerMatrix.maxPhotoIntent()
        assertEquals(ImgMenuTier.Ultra, max.raw)
        assertEquals(ImgMenuTier.Ultra, max.jpeg)
        assertEquals(ImgMenuTier.Ultra, max.hdrWhenJpegOff)
    }
}
