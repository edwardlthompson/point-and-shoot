package dev.pointandshoot

import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DngGalleryOrientationTest {
    @Test
    fun tiff8_mapsToRotate270() {
        assertEquals(
            ExifInterface.ORIENTATION_ROTATE_270,
            DngGalleryOrientation.tiffOrientationToExif(8),
        )
    }

    @Test
    fun rotate270_isSwapAspect() {
        assertTrue(
            DngGalleryOrientation.needsSwapWidthHeight(ExifInterface.ORIENTATION_ROTATE_270),
        )
    }

    @Test
    fun rotationDegrees_forRotate180() {
        assertEquals(
            180f,
            DngGalleryOrientation.rotationDegreesForExif(ExifInterface.ORIENTATION_ROTATE_180),
        )
    }
}
