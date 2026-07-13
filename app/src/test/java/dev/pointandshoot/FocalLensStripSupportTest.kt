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
    fun primeEqTargets_includeNativeTeleAndDigitalCrops() {
        assertEquals(listOf(14, 23, 35, 50, 73, 85, 150), FocalLensStripSupport.primeEqTargets())
        assertEquals(FocalLensStripSupport.FOCAL_CHIP_EQ_MM, FocalLensStripSupport.primeEqTargets())
    }

    @Test
    fun primeAssignments_teleChipTargetsKeepNative73DistinctFrom85() {
        val assignments =
            FocalLensStripSupport.resolvePrimeLensAssignmentsFromCandidates(
                candidates =
                    listOf(
                        FocalLensStripSupport.PrimeLensCandidate(
                            cameraId = "wide",
                            nativeEqMm = 23,
                            focalMm = 6.1f,
                            sensorMp = 12.6,
                        ),
                        FocalLensStripSupport.PrimeLensCandidate(
                            cameraId = "tele",
                            nativeEqMm = 73,
                            focalMm = 13.9f,
                            sensorMp = 12.6,
                        ),
                    ),
                targets = FocalLensStripSupport.FOCAL_CHIP_EQ_MM,
            )
        val byTarget = assignments.associateBy { it.targetEqMm }
        assertEquals("tele", byTarget.getValue(73).cameraId)
        assertTrue(byTarget.getValue(73).isNative)
        assertEquals("tele", byTarget.getValue(85).cameraId)
        assertFalse(byTarget.getValue(85).isNative)
        assertEquals("tele", byTarget.getValue(150).cameraId)
        // Nearest-chip remap must not collapse 73 → 85.
        val nearest73 =
            assignments.minWith(
                compareBy(
                    { kotlin.math.abs(it.targetEqMm - 73) },
                    { if (it.isNative) 0 else 1 },
                    { it.targetEqMm },
                ),
            )
        assertEquals(73, nearest73.targetEqMm)
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

    @Test
    fun primeAssignments_fallsBackToNearestNativeWhenNoCropCandidateExists() {
        val assignments =
            FocalLensStripSupport.resolvePrimeLensAssignmentsFromCandidates(
                candidates =
                    listOf(
                        FocalLensStripSupport.PrimeLensCandidate(
                            cameraId = "uw",
                            nativeEqMm = 15,
                            focalMm = 1.7f,
                            sensorMp = 12.0,
                        ),
                        FocalLensStripSupport.PrimeLensCandidate(
                            cameraId = "wide",
                            nativeEqMm = 23,
                            focalMm = 2.9f,
                            sensorMp = 12.5,
                        ),
                    ),
                targets = listOf(14),
            )
        assertEquals(1, assignments.size)
        assertEquals("uw", assignments.first().cameraId)
        assertEquals(15, assignments.first().nativeEqMm)
    }

    @Test
    fun primeAssignments_retainsLowMpStaticCropAssignmentForMatrixRouting() {
        val assignments =
            FocalLensStripSupport.resolvePrimeLensAssignmentsFromCandidates(
                candidates =
                    listOf(
                        FocalLensStripSupport.PrimeLensCandidate(
                            cameraId = "wide",
                            nativeEqMm = 23,
                            focalMm = 3.0f,
                            sensorMp = 12.6,
                        ),
                    ),
                targets = listOf(35),
            )
        assertEquals(1, assignments.size)
        assertEquals("wide", assignments.first().cameraId)
        assertEquals(35, assignments.first().targetEqMm)
        assertTrue(assignments.first().effectiveMp > 0.0)
    }
}
