package dev.pointandshoot

import java.util.UUID

/**
 * Plans a single exposure bracket per BUILD_PLAN §4 (Phase 1):
 * "Exposure bracketing (BKT): 3/5/7 RAW12 sequences with GroupingID metadata".
 *
 * The plan is intentionally engine-agnostic - it produces ordered EV offsets
 * (relative to the metered exposure) plus a stable [groupingId] so the output
 * files can be re-grouped in post-processing. The capture engine maps each
 * `BracketStop.evOffset` to `CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION`
 * (scaled by the camera's `CONTROL_AE_COMPENSATION_STEP`).
 *
 * Pure data + pure functions = trivially unit-testable.
 */
data class BracketPlan(
    val groupingId: String,
    val stops: List<BracketStop>,
    val pattern: BracketPattern,
    val evStep: Double,
) {
    companion object {
        /**
         * Build a centered, monotonically-increasing bracket of [pattern.shotCount]
         * shots separated by [evStep] EV stops. The middle shot is always EV 0.
         * [groupingId] defaults to a fresh UUID; pass an explicit id only when
         * resuming or merging plans.
         */
        fun build(
            pattern: BracketPattern,
            evStep: Double = 1.0,
            groupingId: String = newGroupingId(),
        ): BracketPlan {
            require(evStep > 0.0) { "evStep must be positive (was $evStep)" }
            val n = pattern.shotCount
            val half = n / 2
            val stops = (0 until n).map { i ->
                val ev = (i - half) * evStep
                BracketStop(
                    indexInBurst = i,
                    evOffset = ev,
                    bracketGroupingId = groupingId,
                    isReference = (i == half),
                )
            }
            return BracketPlan(
                groupingId = groupingId,
                stops = stops,
                pattern = pattern,
                evStep = evStep,
            )
        }

        fun newGroupingId(): String = "bkt-" + UUID.randomUUID().toString().take(12)
    }
}


/** A single shot inside a bracket. EV offset is relative to the metered exposure. */
data class BracketStop(
    val indexInBurst: Int,
    val evOffset: Double,
    val bracketGroupingId: String,
    val isReference: Boolean,
)
