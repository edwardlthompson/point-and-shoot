package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoCaptureMetadataTest {
    @Test
    fun parseDescription_extractsFpsAndCodec() {
        val parsed =
            VideoCaptureMetadata.parseDescription(
                "Point & Shoot · H.264 · 120fps",
            )
        assertEquals(120, parsed.captureFps)
        assertEquals("H.264", parsed.codecLabel)
    }

    @Test
    fun parseDescription_extractsAudioFields() {
        val parsed =
            VideoCaptureMetadata.parseDescription(
                "Point & Shoot · H.265 · 120fps · AAC 48kHz 256k 2ch Hi-Fi wind NS",
            )
        assertEquals(48_000, parsed.audioSampleRateHz)
        assertEquals(256_000, parsed.audioAacBitrateBps)
        assertEquals(2, parsed.audioChannelCount)
        assertEquals(true, parsed.audioHiFi)
        assertEquals(true, parsed.audioWindNoiseReduction)
    }

    @Test
    fun mergeFrameRateForDisplay_prefersMeasuredOverEmbeddedTarget() {
        val merged =
            VideoCaptureMetadata.mergeFrameRateForDisplay(
                embeddedCaptureFps = 60,
                retrieverFpsRaw = "30",
            )
        assertEquals("30", merged)
    }

    @Test
    fun mergeFrameRateForDisplay_usesEmbeddedWhenNoMeasured() {
        val merged =
            VideoCaptureMetadata.mergeFrameRateForDisplay(
                embeddedCaptureFps = 120,
                retrieverFpsRaw = null,
            )
        assertEquals("120", merged)
    }

    @Test
    fun mergeFrameRateForDisplay_usesRetrieverWhenNoEmbedded() {
        val merged =
            VideoCaptureMetadata.mergeFrameRateForDisplay(
                embeddedCaptureFps = null,
                retrieverFpsRaw = "59.94",
            )
        assertEquals("59.9", merged)
    }
}
