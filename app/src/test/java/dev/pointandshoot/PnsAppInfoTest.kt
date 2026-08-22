package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class PnsAppInfoTest {
    @Test
    fun displayLabel_joinsAppNameAndStableVersion() {
        assertEquals("Point & Shoot 0.14.0", PnsAppInfo.displayLabel("Point & Shoot", "0.14.0"))
        assertEquals("0.14.0", PnsAppInfo.displayLabel("  ", "0.14.0"))
    }
}
