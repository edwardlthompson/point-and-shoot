package dev.pointandshoot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Avoid instantiating [android.graphics.Rect] here: JVM unit tests use Android stubs
 * that throw on [Rect.width] / copy constructors. Offset + [Rect] integration is
 * covered on-device via [PreviewEngineScreen] + logcat `SCALER_CROP_REGION=…`.
 */
class SensorCropGeometryTest {

    @Test
    fun allows_digital_crop_only_on_wide_and_tele_physical_ids() {
        assertTrue(SensorCropGeometry.allowsDigitalCrop("2", FocalMode.Street35))
        assertTrue(SensorCropGeometry.allowsDigitalCrop("2", FocalMode.Standard50))
        assertFalse(SensorCropGeometry.allowsDigitalCrop("2", FocalMode.Portrait85))

        assertTrue(SensorCropGeometry.allowsDigitalCrop("4", FocalMode.Portrait85))
        assertTrue(SensorCropGeometry.allowsDigitalCrop("4", FocalMode.LongTele150))
        assertFalse(SensorCropGeometry.allowsDigitalCrop("4", FocalMode.Street35))

        assertTrue(SensorCropGeometry.allowsDigitalCrop("5", FocalMode.Portrait85))
        assertTrue(SensorCropGeometry.allowsDigitalCrop("6", FocalMode.LongTele150))

        assertFalse(SensorCropGeometry.allowsDigitalCrop("3", FocalMode.Street35))
        assertFalse(SensorCropGeometry.allowsDigitalCrop("0", FocalMode.Street35))
    }
}
