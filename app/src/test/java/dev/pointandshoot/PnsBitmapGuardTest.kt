package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class PnsBitmapGuardTest {
    @Test
    fun activeCount_startsAtZero() {
        assertEquals(0, PnsBitmapGuard.activeCount())
    }
}
