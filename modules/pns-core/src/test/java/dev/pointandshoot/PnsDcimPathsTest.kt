package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PnsDcimPathsTest {
    @Test
    fun canonicalWritePath_isPointAndShootWithoutSpaces() {
        assertEquals("PointAndShoot", PnsDcimPaths.FOLDER_NAME)
        assertEquals("DCIM/PointAndShoot", PnsDcimPaths.RELATIVE_PATH)
        assertFalse(PnsDcimPaths.FOLDER_NAME.contains(' '))
        assertFalse(PnsDcimPaths.RELATIVE_PATH.contains(' '))
        assertFalse(PnsDcimPaths.RELATIVE_PATH.contains('&'))
    }

    @Test
    fun readsIncludeLegacySpacedFolder_andDoNotMoveIt() {
        assertEquals("Point & Shoot", PnsDcimPaths.LEGACY_FOLDER_NAME)
        assertEquals("DCIM/Point & Shoot", PnsDcimPaths.LEGACY_RELATIVE_PATH)
        assertTrue(PnsDcimPaths.readRelativePaths().contains(PnsDcimPaths.RELATIVE_PATH))
        assertTrue(PnsDcimPaths.readRelativePaths().contains(PnsDcimPaths.LEGACY_RELATIVE_PATH))
        assertEquals(2, PnsDcimPaths.readRelativePaths().size)
    }

    @Test
    fun relativePathLikeArgs_coverCanonicalAndLegacy() {
        assertEquals("DCIM/PointAndShoot%", PnsDcimPaths.canonicalRelativePathLikeArg())
        assertArrayEquals(
            arrayOf("DCIM/PointAndShoot%", "DCIM/Point & Shoot%"),
            PnsDcimPaths.relativePathLikeArgs(),
        )
    }

    @Test
    fun relativePathSqlClause_orsBothFolders() {
        val (sql, args) = PnsDcimPaths.relativePathSqlClause("relative_path")
        assertEquals("(relative_path LIKE ? OR relative_path LIKE ?)", sql)
        assertArrayEquals(PnsDcimPaths.relativePathLikeArgs(), args)
    }

    @Test
    fun matchesRelativePath_acceptsTrailingSlashOnBothTrees() {
        assertTrue(PnsDcimPaths.matchesRelativePath("DCIM/PointAndShoot/"))
        assertTrue(PnsDcimPaths.matchesRelativePath("DCIM/PointAndShoot"))
        assertTrue(PnsDcimPaths.matchesRelativePath("DCIM/Point & Shoot/"))
        assertTrue(PnsDcimPaths.matchesRelativePath("DCIM/Point & Shoot"))
        assertFalse(PnsDcimPaths.matchesRelativePath("DCIM/Camera/"))
        assertFalse(PnsDcimPaths.matchesRelativePath(null))
        assertFalse(PnsDcimPaths.matchesRelativePath(""))
    }
}
