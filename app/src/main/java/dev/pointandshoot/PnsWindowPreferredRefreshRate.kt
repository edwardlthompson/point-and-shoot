package dev.pointandshoot

import android.os.Build
import android.view.Display
import androidx.activity.ComponentActivity

/**
 * Requests a window refresh rate up to [maxHz] using the highest mode the display advertises
 * at or below that cap (honors system / OEM caps when they do not expose higher modes).
 */
object PnsWindowPreferredRefreshRate {

    fun applyUpTo(activity: ComponentActivity, maxHz: Float = 120f) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val display = activity.display ?: return
        val rate = bestRefreshRateHzUpTo(display, maxHz)
        val w = activity.window
        val attrs = w.attributes
        if (kotlin.math.abs(attrs.preferredRefreshRate - rate) < 0.01f) return
        attrs.preferredRefreshRate = rate
        w.attributes = attrs
    }

    fun bestRefreshRateHzUpTo(display: Display, maxHz: Float): Float {
        var best = 0f
        for (mode in display.supportedModes) {
            val r = mode.refreshRate
            if (r <= maxHz + 0.01f && r > best) best = r
        }
        if (best <= 0f) {
            return display.refreshRate.coerceIn(1f, maxHz)
        }
        return best
    }
}
