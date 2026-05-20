package dev.pointandshoot

/**
 * In-app video encode lane (Milestone **13.6**).
 * **Raw** uses [RawVideoRecordingController] + [RawVideoWriter] (no [android.media.MediaRecorder]).
 */
enum class VideoEncodeLane {
    Encoded,
    Raw,
}
