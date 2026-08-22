@file:Suppress("FunctionNaming", "MagicNumber", "TopLevelPropertyNaming")

package dev.pointandshoot

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn

/** Landscape + large-screen / foldable detection. Portrait chrome path stays default. */
object PnsFormFactor {
    fun isLandscape(configuration: Configuration): Boolean =
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun isTwoPane(configuration: Configuration): Boolean {
        val w = configuration.screenWidthDp
        val h = configuration.screenHeightDp
        val smallest = minOf(w, h)
        return w >= 600 && w > h && smallest >= 500
    }
}

@Composable
fun PnsAdaptivePreviewChrome(
    landscape: Boolean,
    finderFlexWeight: Float = PreviewChromeFinderWeight,
    topBand: @Composable () -> Unit,
    finder: @Composable (Modifier) -> Unit,
    belowFinder: @Composable ColumnScope.() -> Unit,
) {
    if (landscape) {
        Column(modifier = Modifier.fillMaxSize()) {
            topBand()
            Row(modifier = Modifier.weight(1f).fillMaxSize()) {
                Box(modifier = Modifier.weight(1.65f).fillMaxHeight()) {
                    finder(Modifier.fillMaxSize())
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    belowFinder()
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            topBand()
            finder(Modifier.weight(finderFlexWeight).fillMaxSize())
            belowFinder()
        }
    }
}

/** Must match locked portrait finder flex. */
internal const val PreviewChromeFinderWeight: Float = 2.9f

@Composable
fun PnsFoldableTwoPane(
    enabled: Boolean,
    preview: @Composable () -> Unit,
    secondary: @Composable () -> Unit,
) {
    val configuration = LocalConfiguration.current
    if (enabled && PnsFormFactor.isTwoPane(configuration)) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1.15f).fillMaxHeight()) { preview() }
            Box(
                modifier =
                    Modifier
                        .widthIn(min = 320.dp)
                        .weight(1f)
                        .fillMaxHeight(),
            ) { secondary() }
        }
    } else {
        preview()
    }
}
