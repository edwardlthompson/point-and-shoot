package dev.pointandshoot

import android.Manifest

/**
 * Ordered runtime-permission steps for [WelcomePermissionsScreen].
 *
 * **When you add a dangerous `uses-permission` to AndroidManifest.xml, add a matching
 * [WelcomeRuntimePermissionStep] here** so users see an explanation before the system dialog.
 * Steps are requested one at a time in list order (after the intro card).
 */
data class WelcomeRuntimePermissionStep(
    val permission: String,
    val title: String,
    val rationaleBody: String,
    /** If false, the user can continue the flow and enter the app without granting this permission. */
    val requiredToEnterApp: Boolean = true,
)

object WelcomeFlowConfig {
    val runtimePermissionSteps: List<WelcomeRuntimePermissionStep> =
        listOf(
            WelcomeRuntimePermissionStep(
                permission = Manifest.permission.CAMERA,
                title = "Camera access",
                rationaleBody =
                    "The camera is used for the live preview, still capture, and video recording. " +
                        "Frames are processed on your device. We do not upload your photos or video.",
                requiredToEnterApp = true,
            ),
            WelcomeRuntimePermissionStep(
                permission = Manifest.permission.RECORD_AUDIO,
                title = "Microphone",
                rationaleBody =
                    "Audio is recorded only when you record video with sound. " +
                        "Nothing is uploaded; it is stored like your other captures in your gallery folder.",
                requiredToEnterApp = false,
            ),
            WelcomeRuntimePermissionStep(
                permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
                title = "Storage",
                rationaleBody =
                    "On older Android versions, saving videos to your gallery folder needs storage access. " +
                        "Newer devices use scoped storage and do not show this step.",
                requiredToEnterApp = false,
            ),
            WelcomeRuntimePermissionStep(
                permission = Manifest.permission.ACCESS_FINE_LOCATION,
                title = "Location",
                rationaleBody =
                    "Optional: when you enable “Save location” in preview options, we attach GPS coordinates " +
                        "to new photos and videos so albums and desktop tools can show where they were taken. " +
                        "You can leave this off and still use the camera.",
                requiredToEnterApp = false,
            ),
        )
}
