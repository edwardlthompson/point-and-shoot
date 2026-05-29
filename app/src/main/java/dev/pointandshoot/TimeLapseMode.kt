package dev.pointandshoot

/**
 * Sprint **15.27** — intervalometer output mode.
 *
 * [Off] / [Photo]: timed stills via the normal shutter path.
 * [Video]: hardware JPEG frames → H.264 MP4 ([TimeLapseVideoEncoder]).
 */
enum class TimeLapseMode(val storageId: String, val label: String) {
    Off("off", "Off"),
    Photo("photo", "Photo"),
    Video("video", "Video"),
    ;

    companion object {
        fun fromStorage(id: String?): TimeLapseMode =
            entries.firstOrNull { it.storageId == id } ?: Off

        fun isTimelapseVideoSession(settings: HudSettings): Boolean =
            settings.timeLapseModeEnum() == Video &&
                settings.intervalometerRunning &&
                settings.intervalometerIntervalSec > 0
    }
}
