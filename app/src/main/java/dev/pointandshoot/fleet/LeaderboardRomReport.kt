package dev.pointandshoot.fleet

import org.json.JSONObject

/** User-reported ROM lane for leaderboard submit (validated against detected heuristics). */
object LeaderboardRomReport {
    enum class Reported(val wire: String) {
        STOCK("stock"),
        LINEAGE("lineage"),
        OTHER_CUSTOM("other_custom"),
        UNSPECIFIED("unspecified"),
    }

    fun parse(wire: String?): Reported =
        when (wire?.lowercase()) {
            "stock" -> Reported.STOCK
            "lineage" -> Reported.LINEAGE
            "other_custom", "custom", "other" -> Reported.OTHER_CUSTOM
            else -> Reported.UNSPECIFIED
        }

    fun detectedFlavor(matrix: JSONObject): String = LeaderboardReadiness.detectedRomFlavor(matrix)

    /** True when report is absent or plausibly matches detected ROM heuristics. */
    fun isConsistent(reported: Reported, detected: String): Boolean =
        when (reported) {
            Reported.UNSPECIFIED -> true
            Reported.STOCK -> detected in setOf("stock", "root_unlocked", "engineering", "unknown")
            Reported.LINEAGE -> detected in setOf("custom_likely", "root_unlocked", "engineering")
            Reported.OTHER_CUSTOM -> detected in setOf("custom_likely", "root_unlocked", "engineering", "unknown")
        }
}
