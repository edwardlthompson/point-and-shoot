package dev.pointandshoot.fleet

/**
 * Rear (and front) camera role for fleet policy and focal routing.
 * Milestone **13.2** — see [FleetCameraProfile] and [LegacyFleetPolicy].
 */
enum class FleetCameraRole {
    ULTRA_WIDE,
    WIDE,
    TELE,
    LONG_TELE,
    FRONT,
    LOGICAL,
    UNKNOWN,
}
