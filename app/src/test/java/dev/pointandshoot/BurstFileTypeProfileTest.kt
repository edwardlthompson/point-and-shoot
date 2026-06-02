package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class BurstFileTypeProfileTest {
    @Test
    fun normalizeBurstFileTypeProfile_restrictsToRawOrJpeg() {
        assertEquals(
            BurstPhotoQualityProfile.ProcessedOnly,
            normalizeBurstFileTypeProfile(BurstPhotoQualityProfile.Auto),
        )
        assertEquals(
            BurstPhotoQualityProfile.ProcessedOnly,
            normalizeBurstFileTypeProfile(BurstPhotoQualityProfile.RawPlusProcessed),
        )
        assertEquals(
            BurstPhotoQualityProfile.RawOnly,
            normalizeBurstFileTypeProfile(BurstPhotoQualityProfile.RawOnly),
        )
        assertEquals(
            BurstPhotoQualityProfile.ProcessedOnly,
            normalizeBurstFileTypeProfile(BurstPhotoQualityProfile.ProcessedOnly),
        )
    }
}
