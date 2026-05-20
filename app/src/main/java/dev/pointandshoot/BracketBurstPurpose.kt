package dev.pointandshoot

/**
 * Why a multi-frame AE bracket is running (Sprint **13.8c** HDR still vs dial **BKT**).
 */
enum class BracketBurstPurpose {
    /** Command dial **BKT** — user-selected [BracketPattern]. */
    BktDial,

    /** HUD / ADB **HdrStill** — fleet-capped EV bracket, burst of DNGs (no merge in MVP). */
    HdrStill,
}
