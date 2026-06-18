package dev.pointandshoot

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.pointandshoot.preview.mock.MockPreviewScreens
import dev.pointandshoot.preview.mock.UnifiedMockPreviewScreen

/**
 * Legacy alias for [UnifiedMockPreviewScreen] (T.14).
 *
 * Milestone 6 automation still uses `--es pns_screen glpreview`; the unified screen
 * logs `glpreview screen compose active lut=…` for gate compatibility.
 */
@Suppress("FunctionNaming")
@Composable
fun GLPreviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UnifiedMockPreviewScreen(
        onBack = onBack,
        launchRoute = MockPreviewScreens.ROUTE_GLPREVIEW,
        modifier = modifier,
    )
}
