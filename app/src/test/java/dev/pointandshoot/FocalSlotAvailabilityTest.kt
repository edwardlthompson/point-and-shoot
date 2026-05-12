package dev.pointandshoot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocalSlotAvailabilityTest {

    @Test
    fun `digital eq slots enabled at 12MP boundary`() {
        assertTrue(FocalSlotAvailability.digitalEqSlotsEnabled(4000, 3000)) // 12.0
    }

    @Test
    fun `digital eq slots disabled below 12MP`() {
        assertFalse(FocalSlotAvailability.digitalEqSlotsEnabled(3000, 3000)) // 9.0
    }

    @Test
    fun `digital eq slots disabled for non positive sizes`() {
        assertFalse(FocalSlotAvailability.digitalEqSlotsEnabled(0, 3000))
        assertFalse(FocalSlotAvailability.digitalEqSlotsEnabled(3000, 0))
    }

    @Test
    fun `megapixelsFromActiveArray matches spec`() {
        val mp = FocalSlotAvailability.megapixelsFromActiveArray(4096, 3072)
        org.junit.Assert.assertEquals(12.582912, mp, 0.0001)
    }
}
