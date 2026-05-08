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

    fun toSystemBarsInsetsPx(insets: WindowInsets): IntArray =
        if (Build.VERSION.SDK_INT >= 30) {
            val i = insets.getInsets(WindowInsets.Type.systemBars())
            intArrayOf(i.left, i.top, i.right, i.bottom)
        } else {
            @Suppress("DEPRECATION")
            intArrayOf(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom,
            )
        }

    DisposableEffect(view) {
        val listener = View.OnApplyWindowInsetsListener { _, insets ->
            insetsPx = toSystemBarsInsetsPx(insets)
            insets
        }
        view.setOnApplyWindowInsetsListener(listener)
        view.requestApplyInsets()

        val rootInsets = view.rootWindowInsets
        if (rootInsets != null) {
            insetsPx = toSystemBarsInsetsPx(rootInsets)
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

