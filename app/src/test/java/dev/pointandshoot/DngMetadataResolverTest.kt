package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DngMetadataResolverTest {

    @Test
    fun pickPhysical_prefersPreviewPinWhenInChildren() {
        val children = setOf("2", "3", "4")
        assertEquals(
            "4",
            DngMetadataResolver.pickPhysicalIdForDng(children, previewPhysicalCameraId = "4", activePhysicalFromResult = "3"),
        )
    }

    @Test
    fun pickPhysical_fallsBackToActiveResultWhenPinAbsentOrUnknown() {
        val children = setOf("2", "3", "4")
        assertEquals(
            "3",
            DngMetadataResolver.pickPhysicalIdForDng(children, previewPhysicalCameraId = "9", activePhysicalFromResult = "3"),
        )
    }

    @Test
    fun pickPhysical_returnsNullWhenNoChildren() {
        assertNull(DngMetadataResolver.pickPhysicalIdForDng(emptySet(), "4", "3"))
    }

    @Test
    fun pickPhysical_returnsNullWhenNoMatch() {
        val children = setOf("2", "3")
        assertNull(
            DngMetadataResolver.pickPhysicalIdForDng(children, previewPhysicalCameraId = "4", activePhysicalFromResult = "5"),
        )
    }

    @Test
    fun pairingMode_leafSession() {
        assertEquals(
            DngMetadataResolver.DngMetadataPairingMode.LEAF,
            DngMetadataResolver.pairingModeForDngSave(
                physicalChildren = emptySet(),
                pickedPhysicalId = "3",
                physicalTotalPresent = false,
                allowPhysicalTotalResultPairing = false,
                physicalCharacteristicsAvailable = true,
            ),
        )
    }

    @Test
    fun pairingMode_noPhysicalPick() {
        assertEquals(
            DngMetadataResolver.DngMetadataPairingMode.LOGICAL_NO_PICK,
            DngMetadataResolver.pairingModeForDngSave(
                physicalChildren = setOf("2", "3", "4"),
                pickedPhysicalId = null,
                physicalTotalPresent = false,
                allowPhysicalTotalResultPairing = false,
                physicalCharacteristicsAvailable = true,
            ),
        )
    }

    @Test
    fun pairingMode_physicalMapPresent_allowPhysicalFalse() {
        assertEquals(
            DngMetadataResolver.DngMetadataPairingMode.LOGICAL_IGNORE_PHYSICAL_MAP,
            DngMetadataResolver.pairingModeForDngSave(
                physicalChildren = setOf("2", "3", "4"),
                pickedPhysicalId = "4",
                physicalTotalPresent = true,
                allowPhysicalTotalResultPairing = false,
                physicalCharacteristicsAvailable = true,
            ),
        )
    }

    @Test
    fun pairingMode_physicalMapPresent_allowPhysicalTrue() {
        assertEquals(
            DngMetadataResolver.DngMetadataPairingMode.PHYSICAL_PAIRED,
            DngMetadataResolver.pairingModeForDngSave(
                physicalChildren = setOf("2", "3", "4"),
                pickedPhysicalId = "4",
                physicalTotalPresent = true,
                allowPhysicalTotalResultPairing = true,
                physicalCharacteristicsAvailable = true,
            ),
        )
    }

    @Test
    fun pairingMode_missingPhysicalTotal() {
        assertEquals(
            DngMetadataResolver.DngMetadataPairingMode.LOGICAL_MISSING_PHYSICAL_TOTAL,
            DngMetadataResolver.pairingModeForDngSave(
                physicalChildren = setOf("2", "3", "4"),
                pickedPhysicalId = "4",
                physicalTotalPresent = false,
                allowPhysicalTotalResultPairing = true,
                physicalCharacteristicsAvailable = true,
            ),
        )
    }

    @Test
    fun pairingMode_characteristicsLookupFailed() {
        assertEquals(
            DngMetadataResolver.DngMetadataPairingMode.LOGICAL_CHARS_LOOKUP_FAILED,
            DngMetadataResolver.pairingModeForDngSave(
                physicalChildren = setOf("2", "3", "4"),
                pickedPhysicalId = "4",
                physicalTotalPresent = true,
                allowPhysicalTotalResultPairing = true,
                physicalCharacteristicsAvailable = false,
            ),
        )
    }

    @Test
    fun pairingMode_endToEndWithPickPhysical() {
        val children = setOf("2", "3", "4")
        val picked =
            DngMetadataResolver.pickPhysicalIdForDng(
                children,
                previewPhysicalCameraId = "4",
                activePhysicalFromResult = "3",
            )
        assertEquals(
            DngMetadataResolver.DngMetadataPairingMode.LOGICAL_IGNORE_PHYSICAL_MAP,
            DngMetadataResolver.pairingModeForDngSave(
                physicalChildren = children,
                pickedPhysicalId = picked,
                physicalTotalPresent = true,
                allowPhysicalTotalResultPairing = DngSavePairingPolicy.ALLOW_PHYSICAL_TOTAL_RESULT_PAIRING,
                physicalCharacteristicsAvailable = true,
            ),
        )
    }

    @Test
    fun shippedPairingPolicy_staysFalse() {
        assertFalse(DngSavePairingPolicy.ALLOW_PHYSICAL_TOTAL_RESULT_PAIRING)
    }
}
