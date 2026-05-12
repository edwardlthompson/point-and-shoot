package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.params.DynamicRangeProfiles
import android.os.Build
import android.util.Log
import android.view.Surface

private const val TAG = "PNS.PreviewHdr"

/**
 * Picks a [android.hardware.camera2.params.OutputConfiguration.setDynamicRangeProfile] for the
 * first (preview) surface of a REGULAR multi-output session when the HAL advertises profiles and
 * [android.hardware.camera2.CameraDevice.isSessionConfigurationSupported] accepts the full surface
 * list (Milestone 4 / 10 HDR preview path).
 */
internal object PreviewHdrSessionSupport {

    /**
     * Prefer [CameraCharacteristics.REQUEST_RECOMMENDED_TEN_BIT_DYNAMIC_RANGE_PROFILE] when present,
     * then remaining supported IDs in stable ascending order. Exposed for JVM tests.
     */
    internal fun orderedDynamicRangeCandidates(
        recommendedTenBit: Long?,
        supportedProfiles: Collection<Long>,
    ): List<Long> =
        buildList {
            if (recommendedTenBit != null) add(recommendedTenBit)
            supportedProfiles.filter { it != recommendedTenBit }.sorted().forEach { add(it) }
        }

    /**
     * Returns a profile long to pass to [outputConfigurationsWithOptionalStreamUseCases], or null
     * when HDR preview should not be applied (API, prefs off, missing metadata, or no supported combo).
     */
    fun pickProfileForPreviewOutputsOrNull(
        device: CameraDevice,
        chars: CameraCharacteristics,
        outputSurfaces: List<Surface>,
        wantHdrPreview: Boolean,
    ): Long? {
        if (!wantHdrPreview) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        if (outputSurfaces.isEmpty()) return null
        val drp =
            runCatching {
                chars.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES) as? DynamicRangeProfiles
            }.getOrNull()
                ?: return null
        val supported = drp.supportedProfiles?.toList().orEmpty()
        if (supported.isEmpty()) return null
        val recTenBit: Long? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                runCatching {
                    @Suppress("NewApi")
                    chars.get(CameraCharacteristics.REQUEST_RECOMMENDED_TEN_BIT_DYNAMIC_RANGE_PROFILE)
                }.getOrNull()
            } else {
                null
            }
        val ordered = orderedDynamicRangeCandidates(recTenBit, supported)
        for (p in ordered) {
            if (isMultiOutputSessionSupportedWithDynamicRangeOnPreview(device, outputSurfaces, p)) {
                Log.d(TAG, "pickProfile profile=$p outputs=${outputSurfaces.size}")
                return p
            }
        }
        Log.d(TAG, "pickProfile no supported combo outputs=${outputSurfaces.size}")
        return null
    }
}
