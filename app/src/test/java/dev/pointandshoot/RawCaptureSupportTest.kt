package dev.pointandshoot

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RawCaptureSupportTest {

    /**
     * JVM unit tests cannot construct [android.util.Size] (android.jar stubs throw [RuntimeException]).
     * [RawStreamPreference.Default] follows **RAW12 → RAW_SENSOR → RAW10** (fleet scripted-DNG default);
     * use [RawStreamPreference.RawSensorFirst] when probing HALs that need SENSOR before RAW12/RAW10.
     */

    @Test
    fun pickRawOutputFromMaps_returnsNullWhenEmpty() {
        assertNull(
            RawCaptureSupport.pickRawOutputFromMaps(
                raw12 = emptyList(),
                raw10 = emptyList(),
                rawSensor = emptyList(),
            ),
        )
    }

    @Test
    fun pickRawOutputFromMaps_raw10Only_returnsNullWhenNoRaw10() {
        assertNull(
            RawCaptureSupport.pickRawOutputFromMaps(
                raw12 = emptyList(),
                raw10 = emptyList(),
                rawSensor = emptyList(),
                preference = RawStreamPreference.Raw10Only,
            ),
        )
    }

    @Test
    fun pickRawSensorWxHForActiveArray_prefersExactActiveArray() {
        val picked =
            RawCaptureSupport.pickRawSensorWxHForActiveArray(
                rawSensorSizes = listOf(4096 to 3072, 1920 to 1440),
                activeW = 4096,
                activeH = 3072,
            )
        assertEquals(4096 to 3072, picked)
    }

    @Test
    fun pickRawSensorWxHForActiveArray_fallsBackToLargest() {
        val picked =
            RawCaptureSupport.pickRawSensorWxHForActiveArray(
                rawSensorSizes = listOf(1920 to 1440, 4000 to 3000),
                activeW = 4096,
                activeH = 3072,
            )
        assertEquals(4000 to 3000, picked)
    }

    @Test
    fun rawPickEffectiveLabel_mapsFormats() {
        assertEquals("RAW12", RawCaptureSupport.rawPickEffectiveLabel(ImageFormat.RAW12))
        assertEquals("RAW10", RawCaptureSupport.rawPickEffectiveLabel(ImageFormat.RAW10))
        assertEquals("RAW_SENSOR", RawCaptureSupport.rawPickEffectiveLabel(ImageFormat.RAW_SENSOR))
        assertEquals("null", RawCaptureSupport.rawPickEffectiveLabel(null))
    }

    @Test
    fun shouldPreferRawSensor_auxPinNotWide_true() {
        assertEquals(
            true,
            RawCaptureSupport.shouldPreferRawSensorForAuxPhysicalPreviewPin(
                RawStreamPreference.Default,
                logicalPhysicalChildren = setOf("2", "3", "4"),
                previewPhysicalCameraId = "4",
                wideBackCameraId = "2",
            ),
        )
    }

    @Test
    fun shouldPreferRawSensor_widePinOrNoPin_false() {
        assertEquals(
            false,
            RawCaptureSupport.shouldPreferRawSensorForAuxPhysicalPreviewPin(
                RawStreamPreference.Default,
                logicalPhysicalChildren = setOf("2", "3"),
                previewPhysicalCameraId = "2",
                wideBackCameraId = "2",
            ),
        )
        assertEquals(
            false,
            RawCaptureSupport.shouldPreferRawSensorForAuxPhysicalPreviewPin(
                RawStreamPreference.Default,
                logicalPhysicalChildren = setOf("2", "3"),
                previewPhysicalCameraId = null,
                wideBackCameraId = "2",
            ),
        )
    }

    @Test
    fun shouldPreferRawSensor_logicalTeleFocalCrop_true() {
        assertEquals(
            true,
            RawCaptureSupport.shouldPreferRawSensorForLogicalTeleFocalCrop(
                RawStreamPreference.Default,
                logicalPhysicalChildren = setOf("2", "3", "4"),
                focalCropMode = FocalMode.LongTele150,
            ),
        )
    }

    @Test
    fun shouldPreferRawSensor_logicalTeleFocalCrop_native_false() {
        assertEquals(
            false,
            RawCaptureSupport.shouldPreferRawSensorForLogicalTeleFocalCrop(
                RawStreamPreference.Default,
                logicalPhysicalChildren = setOf("2", "3", "4"),
                focalCropMode = null,
            ),
        )
    }

    @Test
    fun shouldPreferRawSensor_nonDefaultPreference_false() {
        assertEquals(
            false,
            RawCaptureSupport.shouldPreferRawSensorForAuxPhysicalPreviewPin(
                RawStreamPreference.Raw12Only,
                logicalPhysicalChildren = setOf("2", "3"),
                previewPhysicalCameraId = "3",
                wideBackCameraId = "2",
            ),
        )
    }

    @Test
    fun shouldUseLeafNonWide_trueWhenLeafBackNotWide() {
        assertEquals(
            true,
            RawCaptureSupport.shouldUseLeafNonWideBackRawSensorPolicy(
                RawStreamPreference.Default,
                sessionCameraId = "3",
                wideBackCameraId = "2",
                logicalPhysicalChildren = emptySet(),
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
            ),
        )
    }

    @Test
    fun useNeutralColorPipelineForRawStillCore_leafUw_false() {
        assertEquals(
            false,
            RawCaptureSupport.useNeutralColorPipelineForRawStillCore(
                wideBackCameraId = "2",
                sessionPhysicalChildren = emptySet(),
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
                sessionCameraId = "3",
                previewPhysicalCameraId = null,
            ),
        )
    }

    @Test
    fun useNeutralColorPipelineForRawStillCore_leafWide_false() {
        assertEquals(
            false,
            RawCaptureSupport.useNeutralColorPipelineForRawStillCore(
                wideBackCameraId = "2",
                sessionPhysicalChildren = emptySet(),
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
                sessionCameraId = "2",
                previewPhysicalCameraId = null,
            ),
        )
    }

    @Test
    fun useNeutralColorPipelineForRawStillCore_logicalTelePin_true() {
        assertEquals(
            true,
            RawCaptureSupport.useNeutralColorPipelineForRawStillCore(
                wideBackCameraId = "2",
                sessionPhysicalChildren = setOf("2", "3", "4"),
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
                sessionCameraId = "0",
                previewPhysicalCameraId = "4",
            ),
        )
    }

    @Test
    fun isLeafBackSession_trueForEmptyPhysicalChildrenOnBack() {
        assertEquals(
            true,
            RawCaptureSupport.isLeafBackSession(
                sessionPhysicalChildren = emptySet(),
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
            ),
        )
        assertEquals(
            false,
            RawCaptureSupport.isLeafBackSession(
                sessionPhysicalChildren = setOf("2", "3"),
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
            ),
        )
    }

    @Test
    fun shouldUseLeafNonWide_falseWhenLogicalSessionOrWideOrFront() {
        assertEquals(
            false,
            RawCaptureSupport.shouldUseLeafNonWideBackRawSensorPolicy(
                RawStreamPreference.Default,
                sessionCameraId = "0",
                wideBackCameraId = "2",
                logicalPhysicalChildren = setOf("2", "3"),
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
            ),
        )
        assertEquals(
            false,
            RawCaptureSupport.shouldUseLeafNonWideBackRawSensorPolicy(
                RawStreamPreference.Default,
                sessionCameraId = "2",
                wideBackCameraId = "2",
                logicalPhysicalChildren = emptySet(),
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
            ),
        )
        assertEquals(
            false,
            RawCaptureSupport.shouldUseLeafNonWideBackRawSensorPolicy(
                RawStreamPreference.Default,
                sessionCameraId = "1",
                wideBackCameraId = "2",
                logicalPhysicalChildren = emptySet(),
                lensFacing = CameraCharacteristics.LENS_FACING_FRONT,
            ),
        )
    }
}
