package dev.pointandshoot

/** App registers [dev.pointandshoot.fleet.LegacyFleetPolicy.mergeRoles] at startup. */
object FleetRoleMergeHook {
    var mergeRoles: (BackCameraRoleResolver.Roles, List<String>) -> BackCameraRoleResolver.Roles =
        { enumerated, _ -> enumerated }
}
