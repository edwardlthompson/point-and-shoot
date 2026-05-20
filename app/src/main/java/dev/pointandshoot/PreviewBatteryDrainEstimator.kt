package dev.pointandshoot

/**
 * Rolling-window battery drain estimate for Sprint **13V.12** (pure JVM — unit tested).
 */
object PreviewBatteryDrainEstimator {
    data class Sample(val levelPct: Int, val elapsedMs: Long)

    /** Minimum span between first and last sample before emitting a rate. */
    const val MIN_WINDOW_MS = 30_000L

    /**
     * Estimated drain in **percent per hour** (positive = battery dropping).
     * Returns null when there are fewer than two samples or the window is too short.
     */
    fun estimateDrainPctPerHour(samples: List<Sample>): Float? {
        if (samples.size < 2) return null
        val first = samples.first()
        val last = samples.last()
        val dtMs = last.elapsedMs - first.elapsedMs
        if (dtMs < MIN_WINDOW_MS) return null
        val deltaPct = first.levelPct - last.levelPct
        val hours = dtMs / 3_600_000f
        if (hours <= 0f) return null
        return deltaPct / hours
    }

    fun formatDrainPctPerHour(drainPctPerHour: Float?): String =
        when {
            drainPctPerHour == null -> "—"
            drainPctPerHour >= 0.05f -> "−%.0f%%/hr".format(drainPctPerHour)
            drainPctPerHour <= -0.05f -> "+%.0f%%/hr".format(-drainPctPerHour)
            else -> "~0%/hr"
        }
}
