package dev.pointandshoot.fleet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Op13LeafStillColorCorrectionTest {

    @Test
    fun appliesCaptureTimeGains_uwAndTeleOnly() {
        assertTrue(
            Op13LeafStillColorCorrection.appliesCaptureTimeGainsWhen(
                deviceApplies = true,
                OnePlus13FleetPolicy.CANONICAL_UW,
            ),
        )
        assertTrue(
            Op13LeafStillColorCorrection.appliesCaptureTimeGainsWhen(
                deviceApplies = true,
                OnePlus13FleetPolicy.CANONICAL_TELE,
            ),
        )
        assertFalse(
            Op13LeafStillColorCorrection.appliesCaptureTimeGainsWhen(
                deviceApplies = true,
                OnePlus13FleetPolicy.CANONICAL_WIDE,
            ),
        )
    }
}
