# Agent memory (ephemeral)

**Not** bisect history — that stays in [`docs/AGENT_REGRESSION_MEMORY.md`](docs/AGENT_REGRESSION_MEMORY.md). Update this file only at **session startup**, **milestone boundary**, or **architectural pivot** (see [`AGENTS.md`](AGENTS.md) Template file map).

---

## Current focus (updated 2026-06-18 — AUDIT3)

| Field | Value |
|-------|--------|
| **Active milestone** | **H** — human & publication |
| **Active sprint** | None (AUDIT3 closed) |
| **Primary USB device** | OnePlus 12 **CPH2583** — wireless ADB `10.0.0.9:44487` (`scripts/pns_adb_device.env`) |
| **Agent lanes closed** | **AUDIT3-2026-06-18**, release fix `1b6f1cb`, **AUDIT2**, **H.CRI-5**, **T.14**, **H.HYGIENE** |

## Toolchain stack (2026-06-18)

| Component | Version |
|-----------|---------|
| Gradle | **9.5.1** |
| AGP | **9.1.1** |
| Kotlin | **2.4.0** |
| compileSdk | **37** |
| Compose BOM | **2026.05.01** |
| CameraX | **1.6.1** |

## Last gates (2026-06-18)

| Gate | Result | Artifact / notes |
|------|--------|------------------|
| `pns_local_dev_parallel.ps1` | **PASS** | 8/8 |
| `pns_verify_toolchain.ps1 -RunTests` | **PASS** | Tier 2 during `/audit` |
| `pns_validate_bootstrap.ps1` | **PASS** | `/audit` step 1 |
| GitHub Toolchain @ `1b6f1cb` | **PASS** | Security/CodeQL failed pre-AUDIT3 push |
| `pns_eye_af_pixel_gate.ps1` | **FAIL** | No face in frame; HUD seed fix verified in logcat |
| `pns_release_packaging.ps1 -SkipAssemble` | **PASS** | zipalign after `sdk.dir` parse fix |
| Release | **Shipped** | `v0.14.0-beta.12` minified APK on GitHub |

## Open blockers

| ID | Area | Status |
|----|------|--------|
| **CRI-032** | Eye-AF pixel gate | HUD ADB seed **fixed**; gate needs **face in frame** for green markers |
| **CRI-033…035** | Human | DCG colors, store/PRIVACY, OP13 ACR |
| **H.9** | Signing | Debug-key release; human keystore + `signing_pins.json` |

## Immediate next steps

1. **[HUMAN]** Milestone H checklist (**CRI-032…035**) + subjective UX
2. Push AUDIT3 CI/overlay fixes; confirm Security + CodeQL green on `main`
3. Re-run `pns_eye_af_pixel_gate.ps1` with face in frame after AUDIT3 lands

---

**Document control:** 2026-06-18 — AUDIT3 closure; refresh at Milestone H ship.
