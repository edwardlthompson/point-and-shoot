package dev.pointandshoot.fleet

import dev.pointandshoot.fleet.CameraCapabilityCatalog.AppStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetParitySweepTest {
    @Test
    fun `classify OK when proven and advertised`() {
        val cell =
            FleetParitySweep.ParityCellResult(
                catalogId = "video.h264",
                advertised = true,
                sessionOk = true,
                appEnabled = true,
                provenOk = true,
            )
        assertEquals(FleetParitySweep.GapClass.OK, FleetParitySweep.classify(cell, AppStatus.Shipped))
    }

    @Test
    fun `classify GAP_DELIVERY_MISMATCH before advertised not proven`() {
        val cell =
            FleetParitySweep.ParityCellResult(
                catalogId = "video.uhd60",
                advertised = true,
                sessionOk = true,
                appEnabled = true,
                provenOk = true,
                deliveryProbe =
                    FleetParitySweep.DeliveryProbe(
                        requestedWidth = 3840,
                        requestedHeight = 2160,
                        requestedFps = 60,
                        actualWidth = 3840,
                        actualHeight = 2160,
                        actualFps = 29.8,
                        matchOk = false,
                        mismatchReason = "fps_low",
                    ),
            )
        assertEquals(FleetParitySweep.GapClass.GAP_DELIVERY_MISMATCH, FleetParitySweep.classify(cell, AppStatus.Shipped))
        assertTrue(
            FleetParitySweep.closurePlanPriority(FleetParitySweep.GapClass.GAP_DELIVERY_MISMATCH) <
                FleetParitySweep.closurePlanPriority(FleetParitySweep.GapClass.GAP_ADVERTISED_NOT_PROVEN),
        )
    }

    @Test
    fun `classify GAP_PLANNED for planned catalog rows`() {
        val cell =
            FleetParitySweep.ParityCellResult(
                catalogId = "still.heic",
                advertised = false,
                sessionOk = null,
                appEnabled = false,
                provenOk = false,
            )
        assertEquals(FleetParitySweep.GapClass.GAP_PLANNED, FleetParitySweep.classify(cell, AppStatus.Planned))
    }

    @Test
    fun `classify GAP_PROBE_INVENTORY for probe only skip`() {
        val cell =
            FleetParitySweep.ParityCellResult(
                catalogId = "encoder.hevc.1080p60",
                advertised = false,
                sessionOk = null,
                appEnabled = false,
                provenOk = true,
                failReason = "skip:probe_only_inventory",
            )
        assertEquals(FleetParitySweep.GapClass.GAP_PROBE_INVENTORY, FleetParitySweep.classify(cell, AppStatus.ProbeOnly))
        assertTrue(FleetParitySweep.isExcludedFromGapCount(FleetParitySweep.GapClass.GAP_PROBE_INVENTORY))
    }

    @Test
    fun `classify OK when not advertised on device`() {
        val cell =
            FleetParitySweep.ParityCellResult(
                catalogId = "raw.dng",
                advertised = false,
                sessionOk = false,
                appEnabled = true,
                provenOk = false,
                failReason = "not_advertised",
            )
        assertEquals(FleetParitySweep.GapClass.OK, FleetParitySweep.classify(cell, AppStatus.Shipped))
        assertFalse(
            FleetParitySweep.blocksFullPass(
                FleetParitySweep.GapClass.OK,
                FleetParitySweep.ConsumerImpact.SHIP_BLOCKER,
            ),
        )
    }

    @Test
    fun `blocksFullPass only for ship blocker gaps`() {
        assertTrue(
            FleetParitySweep.blocksFullPass(
                FleetParitySweep.GapClass.GAP_ADVERTISED_NOT_PROVEN,
                FleetParitySweep.ConsumerImpact.SHIP_BLOCKER,
            ),
        )
        assertFalse(
            FleetParitySweep.blocksFullPass(
                FleetParitySweep.GapClass.GAP_ADVERTISED_NOT_PROVEN,
                FleetParitySweep.ConsumerImpact.INFORMATIONAL,
            ),
        )
    }
}
