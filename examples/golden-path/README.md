# Golden Path (Point & Shoot)

Offline and on-device reference flows for agents and maintainers. **No duplicate app code** — routes live in `:app`.

## 1. Engineering probe hub

- **Entry:** `CameraCapabilitiesProbe` home → Diagnostics / matrix hub
- **ADB:** `adb shell am start -n dev.pointandshoot/.MainActivity --es pns_screen probehub`
- **Gate:** `scripts/pns_fleet_matrix_scan.ps1`
- **Docs:** [`PROBE_BUILD_PLAN.md`](../../PROBE_BUILD_PLAN.md), [`docs/FLEET_DEVICE_CAPABILITY_MATRIX.md`](../../docs/FLEET_DEVICE_CAPABILITY_MATRIX.md)

## 2. Unified mock preview (T.14)

- **Entry:** `UnifiedMockPreviewScreen` — GLES test pattern + Pro HUD chrome without Camera2
- **ADB:** `--es pns_screen mock` (aliases: `prohud`, `glpreview`)
- **Code:** [`app/src/main/java/dev/pointandshoot/preview/mock/UnifiedMockPreviewScreen.kt`](../../app/src/main/java/dev/pointandshoot/preview/mock/UnifiedMockPreviewScreen.kt)
- **ADR:** [`docs/adr/0008-mock-mode-cold-restart.md`](../../docs/adr/0008-mock-mode-cold-restart.md)

## 3. Portrait preview engine (USB)

- **Entry:** `--es pns_screen preview`
- **Gates:** `pns_capture_pipeline_verify.ps1` then `pns_chrome_ux_gate.ps1` (sequential, one serial)
- **Chrome lock:** [`docs/preview-chrome-layout-style-guide.md`](../../docs/preview-chrome-layout-style-guide.md)

## Module map

See [`modules/`](../../modules/) and [`docs/BOOTSTRAP_TEMPLATE_MAP.md`](../../docs/BOOTSTRAP_TEMPLATE_MAP.md).
