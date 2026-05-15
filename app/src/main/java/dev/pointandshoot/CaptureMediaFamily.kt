package dev.pointandshoot

/**
 * Photo vs video capture intent for chrome behavior (BUILD_PLAN Sprint 10.10 `CaptureMediaFamily`).
 * Tray **Photo | Video** toggles map to [Photo] vs [Video]; [fromPrimaryPhoto] mirrors legacy `primaryPhoto`.
 */
enum class CaptureMediaFamily {
    Photo,
    Video,
    ;

    companion object {
        fun fromPrimaryPhoto(primaryPhoto: Boolean): CaptureMediaFamily =
            if (primaryPhoto) Photo else Video

        /** Modes shown in the Mode dial when the tray is in video capture (no RAW bracket / highlight metering). */
        fun commandDialModesFor(family: CaptureMediaFamily): List<CommandDialMode> =
            when (family) {
                Photo -> CommandDialMode.entries
                Video -> listOf(CommandDialMode.Auto, CommandDialMode.M, CommandDialMode.S)
            }
    }
}
