package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class PnsUserFacingErrorsTest {
    @Test
    fun stillCaptureFailure_mapsBusy() {
        assertEquals(
            "Could not save — the engine is still finishing the last capture. Try again in a moment.",
            PnsUserFacingErrors.stillCaptureFailure(IllegalStateException("encode_lane_busy")),
        )
    }

    @Test
    fun stillCaptureFailure_mapsEnospc() {
        assertEquals(
            "Could not save — storage may be full.",
            PnsUserFacingErrors.stillCaptureFailure(RuntimeException("ENOSPC")),
        )
    }

    @Test
    fun stillCaptureFailure_blankMessage() {
        assertEquals(
            "Could not save this capture.",
            PnsUserFacingErrors.stillCaptureFailure(RuntimeException("")),
        )
    }
}
