package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.DynamicRangeProfiles
import android.media.MediaCodecInfo
import android.os.Build
import android.os.PowerManager
import java.io.ByteArrayOutputStream

/**
 * Runtime-safe accessors for APIs above [Build.VERSION_CODES.P] (minSdk 28).
 *
 * Referencing SDK-only static fields (e.g. [CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID],
 * [PowerManager.THERMAL_STATUS_MODERATE]) on older devices throws [NoSuchFieldError] when the
 * containing method runs — lint suppressions are not enough.
 */
object ApiLevelGuards {

    /** Matches [CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID] without field lookup on API 28. */
    const val LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID_NAME =
        "android.logical.multiCamera.activePhysicalId"

    /** [PowerManager.THERMAL_STATUS_*] values (API 29+); safe literals for API 28. */
    const val THERMAL_STATUS_NONE = 0
    const val THERMAL_STATUS_LIGHT = 1
    const val THERMAL_STATUS_MODERATE = 2
    const val THERMAL_STATUS_SEVERE = 3
    const val THERMAL_STATUS_CRITICAL = 4
    const val THERMAL_STATUS_EMERGENCY = 5
    const val THERMAL_STATUS_SHUTDOWN = 6
}

fun MediaCodecInfo.isCodecAlias(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isAlias

fun MediaCodecInfo.isNonAliasEncoder(): Boolean =
    isEncoder && !isCodecAlias()

fun PowerManager.currentThermalStatusCompat(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        currentThermalStatus
    } else {
        ApiLevelGuards.THERMAL_STATUS_NONE
    }

fun CaptureResult.getLogicalMultiCameraActivePhysicalIdOrNull(): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    return get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)
}

fun TotalCaptureResult.getPhysicalCameraTotalResultsOrNull(): Map<String, TotalCaptureResult>? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return runCatching { physicalCameraTotalResults }.getOrNull()
}

fun CameraCharacteristics.getAvailableDynamicRangeProfilesOrNull(): DynamicRangeProfiles? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    @Suppress("NewApi")
    return get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES) as? DynamicRangeProfiles
}

fun CameraCharacteristics.getRecommendedTenBitDynamicRangeProfileOrNull(): Long? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
    @Suppress("NewApi")
    return get(CameraCharacteristics.REQUEST_RECOMMENDED_TEN_BIT_DYNAMIC_RANGE_PROFILE)
}

/** [CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_MODES] — API 36+ on device framework. */
fun CameraCharacteristics.getColorCorrectionAvailableModesOrEmpty(): IntArray {
    if (Build.VERSION.SDK_INT < 36) return intArrayOf()
    @Suppress("NewApi")
    return get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_MODES) ?: intArrayOf()
}

fun CameraCharacteristics.getScalerAvailableStreamUseCasesOrNull(): LongArray? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    @Suppress("NewApi")
    return get(CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES)
}

fun CameraCharacteristics.getInfoSessionConfigurationQueryVersionOrNull(): Int? {
    if (Build.VERSION.SDK_INT < 35) return null
    @Suppress("NewApi")
    return get(CameraCharacteristics.INFO_SESSION_CONFIGURATION_QUERY_VERSION)
}

fun ByteArrayOutputStream.writeBytesCompat(bytes: ByteArray) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        writeBytes(bytes)
    } else {
        write(bytes)
    }
}
