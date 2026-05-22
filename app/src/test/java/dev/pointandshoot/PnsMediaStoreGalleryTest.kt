package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PnsMediaStoreGalleryTest {
    @Test
    fun dcimRelativePathLikeArg_usesPointAndShootTree() {
        assertEquals("DCIM/Point & Shoot%", PnsMediaStoreGallery.dcimRelativePathLikeArg())
    }

    @Test
    fun defaultMaxItems_isCapped() {
        assertTrue(PnsMediaStoreGallery.DEFAULT_MAX_ITEMS in 100..1000)
    }
}
