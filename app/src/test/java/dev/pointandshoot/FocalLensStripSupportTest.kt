package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocalLensStripSupportTest {

    @Test
    fun teleSlotsRecognized() {
        assertTrue(FocalLensStripSupport.isTeleSlot(FocalMmSlot.M73))
        assertTrue(FocalLensStripSupport.isTeleSlot(FocalMmSlot.M85))
        assertTrue(FocalLensStripSupport.isTeleSlot(FocalMmSlot.M150))
        assertFalse(FocalLensStripSupport.isTeleSlot(FocalMmSlot.M23))
    }

    @Test
    fun digitalEqPolicySlots() {
        assertTrue(FocalLensStripSupport.isDigitalEqPolicySlot(FocalMmSlot.M35))
        assertTrue(FocalLensStripSupport.isDigitalEqPolicySlot(FocalMmSlot.M50))
        assertTrue(FocalLensStripSupport.isDigitalEqPolicySlot(FocalMmSlot.M85))
        assertTrue(FocalLensStripSupport.isDigitalEqPolicySlot(FocalMmSlot.M150))
        assertFalse(FocalLensStripSupport.isDigitalEqPolicySlot(FocalMmSlot.M73))
    }

    @Test
    fun formatShortNativeFocalMm_oneDecimal() {
        assertEquals("6.1mm", FocalLensStripSupport.formatShortNativeFocalMm(6.06f))
    }

    @Test
    fun formatShortNativeFocalMm_integerMm() {
        assertEquals("3mm", FocalLensStripSupport.formatShortNativeFocalMm(3.04f))
    }
}
