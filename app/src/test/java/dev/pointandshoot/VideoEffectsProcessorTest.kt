package dev.pointandshoot

import android.media.MediaFormat
import android.util.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoEffectsProcessorTest {
    @Test
    fun videoFormat_av1_requiresMediaCodecPath() {
        val fmt =
            VideoFormat(
                codec = VideoCodec.AV1,
                resolution = Size(1920, 1080),
                frameRate = 60,
                bitrate = 12_000_000,
            )
        assertTrue(fmt.requiresMediaCodec)
    }

    @Test
    fun mcVideoRecorder_av1Config_usesAv1Mime() {
        assertEquals(
            MediaFormat.MIMETYPE_VIDEO_AV1,
            MediaCodecVideoRecorder.videoMimeForConfig(
                MediaCodecVideoRecorder.Config(
                    width = 1920,
                    height = 1080,
                    fps = 60,
                    bitrate = 12_000_000,
                    encoderKind = MediaCodecVideoRecorder.VideoEncoderKind.AV1,
                ),
            ),
        )
    }

    @Test
    fun readoutLabel_oisAndEis() {
        assertEquals(
            "OIS+EIS",
            VideoEffectsProcessor.readoutLabel(
                VideoEffectsProcessor.StabilizationState(
                    oisOn = true,
                    eisOn = true,
                    oisAdvertised = true,
                    eisAdvertised = true,
                ),
            ),
        )
    }

    @Test
    fun readoutLabel_oisOnly() {
        assertEquals(
            "OIS",
            VideoEffectsProcessor.readoutLabel(
                VideoEffectsProcessor.StabilizationState(
                    oisOn = true,
                    eisOn = false,
                    oisAdvertised = true,
                    eisAdvertised = true,
                ),
            ),
        )
    }

    @Test
    fun readoutLabel_oisOnly_offOnlyCharacteristics() {
        assertEquals(
            "OIS",
            VideoEffectsProcessor.readoutLabel(
                VideoEffectsProcessor.StabilizationState(
                    oisOn = true,
                    eisOn = false,
                    oisAdvertised = true,
                    eisAdvertised = true,
                ),
            ),
        )
    }

    @Test
    fun readoutLabel_eisBlockedByManualSensor() {
        assertEquals(
            "OIS",
            VideoEffectsProcessor.readoutLabel(
                VideoEffectsProcessor.StabilizationState(
                    oisOn = true,
                    eisOn = false,
                    oisAdvertised = true,
                    eisAdvertised = true,
                ),
            ),
        )
    }

    @Test
    fun readoutLabel_advertisedButOff() {
        assertEquals(
            "Off",
            VideoEffectsProcessor.readoutLabel(
                VideoEffectsProcessor.StabilizationState(
                    oisOn = false,
                    eisOn = false,
                    oisAdvertised = true,
                    eisAdvertised = false,
                ),
            ),
        )
    }

    @Test
    fun readoutLabel_noneHidden() {
        assertEquals(
            null,
            VideoEffectsProcessor.readoutLabel(
                VideoEffectsProcessor.StabilizationState(
                    oisOn = false,
                    eisOn = false,
                    oisAdvertised = false,
                    eisAdvertised = false,
                ),
            ),
        )
    }
}
