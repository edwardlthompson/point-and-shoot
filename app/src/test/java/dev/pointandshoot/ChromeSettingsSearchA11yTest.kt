package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host-side accessibility contract for Settings search index entries. */
class ChromeSettingsSearchA11yTest {

    @Test
    fun searchIndex_entriesHaveReadableLabels() {
        val index = buildChromeSettingsSearchIndex()
        assertTrue("settings search index should not be empty", index.isNotEmpty())
        index.forEach { hit ->
            assertTrue("title blank for ${hit.settingKey}", hit.title.isNotBlank())
            assertTrue("subtitle blank for ${hit.title}", hit.subtitle.isNotBlank())
            assertTrue("subPage blank for ${hit.title}", hit.subPage.isNotBlank())
            assertTrue("keywords blank for ${hit.title}", hit.keywords.isNotBlank())
        }
    }

    @Test
    fun searchIndex_settingKeysAreUniqueWhenPresent() {
        val keys =
            buildChromeSettingsSearchIndex()
                .mapNotNull { it.settingKey }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun searchIndex_includesAboutEntry() {
        val abouts = buildChromeSettingsSearchIndex().filter { it.subPage == "about" }
        assertTrue(abouts.any { it.title.contains("About", ignoreCase = true) })
        assertEquals("about.heritage", abouts.single { it.settingKey == "about.heritage" }.settingKey)
        assertTrue(abouts.any { it.title == "Donate via Venmo" && it.settingKey == "about.donate" })
        assertTrue(abouts.any { it.title == "Check for updates" && it.settingKey == "about.checkUpdates" })
        assertTrue(abouts.any { it.title == "Wi-Fi only updates" && it.settingKey == "about.wifiOnly" })
        assertTrue(abouts.any { it.title == "Add in Obtainium" && it.settingKey == "about.obtainium" })
        assertTrue(abouts.any { it.title == "What's new" && it.settingKey == "about.whatsNew" })
        assertTrue(abouts.any { it.title == "Open NOTICE / licenses" && it.settingKey == "about.notice" })
        assertTrue(abouts.any { it.title == "Open LICENSE" && it.settingKey == "about.license" })
    }

    @Test
    fun searchIndex_findsAboutByKeyword() {
        val hits =
            buildChromeSettingsSearchIndex().filter { hit ->
                hit.keywords.contains("about") ||
                    hit.title.lowercase().contains("about")
            }
        assertTrue(hits.isNotEmpty())
    }

    @Test
    fun settingsSearchField_stringsAreNonBlank() {
        assertTrue(ChromeSettingsA11y.SEARCH_FIELD.isNotBlank())
        assertTrue(ChromeSettingsA11y.SEARCH_ICON.isNotBlank())
    }
}
