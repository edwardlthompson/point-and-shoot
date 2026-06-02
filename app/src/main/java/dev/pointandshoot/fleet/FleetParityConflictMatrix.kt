package dev.pointandshoot.fleet

/**
 * Advertised capability pairs that cannot coexist in one session (Milestone **21.9**).
 */
object FleetParityConflictMatrix {
    data class ConflictPair(
        val id: String,
        val leftId: String,
        val rightId: String,
        val note: String,
    )

    val pairs: List<ConflictPair> =
        listOf(
            ConflictPair("dual_hfr", "video.dual", "video.hfr", "Dual video + HFR >60 typically incompatible"),
            ConflictPair("raw_unpinned_pin", "raw.dng", "lens.tele", "RAW still + non-wide physical pin risk (metadata)"),
            ConflictPair("melt_pip", "video.multicam_melt", "preview.pip", "Multicam melt + concurrent PiP thermal/stream budget"),
            ConflictPair("raw_hevc_session", "video.raw", "video.hevc", "RAW video + HEVC same session stream budget"),
        )

    fun activeConflicts(advertisedIds: Set<String>): List<ConflictPair> =
        pairs.filter { it.leftId in advertisedIds && it.rightId in advertisedIds }
}
