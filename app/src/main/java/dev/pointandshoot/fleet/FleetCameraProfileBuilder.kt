package dev.pointandshoot.fleet

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Size
import dev.pointandshoot.BackCameraRoleResolver
import dev.pointandshoot.DcgModeSupport
import dev.pointandshoot.DeviceCameraCapabilityCache

/**
 * Builds [FleetCameraProfile] list from live [CameraManager] enumeration (Milestone **13.2**).
 */
object FleetCameraProfileBuilder {

    fun buildSnapshot(context: Context): FleetProfilesSnapshot {
        val app = context.applicationContext
        val cm = app.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ids = cm.cameraIdList.toList()
        val enumerated = BackCameraRoleResolver.resolveEnumerated(cm, ids)
        val policy = FleetDevicePolicySelector.active(app)
        val roles = policy.mergeRoles(enumerated, ids)
        val roleMap = roleMapFromResolver(roles, ids, cm)
        val profiles =
            ids.mapNotNull { id ->
                val cc = runCatching { cm.getCameraCharacteristics(id) }.getOrNull() ?: return@mapNotNull null
                val role = roleMap[id] ?: FleetCameraRole.UNKNOWN
                buildProfile(id, cc, role).let { policy.applyProfileDefaults(it) }
            }
        return FleetProfilesSnapshot(
            deviceModel = android.os.Build.MODEL,
            manufacturer = android.os.Build.MANUFACTURER,
            logicalCameraId = policy.logicalCameraId(ids),
            roleByCameraId = roleMap,
            profiles = profiles,
            policyId = policy.policyId,
            leafRawFormatOrder = policy.leafRawFormatOrder(),
        )
    }

    internal fun roleMapFromResolver(
        roles: BackCameraRoleResolver.Roles,
        ids: List<String>,
        cm: CameraManager,
    ): Map<String, FleetCameraRole> {
        val out = linkedMapOf<String, FleetCameraRole>()
        for (id in ids) {
            val cc = runCatching { cm.getCameraCharacteristics(id) }.getOrNull()
            out[id] = classifyId(id, cc, roles)
        }
        return out
    }

    internal fun classifyId(
        id: String,
        cc: CameraCharacteristics?,
        roles: BackCameraRoleResolver.Roles,
    ): FleetCameraRole {
        if (cc == null) {
            return when (id) {
                roles.ultraWide -> FleetCameraRole.ULTRA_WIDE
                roles.wide -> FleetCameraRole.WIDE
                roles.tele -> FleetCameraRole.TELE
                roles.longTele -> FleetCameraRole.LONG_TELE
                else -> FleetCameraRole.UNKNOWN
            }
        }
        val facing = cc.get(CameraCharacteristics.LENS_FACING)
        if (facing == CameraCharacteristics.LENS_FACING_FRONT) return FleetCameraRole.FRONT
        val physical = runCatching { cc.physicalCameraIds.toList() }.getOrDefault(emptyList())
        if (physical.isNotEmpty()) return FleetCameraRole.LOGICAL
        return when (id) {
            roles.ultraWide -> FleetCameraRole.ULTRA_WIDE
            roles.wide -> FleetCameraRole.WIDE
            roles.tele -> FleetCameraRole.TELE
            roles.longTele -> FleetCameraRole.LONG_TELE
            else -> FleetCameraRole.UNKNOWN
        }
    }

    private fun buildProfile(
        id: String,
        cc: CameraCharacteristics,
        role: FleetCameraRole,
    ): FleetCameraProfile {
        val map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val physical = runCatching { cc.physicalCameraIds.toList() }.getOrDefault(emptyList())
        val focal =
            cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty()
        val rawFormats = advertisedRawFormats(map)
        val shadingModes =
            cc.get(CameraCharacteristics.SHADING_AVAILABLE_MODES)?.toSet().orEmpty()
        val lensShadingModes =
            cc.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES)
                ?.toSet()
                .orEmpty()
        val lensShadingOnStill =
            lensShadingModes.contains(CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON)
        val arr = cc.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        val (hfrMax, _, _) = DeviceCameraCapabilityCache.hfrMaxAtSizeClasses(map)
        val leaf = physical.isEmpty() && role != FleetCameraRole.FRONT && role != FleetCameraRole.LOGICAL
        return FleetCameraProfile(
            cameraId = id,
            role = role,
            physicalCameraIds = physical,
            focalLengthsMm = focal,
            rawFormatsAdvertised = rawFormats,
            prefersRawSensor = leaf && ImageFormat.RAW_SENSOR in rawFormats,
            lensShadingMapOnStill = lensShadingOnStill,
            shadingModes = shadingModes,
            supportsDcgSession = DcgModeSupport.supportsDcgMode(cc),
            supportsRawVideo = leaf && rawFormats.isNotEmpty(),
            hfrMaxFps = hfrMax,
            activeArrayWidth = arr?.width() ?: 0,
            activeArrayHeight = arr?.height() ?: 0,
            largestRawSensorWxH = DeviceCameraCapabilityCache.largestSizeWxH(map, ImageFormat.RAW_SENSOR),
            largestRaw12WxH = DeviceCameraCapabilityCache.largestSizeWxH(map, ImageFormat.RAW12),
        )
    }

    private fun advertisedRawFormats(map: StreamConfigurationMap?): List<Int> {
        if (map == null) return emptyList()
        val formats =
            listOf(
                ImageFormat.RAW_SENSOR,
                ImageFormat.RAW10,
                ImageFormat.RAW12,
                ImageFormat.RAW_PRIVATE,
            )
        return formats.filter { fmt ->
            runCatching { map.getOutputSizes(fmt)?.isNotEmpty() == true }.getOrDefault(false)
        }
    }
}
