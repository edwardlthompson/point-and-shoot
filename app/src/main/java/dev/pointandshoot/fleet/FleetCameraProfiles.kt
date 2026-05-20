package dev.pointandshoot.fleet

import android.content.Context
import android.hardware.camera2.CameraManager
import dev.pointandshoot.BackCameraRoleResolver

/**
 * Entry point for fleet per-camera profiles (Milestone **13.2**).
 */
object FleetCameraProfiles {

    @Volatile
    private var memoryCache: FleetProfilesSnapshot? = null

    fun snapshot(context: Context, forceRescan: Boolean = false): FleetProfilesSnapshot {
        if (!forceRescan) {
            memoryCache?.let { return it }
            FleetCameraProfileStore.load(context)?.let {
                memoryCache = it
                return it
            }
        }
        val built = FleetCameraProfileBuilder.buildSnapshot(context)
        FleetCameraProfileStore.save(context, built)
        memoryCache = built
        return built
    }

    fun profileForCameraId(context: Context, cameraId: String): FleetCameraProfile? =
        snapshot(context).profile(cameraId)

    fun resolvedRoles(cm: CameraManager, ids: List<String>): BackCameraRoleResolver.Roles {
        val enumerated = BackCameraRoleResolver.resolveEnumerated(cm, ids)
        return OnePlus13FleetPolicy.mergeRoles(enumerated, ids)
    }

    fun resolvedRoles(context: Context, ids: List<String>): BackCameraRoleResolver.Roles {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return resolvedRoles(cm, ids)
    }

    fun invalidateMemoryCache() {
        memoryCache = null
    }
}
