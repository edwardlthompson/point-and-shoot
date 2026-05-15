package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewReadoutStillPipelineTest {

    @Test
    fun chipLabel_jpegOnly() {
        assertEquals(
            "JPG",
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
            "DNG+",
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
    fun chipLabel_ultraMax_dng12PlusWhenBoth() {
        assertEquals(
            "DNG12+",
            PreviewReadoutStillPipeline.chipLabel(
                ImagingProfile.UltraMax,
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
