package dev.pointandshoot.fleet

import org.junit.Assert.assertTrue
import org.junit.Test

class EncoderFleetSliceTest {

    @Test
    fun halAppendix_redactsPaths() {
        val raw = "open /data/user/0/dev.pointandshoot/files/x token deadbeef01234567"
        val redacted = FleetHalAppendix.redact(raw)
        assertTrue(redacted.contains("[APP_DATA]"))
        assertTrue(!redacted.contains("/data/user/0/dev.pointandshoot"))
    }
}
