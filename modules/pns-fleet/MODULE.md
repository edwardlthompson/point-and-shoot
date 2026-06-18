# :pns-fleet

**Gradle:** `modules/pns-fleet` · **Package:** `dev.pointandshoot.fleet`

## Role

Fleet capability matrix schema, catalog registry, parity **pure** types, session probe core, leaderboard helpers.

## Shipped in module

Matrix schema/validator/diff, catalog expansion, parity sweep models, `SessionMatrixProbeCore`, `ProductHardwareLaunchScan`, `FleetCameraProfileStore`, leaderboard slug/readiness.

## Remains in `:app`

Hub Compose (`FleetMatrixHubScreen`, `FleetParityModeSheet`, `FleetDeviceMatrixCatalogAttach`), `FleetDeviceMatrixBuilder` / `Store`, `FleetUiVisibilityGate`, `LegacyFleetPolicy`, live Camera2 / encoder probes.

## Dependencies

- `:pns-core` only

## Gates

`pns_fleet_regression_pack.ps1 -HostOnly` · optional USB `pns_fleet_matrix_scan.ps1`
