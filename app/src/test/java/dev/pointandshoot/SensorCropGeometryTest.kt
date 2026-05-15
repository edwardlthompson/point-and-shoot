package dev.pointandshoot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorCropGeometryTest {

    @Test
    fun allows_digital_crop_matches_resolved_roles_physical_session() {
        val wide = "2"
        val tele = "7"
        assertTrue(
            SensorCropGeometry.allowsDigitalCrop(tele, FocalMode.Portrait85, null, wide, tele),
        )
        assertTrue(
            SensorCropGeometry.allowsDigitalCrop(tele, FocalMode.LongTele150, null, wide, tele),
        )
        assertTrue(
            SensorCropGeometry.allowsDigitalCrop(wide, FocalMode.Street35, null, wide, tele),
        )
        assertFalse(
            SensorCropGeometry.allowsDigitalCrop("3", FocalMode.Portrait85, null, wide, tele),
        )
    }

    @Test
    fun allows_digital_crop_logical_parent_covers_physical_tele() {
        val wide = "1"
        val tele = "5"
        val logical = "0"
        val phys = setOf("1", wide, tele, "6")
        assertTrue(
            SensorCropGeometry.allowsDigitalCrop(logical, FocalMode.Portrait85, phys, wide, tele),
        )
        assertTrue(
            SensorCropGeometry.allowsDigitalCrop(logical, FocalMode.Street35, phys, wide, tele),
        )
        assertFalse(
            SensorCropGeometry.allowsDigitalCrop(logical, FocalMode.Portrait85, emptySet(), wide, tele),
        )
    }

    @Test
    fun long_tele_150_crop_uses_mid_tele_sensor_only() {
        val wide = "0"
        val tele = "4"
        assertTrue(
            SensorCropGeometry.allowsDigitalCrop(tele, FocalMode.LongTele150, null, wide, tele),
        )
    }

    @Test
    fun long_tele_150_rejects_wrong_physical_even_when_fourth_lens_exists_elsewhere() {
        val wide = "0"
        val tele = "4"
        val periscope = "9"
        assertTrue(
            SensorCropGeometry.allowsDigitalCrop(
                tele,
                FocalMode.LongTele150,
                null,
                wide,
                tele,
            ),
        )
        assertFalse(
            SensorCropGeometry.allowsDigitalCrop(
                periscope,
                FocalMode.LongTele150,
                null,
                wide,
                tele,
            ),
        )
    }
}
