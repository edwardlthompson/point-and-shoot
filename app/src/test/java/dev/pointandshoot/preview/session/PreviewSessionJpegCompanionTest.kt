package dev.pointandshoot.preview.session

import android.util.Size
import dev.pointandshoot.ImagingProfile
import dev.pointandshoot.PhotoResolutionMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewSessionJpegCompanionTest {

    @Test
    fun shouldAttach_jpegOnlySession() {
        assertTrue(
            PreviewSessionJpegCompanion.shouldAttachJpegSurface(
                jpegOnlySession = true,
                wantsIndependentTonalStill = false,
                wantsJpegSidecarOnRaw = false,
                jpegSize = null,
            ),
        )
    }

    @Test
    fun shouldAttach_sidecarWhenSizePresent() {
        assertTrue(
            PreviewSessionJpegCompanion.shouldAttachJpegSurface(
                jpegOnlySession = false,
                wantsIndependentTonalStill = false,
                wantsJpegSidecarOnRaw = true,
                jpegSize = Size(4032, 3024),
            ),
        )
    }

    @Test
    fun shouldAttach_rawJpegAnchorWhenSizePresent() {
        assertTrue(
            PreviewSessionJpegCompanion.shouldAttachJpegSurface(
                jpegOnlySession = false,
                wantsIndependentTonalStill = false,
                wantsJpegSidecarOnRaw = false,
                jpegSize = Size(4032, 3024),
                wantsRawStillJpegAnchor = true,
            ),
        )
    }

    @Test
    fun shouldAttach_falseWhenNoTiersNoAnchorAndNoSize() {
        assertFalse(
            PreviewSessionJpegCompanion.shouldAttachJpegSurface(
                jpegOnlySession = false,
                wantsIndependentTonalStill = false,
                wantsJpegSidecarOnRaw = false,
                jpegSize = null,
                wantsRawStillJpegAnchor = false,
            ),
        )
    }

    @Test
    fun configure_skipsTonalOff() {
        val outcome =
            PreviewSessionJpegCompanion.configure(
                input =
                    PreviewSessionJpegCompanion.Input(
                        characteristics = null,
                        streamConfigurationMap = null,
                        imagingProfileForStreams = ImagingProfile.StandardPro,
                        stillPhotoResolutionMode = PhotoResolutionMode.Binned,
                        wantsIndependentTonalStill = false,
                        wantsJpegSidecarOnRaw = false,
                    ),
                surfaces = mutableListOf(),
                extraOutputConfigs = mutableListOf(),
                onDebugLog = {},
                onPipelineEvent = { _, _, _ -> },
            )
        assertTrue(outcome is PreviewSessionJpegCompanion.Outcome.Skipped)
        assertTrue(
            (outcome as PreviewSessionJpegCompanion.Outcome.Skipped).reason is
                PreviewSessionJpegCompanion.SkipReason.TonalOff,
        )
    }
}
