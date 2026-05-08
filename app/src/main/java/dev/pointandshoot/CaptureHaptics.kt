package dev.pointandshoot

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.MainThread

/**
 * Sensor-stability haptic protocol from BUILD_PLAN §4 (Phase 1):
 *
 *   * Still capture: fire the electronic shutter, wait for the readout to
 *     complete, then fire a short tick **30 ms after** so the user's finger
 *     is no longer disturbing the sensor at the moment of exposure.
 *   * Video start/stop: **never** vibrate. The solid red [PnsColors.RecordRed]
 *     tally border is the only visual feedback (`VideoTallyOverlay`).
 *
 * The 30 ms delay is intentionally measured from *readout-complete*, not from
 * the user tap. Callers should invoke [scheduleStillTick] from the Camera2
 * `CaptureCallback.onCaptureCompleted` handler or an equivalent post-readout
 * signal so the tick lands after the sensor is done.
 */
class CaptureHaptics(private val appContext: Context) {

    private val vibrator: Vibrator? = run {
        val ctx = appContext.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Schedule the post-readout still-capture tick. Call this from your
     * `onCaptureCompleted` handler (or wherever you confirm the sensor read
     * has finished). The tick fires after [POST_READOUT_TICK_DELAY_MS].
     *
     * @return true if a haptic was scheduled, false if the device has no
     *   vibrator capability or vibrator is disabled.
     */
    @MainThread
    fun scheduleStillTick(): Boolean {
        val v = vibrator ?: return false
        if (!v.hasVibrator()) return false
        mainHandler.postDelayed({ runCatching { fireTick(v) } }, POST_READOUT_TICK_DELAY_MS)
        return true
    }

    /**
     * Cancel any pending still tick. Useful if the capture is aborted between
     * scheduling and firing. Safe to call when nothing is pending.
     */
    fun cancelPending() {
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun fireTick(v: Vibrator) {
        // Single short pulse - fast enough to feel like a shutter click but
        // weak enough not to be a "long press". Uses TICK predefined effect
        // when available (API 29+) and falls back to a 12 ms one-shot on
        // older devices.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val tick = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            v.vibrate(tick)
            return
        }
        val effect = VibrationEffect.createOneShot(12L, VibrationEffect.DEFAULT_AMPLITUDE)
        v.vibrate(effect)
    }

    companion object {
        const val POST_READOUT_TICK_DELAY_MS = 30L
    }
}
