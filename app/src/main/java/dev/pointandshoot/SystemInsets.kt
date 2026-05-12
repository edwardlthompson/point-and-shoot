package dev.pointandshoot

import android.os.Build
import android.view.View
import android.view.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

data class SystemInsetsDp(
    val left: Dp,
    val top: Dp,
    val right: Dp,
    val bottom: Dp,
)

@Composable
fun rememberSystemInsetsDp(): SystemInsetsDp {
    val view = LocalView.current
    val density = LocalDensity.current

    var insetsPx by remember { mutableStateOf(intArrayOf(0, 0, 0, 0)) }

    /**
     * Status/nav bars plus display cutout so preview/chrome clears punch-hole/camera (Milestone 9).
     */
    fun toMergedSystemBarsAndCutoutPx(insets: WindowInsets): IntArray =
        when {
            Build.VERSION.SDK_INT >= 30 -> {
                val types = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                val i = insets.getInsets(types)
                intArrayOf(i.left, i.top, i.right, i.bottom)
            }
            Build.VERSION.SDK_INT >= 28 -> {
                @Suppress("DEPRECATION")
                val sys =
                    intArrayOf(
                        insets.systemWindowInsetLeft,
                        insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight,
                        insets.systemWindowInsetBottom,
                    )
                val cut = insets.displayCutout
                if (cut == null) {
                    sys
                } else {
                    intArrayOf(
                        max(sys[0], cut.safeInsetLeft),
                        max(sys[1], cut.safeInsetTop),
                        max(sys[2], cut.safeInsetRight),
                        max(sys[3], cut.safeInsetBottom),
                    )
                }
            }
            else -> {
                @Suppress("DEPRECATION")
                intArrayOf(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom,
                )
            }
        }

    DisposableEffect(view) {
        val listener = View.OnApplyWindowInsetsListener { _, insets ->
            insetsPx = toMergedSystemBarsAndCutoutPx(insets)
            insets
        }
        view.setOnApplyWindowInsetsListener(listener)
        view.requestApplyInsets()

        val rootInsets = view.rootWindowInsets
        if (rootInsets != null) {
            insetsPx = toMergedSystemBarsAndCutoutPx(rootInsets)
        }

        onDispose {
            view.setOnApplyWindowInsetsListener(null)
        }
    }

    return with(density) {
        SystemInsetsDp(
            left = insetsPx[0].toDp(),
            top = insetsPx[1].toDp(),
            right = insetsPx[2].toDp(),
            bottom = insetsPx[3].toDp(),
        )
    }
}

fun SystemInsetsDp.asPaddingValues(extra: Dp = 0.dp): PaddingValues =
    PaddingValues(
        start = left + extra,
        top = top + extra,
        end = right + extra,
        bottom = bottom + extra,
    )

/**
 * Adds a second full **top** inset (doubles the merged top). Preview chrome uses
 * [asPaddingValues] instead; see `docs/preview-chrome-layout-style-guide.md`. Retained for any
 * legacy or special routes that still want extra top clearance.
 */
fun SystemInsetsDp.asPaddingValuesWithExtraTopBarBand(): PaddingValues =
    PaddingValues(
        start = left,
        top = top + top,
        end = right,
        bottom = bottom,
    )

