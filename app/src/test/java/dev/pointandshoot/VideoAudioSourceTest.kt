package dev.pointandshoot

import android.media.MediaRecorder
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoAudioSourceTest {
    @Test
    fun fromStorage_defaultsToCamcorder() {
        assertEquals(VideoAudioSource.Camcorder, VideoAudioSource.fromStorage(null))
        assertEquals(VideoAudioSource.Camcorder, VideoAudioSource.fromStorage("unknown"))
    }

    @Test
    fun logTag_matchesMediaRecorderConstantNames() {
        assertEquals("CAMCORDER", VideoAudioSource.Camcorder.logTag())
        assertEquals("MIC", VideoAudioSource.Mic.logTag())
        assertEquals(
            MediaRecorder.AudioSource.CAMCORDER,
            VideoAudioSource.Camcorder.toAudioRecordSource(),
        )
    }
}
