package dev.pointandshoot

import android.graphics.Rect
import android.util.Log
import kotlin.math.abs

/**
 * Sprint **15.28** — EMA-smoothed [android.hardware.camera2.CaptureRequest.SCALER_CROP_REGION]
 * nudge while manual-focus racks on tele slots (M dial).
 *
 * When focus moves closer (diopters increase), lenses often narrow FOV (“breathing”).
 * Expand the crop rect slightly: target scale = 1 + (Δdiopters × k).
 */
object FocusBreathingCompensator {

    const val TAG = "PNS.FocusBreathing"
    private const val DIOPTER_DELTA_THRESHOLD = 0.3f
    private const val EMA_ALPHA = 0.18f
    private const val MAX_SCALE = 1.12f
    private const val LOG_MIN_INTERVAL_MS = 400L

    data class Config(
        val active: Boolean,
        val k: Float,
    )

    private var lastDiopters: Float? = null
    private var emaScale = 1f
    private var lastLogMs = 0L

    fun reset() {
        lastDiopters = null
        emaScale = 1f
    }

    fun currentScale(): Float = emaScale

    /**
     * @return true when [emaScale] changed enough to warrant refreshing the repeating preview request.
     */
    fun onFocusDistance(diopters: Float?, config: Config): Boolean {
        if (!config.active) {
            val hadState = emaScale != 1f || lastDiopters != null
            reset()
            return hadState
        }
        val d = diopters?.takeIf { it.isFinite() && it >= 0f } ?: return false
        val prev = lastDiopters
        lastDiopters = d
        if (prev == null) return false
        val delta = d - prev
        if (abs(delta) < DIOPTER_DELTA_THRESHOLD) return false
        val k = config.k.coerceIn(0.0005f, 0.05f)
        val targetScale = (1f + delta * k).coerceIn(1f, MAX_SCALE)
        val before = emaScale
        emaScale = (EMA_ALPHA * targetScale + (1f - EMA_ALPHA) * emaScale).coerceIn(1f, MAX_SCALE)
        val now = System.currentTimeMillis()
        if (now - lastLogMs >= LOG_MIN_INTERVAL_MS) {
            lastLogMs = now
            logBreathing(
                "breathing diopters=${"%.3f".format(d)} delta=${"%.3f".format(delta)} " +
                    "emaScale=${"%.4f".format(emaScale)} k=${"%.4f".format(k)}",
            )
        }
        return abs(emaScale - before) > 0.0001f
    }

    /** Expand [base] about its center, clamped to [active]. */
    fun applyScaleToCrop(base: Rect, active: Rect, scale: Float): Rect {
        if (scale <= 1.001f || base.width() <= 0 || base.height() <= 0) return base
        val cx = base.centerX()
        val cy = base.centerY()
        var w = (base.width() * scale).toInt().coerceIn(base.width(), active.width())
        var h = (base.height() * scale).toInt().coerceIn(base.height(), active.height())
        var l = cx - w / 2
        var t = cy - h / 2
        if (l < active.left) l = active.left
        if (t < active.top) t = active.top
        if (l + w > active.right) l = (active.right - w).coerceAtLeast(active.left)
        if (t + h > active.bottom) t = (active.bottom - h).coerceAtLeast(active.top)
        w = w.coerceAtMost(active.right - l)
        h = h.coerceAtMost(active.bottom - t)
        if (w < 1 || h < 1) return base
        return Rect(l, t, l + w, t + h)
    }

    private fun logBreathing(message: String) {
        runCatching { Log.i(TAG, message) }
    }
}
