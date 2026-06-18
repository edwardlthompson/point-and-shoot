package dev.pointandshoot.preview.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewVendorSessionParametersTest {

    @Test
    fun sessionSweepKeys_wideCamOnlyWhenExperimental() {
        val keys = listOf("vendor.key.a", "vendor.key.b")
        assertEquals(keys, PreviewVendorSessionParameters.sessionSweepKeys("2", true, keys))
        assertTrue(PreviewVendorSessionParameters.sessionSweepKeys("3", true, keys).isEmpty())
        assertTrue(PreviewVendorSessionParameters.sessionSweepKeys("2", false, keys).isEmpty())
    }
}
