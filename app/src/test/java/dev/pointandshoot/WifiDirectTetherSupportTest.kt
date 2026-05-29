package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiDirectTetherSupportTest {
    @Test
    fun nsdServiceType_endsWithTcpSuffix() {
        assertTrue(WifiDirectTetherSupport.NSD_SERVICE_TYPE.endsWith("._tcp."))
    }

    @Test
    fun requiredPermissions_includesFineLocation() {
        assertTrue(WifiDirectTetherSupport.requiredPermissions().contains(android.Manifest.permission.ACCESS_FINE_LOCATION))
    }

    @Test
    fun tetherDefaultPort_matchesServer() {
        assertEquals(28765, TetheredCaptureServer.DEFAULT_PORT)
    }
}
