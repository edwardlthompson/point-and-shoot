package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VendorFaceEyeKeyNamesTest {

    @Test
    fun vendorish_com_org_vendor_substring() {
        assertTrue(VendorFaceEyeKeyNames.isVendorishMetadataKeyName("com.oem.face.enable"))
        assertTrue(VendorFaceEyeKeyNames.isVendorishMetadataKeyName("org.codeaurora.snapcam.face"))
        assertTrue(VendorFaceEyeKeyNames.isVendorishMetadataKeyName("android.vendor.face.mode"))
        assertFalse(VendorFaceEyeKeyNames.isVendorishMetadataKeyName("android.statistics.faceDetectMode"))
    }

    @Test
    fun named_keys_require_vendorish_and_face_eye_terms() {
        val keys =
            listOf(
                "com.oem.face.tracking.enable",
                "android.statistics.faces",
                "com.vendor.eyeaf.lock",
                "com.oem.iso.max",
            )
        assertEquals(
            listOf("com.oem.face.tracking.enable", "com.vendor.eyeaf.lock"),
            VendorFaceEyeKeyNames.namedFaceEyeTrackingVendorKeys(keys),
        )
    }
}
