package dev.pointandshoot

/**
 * Photo vs video capture intent for chrome behavior (BUILD_PLAN Sprint 10.10 `CaptureMediaFamily`).
 * Today this mirrors the bottom tray **primary** shutter: photo-primary vs video-primary.
 */
enum class CaptureMediaFamily {
    Photo,
    Video,
    ;

    companion object {
        fun fromPrimaryPhoto(primaryPhoto: Boolean): CaptureMediaFamily =
            if (primaryPhoto) Photo else Video
    }
}
