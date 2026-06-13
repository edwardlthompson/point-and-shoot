package dev.pointandshoot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Paparazzi scaffold for in-preview Settings search entry (Milestone T.9).
 * Not enabled in CI — [@Ignore] until golden snapshots are approved.
 */
@Ignore("Paparazzi gated until explicit chrome unlock — docs/VISUAL_REGRESSION_POLICY.md")
class ChromeSettingsSearchPaparazziTest {

    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5,
        )

    @Test
    fun settingsSearchField_emptyQuery() {
        paparazzi.snapshot {
            PnsTheme {
                ChromeSettingsSearchField(
                    query = "",
                    onQueryChange = {},
                )
            }
        }
    }
}
