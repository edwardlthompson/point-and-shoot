package dev.pointandshoot

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Sprint **13V.17** / **14.9** — smile-triggered still when ML Kit reports high smile probability.
 *
 * Runs on every YUV analysis frame while enabled (photo mode, YUV attached). [shouldTrigger] applies
 * a short consecutive-frame gate plus capture cooldown.
 */
object SmileStillCapturePolicy {
    /** ML Kit smiling probability in [0, 1] for the largest face in frame. */
    const val SMILE_PROBABILITY_THRESHOLD = 0.70f

    /** Below this, the consecutive high-smile counter resets. */
    private const val SMILE_RESET_THRESHOLD = 0.45f

    /** One high-probability frame is enough when ML Kit is already throttled (~8–100 ms). */
    private const val CONSECUTIVE_HIGH_FRAMES_REQUIRED = 1

    private const val COOLDOWN_MS = 4_500L

    private val lastTriggerWallMs = AtomicLong(0L)
    private val consecutiveHighFrames = AtomicInteger(0)

    fun shouldTrigger(smilingProbability: Float): Boolean {
        if (smilingProbability < SMILE_RESET_THRESHOLD) {
            consecutiveHighFrames.set(0)
            return false
        }
        if (smilingProbability < SMILE_PROBABILITY_THRESHOLD) {
            return false
        }
        val streak = consecutiveHighFrames.incrementAndGet()
        if (streak < CONSECUTIVE_HIGH_FRAMES_REQUIRED) return false
        val now = System.currentTimeMillis()
        val prev = lastTriggerWallMs.get()
        if (now - prev < COOLDOWN_MS) return false
        if (!lastTriggerWallMs.compareAndSet(prev, now)) return false
        consecutiveHighFrames.set(0)
        return true
    }

    fun resetCooldown() {
        lastTriggerWallMs.set(0L)
        consecutiveHighFrames.set(0)
    }
}
