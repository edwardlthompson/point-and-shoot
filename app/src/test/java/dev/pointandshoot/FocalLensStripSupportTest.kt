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
    fun formatShortNativeFocalMm_roundsUpToOneDecimal() {
        assertEquals("6.1mm", FocalLensStripSupport.formatShortNativeFocalMm(6.01f))
    }

    @Test
    fun formatShortNativeFocalMm_integerMm() {
        assertEquals("3.1mm", FocalLensStripSupport.formatShortNativeFocalMm(3.04f))
    }

    @Test
    fun primeAssignments_preferLeastCropOverHigherEffectiveMp() {
        val assignments =
            FocalLensStripSupport.resolvePrimeLensAssignmentsFromCandidates(
                candidates =
                    listOf(
                        FocalLensStripSupport.PrimeLensCandidate(
                            cameraId = "uw",
                            nativeEqMm = 14,
                            focalMm = 1.8f,
                            sensorMp = 64.0,
                        ),
                        FocalLensStripSupport.PrimeLensCandidate(
                            cameraId = "wide",
                            nativeEqMm = 24,
                            focalMm = 3.2f,
                            sensorMp = 50.0,
                        ),
                    ),
                targets = listOf(24),
            )
        assertEquals(1, assignments.size)
        // Even with higher UW sensor MP, 24mm should route to 24mm-native wide (least crop).
        assertEquals("wide", assignments.first().cameraId)
        assertEquals(24, assignments.first().nativeEqMm)
    }

    @Test
    fun primeAssignments_useClosestLowerNativeForOverlapTargets() {
        val assignments =
            FocalLensStripSupport.resolvePrimeLensAssignmentsFromCandidates(
                candidates =
                    listOf(
                        FocalLensStripSupport.PrimeLensCandidate(
                            cameraId = "uw",
                            nativeEqMm = 14,
                            focalMm = 1.8f,
                            sensorMp = 16.0,
                        ),
                        FocalLensStripSupport.PrimeLensCandidate(
                            cameraId = "wide",
                            nativeEqMm = 23,
                            focalMm = 6.1f,
                            sensorMp = 12.6,
                        ),
                        FocalLensStripSupport.PrimeLensCandidate(
                            cameraId = "tele",
                            nativeEqMm = 71,
                            focalMm = 13.3f,
                            sensorMp = 16.2,
                        ),
                    ),
                targets = listOf(24, 85),
            )
        assertEquals(2, assignments.size)
        assertEquals("wide", assignments.first { it.targetEqMm == 24 }.cameraId)
        assertEquals("tele", assignments.first { it.targetEqMm == 85 }.cameraId)
    }
}
