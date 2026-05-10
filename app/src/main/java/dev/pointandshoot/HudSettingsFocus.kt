package dev.pointandshoot

/**
 * When opening [HudSettingsScreen] from preview readout chips, scroll the HUD list to the
 * relevant section.
 */
enum class HudSettingsFocus {
    None,
    /** ISO + shutter visibility toggles (combined row). */
    IsoShutterReadout,
    /** FPS readout visibility toggle. */
    FpsReadout,
    /** In-camera AWB summary (informational card). */
    WhiteBalanceInfo,
}
