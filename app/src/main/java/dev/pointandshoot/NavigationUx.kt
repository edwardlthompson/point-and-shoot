package dev.pointandshoot

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sprint **UX.2** — gesture vs 3-button nav heuristic + inset telemetry. */
enum class NavigationMode {
    Gesture,
    ThreeButton,
    Unknown,
}

data class NavigationUxSnapshot(
    val insets: SystemInsetsDp,
    val navigationMode: NavigationMode,
    val immersiveSystemBarsHidden: Boolean,
)

object NavigationUx {
    private const val TAG = "PNS.NavUx"

    /**
     * Reads framework `config_navBarInteractionMode` when present:
     * **0** = 3-button, **2** = gesture (Android 10+).
     */
    fun detectNavigationMode(context: Context): NavigationMode {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return NavigationMode.Unknown
        val res = context.resources
        val id = res.getIdentifier("config_navBarInteractionMode", "integer", "android")
        if (id == 0) return NavigationMode.Unknown
        return when (res.getInteger(id)) {
            2 -> NavigationMode.Gesture
            0 -> NavigationMode.ThreeButton
            else -> NavigationMode.Unknown
        }
    }

    fun logSnapshot(context: Context, snapshot: NavigationUxSnapshot) {
        val ins = snapshot.insets
        Log.i(
            TAG,
            "navUx mode=${snapshot.navigationMode.name} " +
                "insetsL=${ins.left.value.toInt()} T=${ins.top.value.toInt()} " +
                "R=${ins.right.value.toInt()} B=${ins.bottom.value.toInt()} " +
                "immersiveHidden=${snapshot.immersiveSystemBarsHidden}",
        )
        PnsAdbLog.i(
            context,
            "navUx mode=${snapshot.navigationMode.name} bottomDp=${ins.bottom.value.toInt()} " +
                "immersive=${snapshot.immersiveSystemBarsHidden}",
        )
    }
}

@Composable
fun rememberNavigationUxSnapshot(immersiveSystemBarsHidden: Boolean = true): NavigationUxSnapshot {
    val context = LocalContext.current
    val insets = rememberSystemInsetsDp()
    val mode = remember(context) { NavigationUx.detectNavigationMode(context) }
    val snapshot =
        remember(insets, mode, immersiveSystemBarsHidden) {
            NavigationUxSnapshot(
                insets = insets,
                navigationMode = mode,
                immersiveSystemBarsHidden = immersiveSystemBarsHidden,
            )
        }
    LaunchedEffect(snapshot) {
        NavigationUx.logSnapshot(context, snapshot)
    }
    return snapshot
}

/**
 * Reserves the bottom band for in-app shutter / tray controls so system back/home gestures
 * do not steal taps (API 29+). Cleared on dispose.
 */
@Composable
fun PnsGestureExclusionBottomBand(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bottomFraction: Float = 0.24f,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    var layoutSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    DisposableEffect(enabled, view) {
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.systemGestureExclusionRects = emptyList()
            }
        }
    }

    LaunchedEffect(enabled, layoutSize, bottomFraction) {
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@LaunchedEffect
        if (layoutSize.height <= 0 || layoutSize.width <= 0) return@LaunchedEffect
        val bottomPx = (layoutSize.height * bottomFraction).toInt().coerceAtLeast(1)
        val rect =
            Rect(
                0,
                layoutSize.height - bottomPx,
                layoutSize.width,
                layoutSize.height,
            )
        view.systemGestureExclusionRects = listOf(rect)
        Log.d("PNS.NavUx", "gestureExclusion bottomPx=$bottomPx h=${layoutSize.height}")
    }

    Box(
        modifier =
            modifier.onGloballyPositioned { coordinates ->
                layoutSize = coordinates.size
            },
    ) {
        content()
    }
}

/** Bottom inset for chrome tray when system bars are visible (transient swipe). */
fun SystemInsetsDp.bottomTrayPadding(extra: Dp = 0.dp): Dp = bottom + extra
