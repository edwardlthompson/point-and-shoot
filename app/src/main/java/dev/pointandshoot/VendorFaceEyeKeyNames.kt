package dev.pointandshoot

/**
 * Heuristic filters for **vendor-looking** Camera2 metadata key names that may relate to
 * face / eye detection, portrait, or subject tracking. OEMs use inconsistent naming; this is
 * discovery-only (see probe markdown + `face_meter_probe_*.json`).
 *
 * [isVendorishMetadataKeyName] mirrors the probe report’s “vendor-ish” partition
 * (`com.` / `org.` / literal `vendor` in the string).
 */
object VendorFaceEyeKeyNames {

    /** Substrings matched case-insensitively after [isVendorishMetadataKeyName] passes. */
    private val faceEyeTrackingSubstrings: List<String> =
        listOf(
            "face",
            "eye",
            "iris",
            "pupil",
            "eyelid",
            "blink",
            "gaze",
            "facetrack",
            "face_track",
            "facetracking",
            "face_detect",
            "facedetect",
            "fdetect",
            "eyetrack",
            "eye_track",
            "eyeaf",
            "eye_af",
            "portrait",
            "skin",
            "beauty",
            "selfie",
            "gesture",
            "smile",
            "landmark",
            "saliency",
            "subject",
            "tracking",
            "headpose",
            "head_pose",
            "poseest",
        )

    fun isVendorishMetadataKeyName(name: String): Boolean {
        val n = name.lowercase()
        return n.contains("com.") || n.contains("org.") || n.contains("vendor")
    }

    fun matchesFaceEyeTrackingName(name: String): Boolean {
        val n = name.lowercase()
        return faceEyeTrackingSubstrings.any { n.contains(it) }
    }

    /**
     * Vendor-ish key names whose text suggests face / eye / portrait / tracking.
     * Sorted, distinct.
     */
    fun namedFaceEyeTrackingVendorKeys(keys: Iterable<String>): List<String> =
        keys
            .asSequence()
            .filter { isVendorishMetadataKeyName(it) && matchesFaceEyeTrackingName(it) }
            .distinct()
            .sorted()
            .toList()
}
