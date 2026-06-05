package dev.pointandshoot

/**
 * Pure strict-HFR policy for Milestone 24 — route ladder, warmup gate, retry budget, mid-record recovery.
 * Host-testable without Camera2 session wiring.
 */
object StrictHfrPolicy {
    const val START_RETRY_BUDGET = 2
    const val HEALTH_WINDOW_MS = 2_500L
    const val MIN_WARMUP_FPS = 90.0
    const val MAX_STALL_MS = 1_200L
    const val HFR_THRESHOLD_FPS = 120
    const val UHD_ENCODE_WIDTH = 3840
    const val UHD_ENCODE_HEIGHT = 2160

    enum class RouteStep {
        INTERLEAVED_PRIMARY,
        INTERLEAVED_SUB4K,
        ENCODER_PRIORITY,
        EXHAUSTED,
    }

    enum class MidRecordOutcome {
        SUSTAINED,
        RECOVERED_ONCE,
        BLOCKED_UNSTABLE,
    }

    data class ConfigureFailState(
        val encoderOnlyActive: Boolean,
        val forceInterleaved: Boolean,
        val sub4kFallback: Boolean,
        val encoderPriorityTried: Boolean,
        val prefersInterleavedFor4k: Boolean,
    )

    data class ConfigureFailAction(
        val next: ConfigureFailState,
        val route: String,
        val exhausted: Boolean,
    )

    fun interleavedRouteLabel(
        encodeWidth: Int,
        encodeHeight: Int,
        hsWidth: Int,
        hsHeight: Int,
    ): String =
        if (isSub4kInterleaved(encodeWidth, encodeHeight, hsWidth, hsHeight)) {
            "interleaved_sub4k"
        } else {
            "interleaved_primary"
        }

    fun isSub4kInterleaved(
        encodeWidth: Int,
        encodeHeight: Int,
        hsWidth: Int,
        hsHeight: Int,
    ): Boolean {
        if (encodeWidth < UHD_ENCODE_WIDTH || encodeHeight < UHD_ENCODE_HEIGHT) return false
        return hsWidth < encodeWidth || hsHeight < encodeHeight
    }

    fun initialRoute(
        prefersInterleaved: Boolean,
        encodeWidth: Int,
        encodeHeight: Int,
        hsWidth: Int,
        hsHeight: Int,
    ): String =
        if (prefersInterleaved) {
            interleavedRouteLabel(encodeWidth, encodeHeight, hsWidth, hsHeight)
        } else {
            "encoder_priority"
        }

    /**
     * Bounded ladder: interleaved first → encoder-priority → sub-4K HS capture → exhausted.
     */
    fun nextConfigureFailAction(state: ConfigureFailState): ConfigureFailAction? {
        when {
            state.encoderOnlyActive && !state.forceInterleaved -> {
                return ConfigureFailAction(
                    next =
                        state.copy(
                            encoderOnlyActive = false,
                            forceInterleaved = true,
                            encoderPriorityTried = false,
                        ),
                    route = "interleaved_fallback",
                    exhausted = false,
                )
            }
            state.forceInterleaved && !state.encoderPriorityTried && state.prefersInterleavedFor4k -> {
                return ConfigureFailAction(
                    next =
                        state.copy(
                            forceInterleaved = false,
                            encoderPriorityTried = true,
                        ),
                    route = "encoder_priority",
                    exhausted = false,
                )
            }
            state.encoderPriorityTried && !state.sub4kFallback -> {
                return ConfigureFailAction(
                    next =
                        state.copy(
                            sub4kFallback = true,
                            forceInterleaved = false,
                        ),
                    route = "interleaved_sub4k",
                    exhausted = false,
                )
            }
            !state.sub4kFallback &&
                state.prefersInterleavedFor4k &&
                !state.forceInterleaved -> {
                return ConfigureFailAction(
                    next = state.copy(forceInterleaved = true),
                    route = "interleaved_fallback",
                    exhausted = false,
                )
            }
            !state.sub4kFallback -> {
                return ConfigureFailAction(
                    next =
                        state.copy(
                            sub4kFallback = true,
                            forceInterleaved = false,
                            encoderPriorityTried = false,
                        ),
                    route = "interleaved_sub4k",
                    exhausted = false,
                )
            }
            else -> return null
        }
    }

    fun isWarmupHealthy(
        desiredFps: Int,
        sessionReady: Boolean,
        configurePending: Boolean,
        stallMs: Double,
        smoothedFps: Double,
    ): Boolean {
        if (desiredFps < HFR_THRESHOLD_FPS) return false
        if (!sessionReady || configurePending) return false
        return stallMs <= MAX_STALL_MS && smoothedFps >= MIN_WARMUP_FPS
    }

    sealed class StrictStartDecision {
        data object Allow : StrictStartDecision()

        data class Retry(val retriesRemaining: Int) : StrictStartDecision()

        data class Block(val reason: String) : StrictStartDecision()
    }

    fun evaluateStrictStart(
        desiredFps: Int,
        effectiveFps: Int,
        warmupHealthy: Boolean,
        retryBudgetRemaining: Int,
        recoveryCapActive: Boolean,
    ): StrictStartDecision {
        if (desiredFps < HFR_THRESHOLD_FPS) return StrictStartDecision.Allow
        if (effectiveFps < HFR_THRESHOLD_FPS) {
            val reason = if (recoveryCapActive) "recovery_cap" else "effective_fps"
            return StrictStartDecision.Block(reason)
        }
        if (warmupHealthy) return StrictStartDecision.Allow
        return if (retryBudgetRemaining > 0) {
            StrictStartDecision.Retry(retryBudgetRemaining - 1)
        } else {
            StrictStartDecision.Block("warmup_unhealthy")
        }
    }

    fun midRecordRecoveryAllowed(alreadyUsed: Boolean): Boolean = !alreadyUsed

    fun classifyMidRecordOutcome(
        recovered: Boolean,
        recordingStoppedCleanly: Boolean,
    ): MidRecordOutcome =
        when {
            recordingStoppedCleanly && !recovered -> MidRecordOutcome.SUSTAINED
            recovered -> MidRecordOutcome.RECOVERED_ONCE
            else -> MidRecordOutcome.BLOCKED_UNSTABLE
        }
}
