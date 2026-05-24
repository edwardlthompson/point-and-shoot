package dev.pointandshoot

import android.media.AudioFormat
import org.junit.Assert.assertTrue
import org.junit.Test

class PnsAudioCaptureSupportTest {
    @Test
    fun diagSummary_includesHiFiAndWindFlags() {
        val profile =
            PnsAudioCaptureProfile(
                sampleRateHz = 96_000,
                aacBitrateBps = 256_000,
                channelCount = 2,
                channelConfig = AudioFormat.CHANNEL_IN_STEREO,
                pcmEncoding = AudioFormat.ENCODING_PCM_16BIT,
                windNoiseSuppression = true,
                preferExternalInput = true,
                hiFiMode = true,
            )
        val summary = PnsAudioCaptureSupport.diagSummary(profile)
        assertTrue(summary.contains("sampleRate=96000"))
        assertTrue(summary.contains("hiFi=true"))
        assertTrue(summary.contains("windNs=true"))
        assertTrue(summary.contains("pcm=int16"))
    }

    @Test
    fun targetMuxSampleRates_hiFiPrefers96k() {
        val hiFi =
            PnsAudioCaptureProfile(
                sampleRateHz = 96_000,
                aacBitrateBps = 256_000,
                channelCount = 2,
                channelConfig = AudioFormat.CHANNEL_IN_STEREO,
                pcmEncoding = AudioFormat.ENCODING_PCM_16BIT,
                windNoiseSuppression = false,
                preferExternalInput = false,
                hiFiMode = true,
            )
        assertTrue(PnsAacEncoderSupport.targetMuxSampleRates(hiFi).contentEquals(intArrayOf(96_000, 48_000, 44_100)))
        val std = hiFi.copy(hiFiMode = false)
        assertTrue(PnsAacEncoderSupport.targetMuxSampleRates(std).contentEquals(intArrayOf(48_000, 44_100)))
    }
}
