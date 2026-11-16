package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PnsMediaStoreGalleryTest {
    @Test
    fun dcimRelativePathLikeArg_usesCanonicalPointAndShootTree() {
        assertEquals("DCIM/PointAndShoot%", PnsMediaStoreGallery.dcimRelativePathLikeArg())
        assertEquals(PnsDcimPaths.canonicalRelativePathLikeArg(), PnsMediaStoreGallery.dcimRelativePathLikeArg())
    }

    @Test
    fun dcimRelativePathLikeArgs_includeLegacySpacedFolderForReads() {
        val args = PnsMediaStoreGallery.dcimRelativePathLikeArgs()
        assertArrayEquals(
            arrayOf("DCIM/PointAndShoot%", "DCIM/Point & Shoot%"),
            args,
        )
        assertTrue(args.contains("DCIM/Point & Shoot%"))
        assertEquals(PnsDcimPaths.RELATIVE_PATH + "%", args.first())
        assertEquals(PnsDcimPaths.LEGACY_RELATIVE_PATH + "%", args.last())
    }

    @Test
    fun defaultMaxItems_isCapped() {
        assertTrue(PnsMediaStoreGallery.DEFAULT_MAX_ITEMS in 100..1000)
    }
}
