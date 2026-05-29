package dev.pointandshoot

import android.media.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaCodecVideoRecorderSpatialAudioTest {
    @Test
    fun spatialAudioMetaLogLabel_stereo() {
        assertEquals(
            "stereo",
            MediaCodecVideoRecorder.spatialAudioMetaLogLabel(AudioFormat.CHANNEL_IN_STEREO),
        )
    }

    @Test
    fun spatialAudioMetaLogLabel_mono() {
        assertEquals(
            "mono",
            MediaCodecVideoRecorder.spatialAudioMetaLogLabel(AudioFormat.CHANNEL_IN_MONO),
        )
    }
}
