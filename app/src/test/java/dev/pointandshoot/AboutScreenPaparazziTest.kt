package dev.pointandshoot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Paparazzi scaffold — **Settings/About only** (Milestone T.9).
 * Preview chrome remains locked; do not snapshot [PreviewEngineScreen].
 *
 * Remove [@Ignore] only with maintainer chrome-unlock sign-off — see [docs/VISUAL_REGRESSION_POLICY.md].
 */
@Ignore("Paparazzi gated until explicit chrome unlock — docs/VISUAL_REGRESSION_POLICY.md")
class AboutScreenPaparazziTest {

    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5,
        )

    @Test
    fun aboutRailSheet_default() {
        paparazzi.snapshot {
            PnsTheme {
                AboutRailSheetContent()
            }
        }
    }
}
