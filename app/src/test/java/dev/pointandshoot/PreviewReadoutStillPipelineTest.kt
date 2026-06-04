package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewReadoutStillPipelineTest {

    @Test
    fun chipLabel_jpegOnly() {
        assertEquals(
            "AVIF",
            PreviewReadoutStillPipeline.chipLabel(
                ImagingProfile.JpegOnly,
                stillCaptureJpegCompanion = true,
                sessionJpegCompanionReady = true,
            ),
        )
    }

    @Test
    fun chipLabel_standardPro_dngPlusWhenCompanionActive() {
        assertEquals(
            "DNG+AVIF",
            PreviewReadoutStillPipeline.chipLabel(
                ImagingProfile.StandardPro,
                stillCaptureJpegCompanion = true,
                sessionJpegCompanionReady = true,
            ),
        )
    }

    @Test
    fun chipLabel_standardPro_dngWhenNoSessionCompanion() {
        assertEquals(
            "DNG",
            PreviewReadoutStillPipeline.chipLabel(
                ImagingProfile.StandardPro,
                stillCaptureJpegCompanion = true,
                sessionJpegCompanionReady = false,
            ),
        )
    }

    @Test
    fun chipLabel_ultraMax_dng12() {
        assertEquals(
            "DNG12",
            PreviewReadoutStillPipeline.chipLabel(
                ImagingProfile.UltraMax,
                stillCaptureJpegCompanion = false,
                sessionJpegCompanionReady = false,
            ),
        )
    }

    @Test
    fun chipLabel_ultraMax_dng12PlusJxlWhenBoth() {
        val intent =
            ComposedStillIntent(
                raw = ImgMenuTier.Ultra,
                jpeg = ImgMenuTier.Ultra,
                hdrWhenJpegOff = ImgMenuTier.Ultra,
            )
        assertEquals(
            "DNG12+JPEG",
            PreviewReadoutStillPipeline.chipLabel(
                intent,
                stillCaptureJpegCompanion = true,
                sessionJpegCompanionReady = true,
            ),
        )
    }

    @Test
    fun chromeUxLogValue_matchesChip() {
        val p = ImagingProfile.StandardPro
        assertEquals(
            PreviewReadoutStillPipeline.chipLabel(p, true, true),
            PreviewReadoutStillPipeline.chromeUxLogValue(p, true, true),
        )
    }
}
