# Fleet Device Capability Matrix



**Milestone 16** — canonical per-device Camera2 + product capability artifact.



## Artifact



| Field | Value |

|-------|--------|

| On-device path | `files/fleet_device_matrix.json` |

| History | `files/fleet_device_matrix_history/<timestamp>.json` |

| Schema version | `1` (`FleetDeviceMatrix.SCHEMA_VERSION`) |

| Log tag | `PNS.FleetMatrix` |



## Scan tiers



| Tier | When | Contents |

|------|------|----------|

| **quick** | Diagnostics hub shallow scan (`buildProbeReport`) | Per-`cameraId` shallow JSON, fleet profiles, focal slots; appendix embeds shallow cache |

| **full** | Hub “Rescan full” / `FleetDeviceMatrixBuilder.buildFullAndSave` | Structured `cameras[]` with `featureGates`, `capabilitiesNormalized`, `rawReadiness`; appendix embeds `deepCaps` + `sessionMatrix`; `cameraX` slice; `runtimeProbes.sessionMatrixSummary`; `appendix.diffVsPrevious` |



## Invalidation



Matrix reloads when **build fingerprint prefix** or **`appVersionCode`** changes (see `FleetDeviceMatrixStore.loadValid`).



## Rescan playbook (16.3)



**When to rescan**



- App release (`appVersionCode` bump)

- OS / security patch (fingerprint change)

- Fleet PR touching Camera2 session, RAW, focal routing, or fleet policy

- User report of wrong HFR / RAW / face gating



**Procedure**



1. Install debug build on onboarded SKU (primary: **CPH2583**).

2. Run host gate or hub manually:

   - **Quick:** `.\scripts\pns_fleet_matrix_scan.ps1 -ScanTier quick`

   - **Full:** `.\scripts\pns_fleet_matrix_scan.ps1 -ScanTier full` (opens probehub + background full tier; ~4 min wait)

3. Compare vs previous: `.\scripts\pns_fleet_matrix_diff.ps1 -PathA … -PathB … -OutMarkdown …`

4. Triage `featureGates.*.sessionOk=false` before enabling UI that reads matrix (**16.6**).

5. Update row in `docs/FLEET_DEVICE_VERIFY_MATRIX.md` (**16.8**).



**Hub (manual)**



Engineering hub → **Device capability matrix** → **Quick refresh** or **Rescan full** → **Export JSON**.



**OP13 regression lane only**



Add `-LegacyOp13FleetPolicy` to scan script or `--ez pns_legacy_op13_fleet_policy true` on `am start`. See `docs/FLEET_ONEPLUS13_RAW_POLICY.md`.



## Host automation



```powershell

.\scripts\pns_fleet_matrix_scan.ps1

.\scripts\pns_fleet_matrix_scan.ps1 -ScanTier full

.\scripts\pns_fleet_matrix_diff.ps1 -PathA hfr-runs\fleet_matrix_a\fleet_device_matrix.json -PathB hfr-runs\fleet_matrix_b\fleet_device_matrix.json -OutMarkdown hfr-runs\fleet_matrix_diff.md

```



Shallow hub validate (`pns_shallow_scan_hub_validate.ps1`) also asserts matrix pull + `schemaVersion=1`.



## USB verification rows



Per-SKU checklists: **`docs/FLEET_DEVICE_VERIFY_MATRIX.md`**.



## Agents



See **`AGENTS.md`** — **CRITICAL — Fleet capability matrix** and **`.cursor/rules/fleet-generic-policy.mdc`**.


