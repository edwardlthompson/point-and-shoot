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
        /** Sprint **15.31** — macro video caps preview/record fps for stable EIS on UW. */
        const val MACRO_VIDEO_MAX_FPS = 60

        fun fromPrimaryPhoto(primaryPhoto: Boolean): CaptureMediaFamily =
            if (primaryPhoto) Photo else Video

        /** Video tray + **MACRO** dial (Sprint **15.31**). */
        fun isMacroVideo(primaryPhoto: Boolean, dial: CommandDialMode): Boolean =
            !primaryPhoto && dial == CommandDialMode.Macro

        fun isMacroVideo(family: CaptureMediaFamily, dial: CommandDialMode): Boolean =
            family == Video && dial == CommandDialMode.Macro

        /** Modes shown in the Mode dial when the tray is in video capture (no RAW bracket / highlight metering). */
        fun commandDialModesFor(family: CaptureMediaFamily): List<CommandDialMode> =
            when (family) {
                Photo ->
                    buildList {
                        addAll(
                            CommandDialMode.entries.filter {
                                it != CommandDialMode.Qr && it != CommandDialMode.Dual
                            },
                        )
                        add(CommandDialMode.Qr)
                    }
                Video ->
                    listOf(
                        CommandDialMode.Auto,
                        CommandDialMode.M,
                        CommandDialMode.S,
                        CommandDialMode.Macro,
                        CommandDialMode.Dual,
                    )
            }

        /** Sprint **14.3** — section title for the shooting-mode dropdown ([PreviewCommandDialDropdownMenu]). */
        fun commandDialMenuSectionTitle(family: CaptureMediaFamily): String =
            when (family) {
                Photo -> "Photo programs"
                Video -> "Video programs"
            }
    }
}
