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



**legacy device regression lane only**



Add `-LegacyOp13FleetPolicy` to scan script or `--ez pns_legacy_legacy_fleet_policy true` on `am start`. See `docs/FLEET_ONEPLUS13_RAW_POLICY.md`.



## Host automation



```powershell

.\scripts\pns_fleet_matrix_scan.ps1

.\scripts\pns_fleet_matrix_scan.ps1 -ScanTier full

.\scripts\pns_fleet_matrix_diff.ps1 -PathA hfr-runs\fleet_matrix_a\fleet_device_matrix.json -PathB hfr-runs\fleet_matrix_b\fleet_device_matrix.json -OutMarkdown hfr-runs\fleet_matrix_diff.md

```



Shallow hub validate (`pns_shallow_scan_hub_validate.ps1`) also asserts matrix pull + `schemaVersion=1`.



## USB verification rows



Per-SKU checklists: **`docs/FLEET_DEVICE_VERIFY_MATRIX.md`**.

### MONO + lockscreen rollout checks

For SKUs with `product.focalRow.specialRoles.dedicatedMonochrome=true`, run:

```powershell
.\scripts\pns_mono_capture_verify.ps1
.\scripts\pns_lockscreen_camera_verify.ps1
```

Expected proof:

- `MONO_FALLBACK_SNAPSHOT_SAVED` **or** `captureIndependentTonalStill composed_smoke ok=true saved=`
- `secureLaunchPolicy showWhenLocked=true turnScreenOn=true`
- `secureSession=true mode=...`



## Agents



See **`AGENTS.md`** — **CRITICAL — Fleet capability matrix** and **`.cursor/rules/fleet-generic-policy.mdc`**.

## Product slice — hardware launch & buttons

Written on **every** quick/full rescan by [`ProductHardwareLaunchScan`](app/src/main/java/dev/pointandshoot/fleet/ProductHardwareLaunchScan.kt) into `product.hardwareLaunch` and `product.hardwareButtons`.

| Field | Meaning |
|-------|---------|
| `product.hardwareLaunch.stillImageCamera` | `PackageManager.queryIntentActivities` for `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA` — handler list, `pnsRegistered`, `defaultRoleHolder` (RoleManager `ROLE_CAMERA`) |
| `product.hardwareLaunch.stillImageCameraSecure` | Same for `INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE` |
| `product.hardwareLaunch.videoCamera` / `imageCapture` | `INTENT_ACTION_VIDEO_CAMERA` / `ACTION_IMAGE_CAPTURE` handlers |
| `product.hardwareButtons.knownShutterKeyCodes` | Informational Android key names the app handles (`KEYCODE_CAMERA`, `KEYCODE_FOCUS`, volume, media) |
| `product.hardwareButtons.inputDevices` | `InputManager` inventory with heuristic match (`gpio-keys`, camera/shutter in name) |
| `product.hardwareButtons.dedicatedCameraKeyLikely` | Static heuristic — not Sony-specific |
| `product.hardwareButtons.programmableButtonLikely` | Extra gpio / interactive probe suggests Shortcut-key-style button |
| `product.hardwareButtons.interactiveProbe` | Merged from `files/HARDWARE_KEY_PROBE_LATEST.json` when present (engineering probe / `pns_hardware_key_probe.ps1`) |
| `appendix.inputDevicesRedacted` | Full tier only — redacted `dumpsys input` excerpt |

**Catalog ids:** `product.still_image_camera_launch`, `product.hardware_camera_key`, `product.programmable_hardware_button` — see [`CameraCapabilityCatalog.kt`](app/src/main/java/dev/pointandshoot/fleet/CameraCapabilityCatalog.kt).

**Host scripts:** `pns_hardware_key_probe.ps1`, `pns_hardware_shutter_verify.ps1`, `pns_fleet_matrix_scan.ps1`.

## Milestone 17 — capability catalog & device-tailored UI

| Artifact | Path / code |
|----------|-------------|
| Catalog slice | `capabilityCatalog` + `catalogVersion` in `fleet_device_matrix.json` |
| Human summary | `files/fleet_device_capability_summary.md` (pulled by `pns_fleet_matrix_scan.ps1`) |
| Catalog builder | `CameraCapabilityCatalog.kt`, `CameraCapabilityCatalogBuilder.kt` |
| UI visibility | `FleetUiVisibilityGate.kt`, `FleetChromeVisibility.kt` — hide unavailable consumer chrome; root-only blue + toast |
| Hub UI | `FleetMatrixHubScreen` (Summary · By camera · Features · Raw JSON); `ProbeHubSearch.kt` |
| Encoder rescan | `MediaCodecCapabilityProbe.invalidateAndReprobe()` on matrix quick/full save |

**Consumer chrome policy:** Matrix `capabilityCatalog.deviceSupported` + per-camera `featureGates` drive **hide** vs **show**. Engineering hub shows full inventory (including probe-only rows). Log tags: `PNS.FleetVisibility`, `PNS.ProbeHub`.

**Video format (17.6):** Probe includes **1080p@30** tier; H.264 ≤60 fps rows allowed when HAL **MediaRecorder** lists the size; preview refreshes format catalog when `scanMeta.generatedAtEpochMs` changes after rescan (no app restart).

**Docs:** `docs/CAMERA_CAPABILITY_CATALOG.md` · `docs/PNS_TECHNICAL_SETTINGS.md` §8 / §10 / §14 · `.cursor/rules/fleet-ui-visibility.mdc`

**Milestone 17 gate (2026-05-29):** `pns_verify_toolchain.ps1 -RunTests` (M17 JVM tests PASS; full host gate has pre-existing detekt/license/`WorkflowPresetsTest` drift) + `pns_fleet_matrix_scan.ps1 -Serial b5214fc6` → `hfr-runs/fleet_matrix_20260529_231715/` (pass=True, JSON + summary) + `pns_chrome_ux_gate.ps1 -Serial b5214fc6 -SkipHost` → `hfr-runs/chrome_ux_gate_20260529_232129/` (pass=True, `teleFocalSlotOk=true`) + `pns_video_capability_probe.ps1` → `h264PerfPoint 1920x1080@30fps` on **CPH2583** (`b5214fc6`).


