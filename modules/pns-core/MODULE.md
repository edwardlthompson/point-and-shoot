# :pns-core

**Gradle:** `modules/pns-core` · **Package:** `dev.pointandshoot` (shared types)

## Role

Shared capability types, focal routing, fleet DNG policy registry, and logging with **no** Camera2 session wiring.

## Contents

- `CapabilityGate`, `Feature`, `HardwareCaps`, `GateResult`
- `PnsLog`, `RootCapability`, `RootCapabilityStore`
- **`LeafDngFleetPolicy`**, **`LeafDngFleetPolicies`**, **`StillDngBackend`**
- **`BackCameraRoleResolver`**, **`FleetRoleMergeHook`**, **`FocalMmSlot`**, **`FocalMode`**
- **`CommandDialMode`**, **`ReadoutAeCoupling`**, **`PreviewDynamicRangeLabels`**, **`PreviewVideoConstants`**
- `SessionConfigurationCompat`, `VideoEncodeLane`, `BracketPattern`, `PnsSweepSignals`

## Dependencies

- AndroidX Core KTX
- No dependency on `:pns-fleet`, `:pns-capture`, `:pns-preview`, or `:app`

## Tests

`src/test` — gate, log, `BackCameraRoleResolverTest`, focal routing

## Gates

`pns_verify_toolchain.ps1 -RunTests` (Tier 2)
