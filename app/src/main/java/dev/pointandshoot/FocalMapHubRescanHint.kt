package dev.pointandshoot

import android.content.SharedPreferences
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * Non-blocking **“Calibrating focal map…”** readout hint (Milestone **10.2**): when the probe hub
 * bumps shallow-cache prefs and a valid JSON snapshot is not yet on disk for this fingerprint,
 * preview chrome can surface a short status line (see [PreviewReadoutStrip]).
 */
object FocalMapHubRescanHint {
    internal const val POLL_MS = 250L
}

@Composable
fun rememberFocalMapCalibratingHintVisible(): Boolean {
    val context = LocalContext.current
    val appCtx = context.applicationContext
    var focalMapHintUntilElapsed by remember { mutableStateOf(0L) }
    var focalMapHintTick by remember { mutableIntStateOf(0) }
    DisposableEffect(appCtx) {
        val p = ShallowCapabilityCacheStore.prefs(appCtx)
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == ShallowCapabilityCacheStore.KEY_RESCAN_SEQ ||
                    key == ShallowCapabilityCacheStore.KEY_JSON
                ) {
                    focalMapHintUntilElapsed =
                        SystemClock.elapsedRealtime() + FocalLensStripSupport.FOCAL_MAP_HINT_POST_RESCAN_MS
                }
            }
        p.registerOnSharedPreferenceChangeListener(listener)
        onDispose { p.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(focalMapHintUntilElapsed) {
        while (SystemClock.elapsedRealtime() < focalMapHintUntilElapsed) {
            delay(FocalMapHubRescanHint.POLL_MS)
            focalMapHintTick++
        }
    }
    return remember(focalMapHintUntilElapsed, focalMapHintTick) {
        SystemClock.elapsedRealtime() < focalMapHintUntilElapsed &&
            ShallowCapabilityCacheStore.loadValidCachedRoot(appCtx) == null
    }
}
