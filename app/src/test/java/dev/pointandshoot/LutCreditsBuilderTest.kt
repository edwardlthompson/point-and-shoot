package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LutCreditsBuilderTest {

    @Test
    fun `creditsFromCatalog returns one row per catalog entry`() {
        val rows = LutCreditsBuilder.creditsFromCatalog()
        assertEquals(LutCatalog.entries.size, rows.size)
    }

    @Test
    fun `every catalog entry contributes a credit row with non-empty fields`() {
        for (row in LutCreditsBuilder.creditsFromCatalog()) {
            assertTrue("displayName for ${row.displayName}", row.displayName.isNotBlank())
            assertTrue("source for ${row.displayName}", row.source.isNotBlank())
            assertTrue("spdx for ${row.displayName}", row.spdx.isNotBlank())
            assertTrue("scope for ${row.displayName}", row.scope.isNotBlank())
            assertTrue("description for ${row.displayName}", row.description.isNotBlank())
        }
    }

    @Test
    fun `every credit row's SPDX is in the allowed whitelist`() {
        for (row in LutCreditsBuilder.creditsFromCatalog()) {
            assertTrue(
                "${row.displayName} has SPDX '${row.spdx}' which is not in ALLOWED_SPDX",
                row.spdx in LutCatalog.ALLOWED_SPDX,
            )
        }
    }

    @Test
    fun `credit rows preserve catalog ordering`() {
        val expectedNames = LutCatalog.entries.map { it.displayName }
        val actualNames = LutCreditsBuilder.creditsFromCatalog().map { it.displayName }
        assertEquals(expectedNames, actualNames)
    }

    @Test
    fun `well-known catalog entries appear in the credits`() {
        val names = LutCreditsBuilder.creditsFromCatalog().map { it.displayName }
        assertTrue("expected None: $names", names.contains("None"))
        assertTrue("expected Cinematic: $names", names.contains("Point & Shoot Cinematic"))
        assertTrue("expected B&W BT.709: $names", names.contains("B&W BT.709"))
    }
}
