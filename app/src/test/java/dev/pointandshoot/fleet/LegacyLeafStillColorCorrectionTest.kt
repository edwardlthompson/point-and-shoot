package dev.pointandshoot.fleet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyLeafStillColorCorrectionTest : LegacyFleetPolicyTestHarness() {

    @Test
    fun appliesCaptureTimeGains_uwAndTeleOnly() {
        assertTrue(
            LegacyLeafStillColorCorrection.appliesCaptureTimeGainsWhen(
                deviceApplies = true,
                LegacyFleetPolicy.CANONICAL_UW,
            ),
        )
        assertTrue(
            LegacyLeafStillColorCorrection.appliesCaptureTimeGainsWhen(
                deviceApplies = true,
                LegacyFleetPolicy.CANONICAL_TELE,
            ),
        )
        assertFalse(
            LegacyLeafStillColorCorrection.appliesCaptureTimeGainsWhen(
                deviceApplies = true,
                LegacyFleetPolicy.CANONICAL_WIDE,
            ),
        )
    }
}
