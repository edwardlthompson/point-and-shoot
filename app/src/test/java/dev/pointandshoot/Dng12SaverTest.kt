package dev.pointandshoot

import android.graphics.ImageFormat
import dev.pointandshoot.fleet.ReferenceAppPipelineContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Dng12SaverTest {

    @Test
    fun saveStats_elapsedMs_convertsNanoseconds() {
        val stats =
            Dng12Saver.SaveStats(
                elapsedNs = 2_500_000L,
                profileId = "standard_pro",
                rawMode = RawMode.LosslessCompressedDng,
            )
        assertEquals(2.5, stats.elapsedMs, 0.001)
    }

    @Test
    fun saveStats_exposesProfileFields() {
        val stats =
            Dng12Saver.SaveStats(
                elapsedNs = 1L,
                profileId = ImagingProfile.UltraMax.id,
                rawMode = RawMode.UncompressedRaw12Dng,
            )
        assertEquals(ImagingProfile.UltraMax.id, stats.profileId)
        assertEquals(RawMode.UncompressedRaw12Dng, stats.rawMode)
    }

    @Test
    fun leafPostSaveReconcile_disabledOnGenericHostJvm() {
        assertFalse(ReferenceAppPipelineContract.leafPostSaveTiffReconcileEnabled("3"))
        assertFalse(ReferenceAppPipelineContract.leafPostSaveTiffReconcileEnabled("4"))
    }

    @Test
    fun defaultRawTierOrder_matchesLockedRegressionOrder() {
        assertEquals(
            listOf(
                ImageFormat.RAW12,
                ImageFormat.RAW_SENSOR,
                ImageFormat.RAW10,
            ),
            RawCaptureSupport.DEFAULT_RAW_STREAM_TIER_ORDER.toList(),
        )
    }

    @Test
    fun dngSavePairingPolicy_matchesShippedLock() {
        assertFalse(DngSavePairingPolicy.ALLOW_PHYSICAL_TOTAL_RESULT_PAIRING)
    }
}
