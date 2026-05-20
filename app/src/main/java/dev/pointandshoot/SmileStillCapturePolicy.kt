package dev.pointandshoot

import java.util.concurrent.atomic.AtomicLong

/**
 * Sprint **13V.17** — smile-triggered still when ML Kit reports high smile probability.
 *
 * Cooldown prevents burst firing while the subject holds a smile.
 */
object SmileStillCapturePolicy {
    const val SMILE_PROBABILITY_THRESHOLD = 0.85f
    private const val COOLDOWN_MS = 4_500L

    private val lastTriggerWallMs = AtomicLong(0L)

    fun shouldTrigger(smilingProbability: Float): Boolean {
        if (smilingProbability < SMILE_PROBABILITY_THRESHOLD) return false
        val now = System.currentTimeMillis()
        val prev = lastTriggerWallMs.get()
        if (now - prev < COOLDOWN_MS) return false
        return lastTriggerWallMs.compareAndSet(prev, now)
    }

    fun resetCooldown() {
        lastTriggerWallMs.set(0L)
    }
}
