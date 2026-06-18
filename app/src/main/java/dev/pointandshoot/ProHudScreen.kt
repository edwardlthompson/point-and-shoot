package dev.pointandshoot

import androidx.compose.runtime.Composable
import dev.pointandshoot.preview.mock.MockPreviewScreens
import dev.pointandshoot.preview.mock.UnifiedMockPreviewScreen

/**
 * Legacy alias for [UnifiedMockPreviewScreen] (T.14).
 *
 * Prefer `--es pns_screen mock` or engineering hub **Mock preview (HUD + GLES)**.
 */
@Suppress("FunctionNaming")
@Composable
fun ProHudScreen(onBack: () -> Unit) {
    UnifiedMockPreviewScreen(
        onBack = onBack,
        launchRoute = MockPreviewScreens.ROUTE_PROHUD,
    )
}
