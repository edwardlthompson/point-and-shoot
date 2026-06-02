package dev.pointandshoot.fleet

import android.content.Context
import android.graphics.ImageFormat
import dev.pointandshoot.BackCameraRoleResolver

/**
 * Pluggable fleet policy (Milestone **16.4**).
 *
 * Default path: [GenericFleetPolicy]. [LegacyFleetPolicyPlugin] is opt-in via
 * [FleetPolicyPreferences].
 */
interface FleetDevicePolicy {
    val policyId: String?

    fun mergeRoles(
        enumerated: BackCameraRoleResolver.Roles,
        ids: List<String>,
    ): BackCameraRoleResolver.Roles

    fun applyProfileDefaults(profile: FleetCameraProfile): FleetCameraProfile

    fun logicalCameraId(ids: List<String>): String?

    fun leafRawFormatOrder(): List<Int>
}

/** Generic resolver output — no LegacyDevice-specific overrides. */
object GenericFleetPolicy : FleetDevicePolicy {
    override val policyId: String? = null

    override fun mergeRoles(
        enumerated: BackCameraRoleResolver.Roles,
        ids: List<String>,
    ): BackCameraRoleResolver.Roles = enumerated

    override fun applyProfileDefaults(profile: FleetCameraProfile): FleetCameraProfile = profile

    override fun logicalCameraId(ids: List<String>): String? =
        ids.firstOrNull { it == "0" }

    override fun leafRawFormatOrder(): List<Int> = DEFAULT_LEAF_RAW_FORMAT_ORDER

    val DEFAULT_LEAF_RAW_FORMAT_ORDER: List<Int> =
        listOf(
            ImageFormat.RAW12,
            ImageFormat.RAW_SENSOR,
            ImageFormat.RAW10,
            ImageFormat.RAW_PRIVATE,
        )
}

/** Legacy Legacy device plugin — active only when [FleetPolicyPreferences.legacyOp13Enabled]. */
object LegacyFleetPolicyPlugin : FleetDevicePolicy {
    override val policyId: String? = LegacyFleetPolicy.POLICY_ID

    override fun mergeRoles(
        enumerated: BackCameraRoleResolver.Roles,
        ids: List<String>,
    ): BackCameraRoleResolver.Roles = LegacyFleetPolicy.mergeRoles(enumerated, ids)

    override fun applyProfileDefaults(profile: FleetCameraProfile): FleetCameraProfile =
        LegacyFleetPolicy.applyProfileDefaults(profile)

    override fun logicalCameraId(ids: List<String>): String? =
        LegacyFleetPolicy.logicalCameraId(ids)

    override fun leafRawFormatOrder(): List<Int> = LegacyFleetPolicy.leafRawFormatOrder()
}

object FleetDevicePolicySelector {
    fun active(context: Context): FleetDevicePolicy {
        if (FleetPolicyPreferences.legacyOp13Enabled(context) && LegacyFleetPolicy.appliesToDevice()) {
            return LegacyFleetPolicyPlugin
        }
        return GenericFleetPolicy
    }
}
