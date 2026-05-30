package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeHubSearchTest {

    private val sampleIndex =
        listOf(
            ProbeHubSearchHit(
                title = "Device capability matrix",
                subtitle = "Capability matrices",
                keywords = "device capability matrix catalog hub menu",
                kindLabel = "Hub",
                pick = ProbeHubSearchPick.HubMenu("Device capability matrix"),
            ),
            ProbeHubSearchHit(
                title = "Eye-AF overlay",
                subtitle = "Settings · HUD & readouts",
                keywords = "eye af face detect overlay hud settings",
                kindLabel = "Setting",
                pick =
                    ProbeHubSearchPick.ChromeSetting(
                        ChromeSettingSearchHit(
                            title = "Eye-AF overlay",
                            subtitle = "HUD & readouts",
                            keywords = "eye af",
                            subPage = "hud",
                            settingKey = "hud.eye_af",
                        ),
                    ),
            ),
            ProbeHubSearchHit(
                title = "RAW DNG capture",
                subtitle = "Catalog · Still capture",
                keywords = "raw.dng raw dng still catalog feature",
                kindLabel = "Feature",
                pick = ProbeHubSearchPick.CatalogFeature("raw.dng", "RAW DNG capture"),
            ),
        )

    @Test
    fun filter_matchesHubMenuTitle() {
        val hits = ProbeHubSearch.filter(sampleIndex, "capability matrix")
        assertEquals(1, hits.size)
        assertTrue(hits.first().pick is ProbeHubSearchPick.HubMenu)
    }

    @Test
    fun filter_matchesChromeSettingKeyword() {
        val hits = ProbeHubSearch.filter(sampleIndex, "eye af")
        assertTrue(hits.any { it.pick is ProbeHubSearchPick.ChromeSetting })
    }

    @Test
    fun filter_matchesCatalogId() {
        val hits = ProbeHubSearch.filter(sampleIndex, "raw.dng")
        assertTrue(hits.any { it.pick is ProbeHubSearchPick.CatalogFeature })
    }

    @Test
    fun chromeSettingsIndex_hasSettingKeysForHudToggles() {
        val eye =
            buildChromeSettingsSearchIndex().first { it.title == "Eye-AF overlay" }
        assertEquals("hud.eye_af", eye.settingKey)
    }
}
