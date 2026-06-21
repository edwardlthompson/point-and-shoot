# Agent memory (ephemeral)

**Not** bisect history — that stays in [`docs/AGENT_REGRESSION_MEMORY.md`](docs/AGENT_REGRESSION_MEMORY.md). Update this file only at **session startup**, **milestone boundary**, or **architectural pivot** (see [`AGENTS.md`](AGENTS.md) Template file map).

---

## Current focus (updated 2026-06-21 — AUDIT4)

| Field | Value |
|-------|--------|
| **Active milestone** | **H** — human & publication |
| **Active sprint** | None (AUDIT4 closed) |
| **Ship line** | **0.14.0-beta.15** / `versionCode` **22009** / catalog **v6** |
| **GitHub release** | [v0.14.0-beta.15](https://github.com/edwardlthompson/point-and-shoot/releases/tag/v0.14.0-beta.15) |
| **Primary USB device** | OnePlus 12 **CPH2583** — wireless ADB `b5214fc6` |
| **Agent lanes closed** | **AUDIT4** (H metering + QR), **Milestone 28**, **AUDIT-2026-06-21**, **H.CRI-5**, **T.14** |

## Toolchain stack (2026-06-21)

| Component | Version |
|-----------|---------|
| Gradle | **9.5.1** |
| AGP | **9.1.1** |
| Kotlin | **2.4.0** |
| compileSdk | **37** |
| Compose BOM | **2026.05.01** |
| CameraX | **1.6.1** |

## Last gates (2026-06-21 AUDIT4)

| Gate | Result | Artifact / notes |
|------|--------|------------------|
| `pns_validate_bootstrap.ps1` | **PASS** | `/audit` step 1 |
| `pns_local_dev_parallel.ps1` (Tier 0) | **PASS** | 8/8 jobs |
| `pns_verify_toolchain.ps1 -RunTests` | **PASS** | After AUDIT4 detekt fixes |
| `pns_highlight_meter_verify.ps1` | **PASS** | `highlight_meter_verify_20260621_152031` |
| `pns_qr_scan_verify.ps1` | **PASS** | `qr_scan_verify_20260621_152424` |
| CI Toolchain / CodeQL / Security (`b762061`) | **PASS** | `gh run list` |
| `pns_mediacodec_hfr_verify -GateProfile vf` | **SKIP/FAIL** | `ffprobe` not on PATH |
| Wireless `adb install` | **FLAKE** | `INSTALL_PARSE_FAILED_NOT_APK` — gates ran on installed APK |

## Open blockers

| ID | Area | Status |
|----|------|--------|
| **CRI-032** | Eye-AF pixel gate | Needs face in frame for green markers |
| **CRI-033…035** | Human | DCG colors, store/PRIVACY, OP13 ACR |
| **H.9** | Signing | Debug-key release shipped; production keystore open |
| **Host** | `ffprobe` | VF mediacodec gate blocked without FFmpeg on PATH |

## Immediate next steps

1. **[HUMAN]** Milestone H checklist (**CRI-032…035**) + subjective UX (**H.8.3** DCG colors)
2. **[AGENT]** Commit AUDIT4 code (H metering + QR) when user requests
3. **[MAINTAINER]** Install **ffprobe** on PATH; retry wireless sideload if testing fresh debug APK

---

**Document control:** 2026-06-21 — AUDIT4 closed; refresh at Milestone H ship.
