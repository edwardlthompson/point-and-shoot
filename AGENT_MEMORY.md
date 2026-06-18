# Agent memory (ephemeral)

**Not** bisect history — that stays in [`docs/AGENT_REGRESSION_MEMORY.md`](docs/AGENT_REGRESSION_MEMORY.md). Update this file only at **session startup**, **milestone boundary**, or **architectural pivot** (see [`AGENTS.md`](AGENTS.md) Template file map).

---

## Current focus (updated 2026-06-18 — AUDIT2 + Dependabot closure)

| Field | Value |
|-------|--------|
| **Active milestone** | **H** — human & publication |
| **Active sprint** | None (AUDIT2 closed) |
| **Primary USB device** | OnePlus 12 **CPH2583** — **wireless ADB** `10.0.0.9:44487` (mDNS `adb-b5214fc6-D4ZwCF._adb-tls-connect._tcp`; `scripts/pns_adb_device.env`) — **user confirmed connected 2026-06-18** |
| **Agent lanes closed** | **AUDIT2-2026-06-18** (all items), **AUDIT-2026-06-18**, **H.CRI-5**, **T.14**, **H.HYGIENE** |

## Toolchain stack (2026-06-18)

| Component | Version |
|-----------|---------|
| Gradle | **9.5.1** |
| AGP | **9.1.1** (built-in Kotlin — no `kotlin.android` plugin) |
| Kotlin | **2.4.0** |
| compileSdk | **37** |
| Gradle daemon JDK | **21** (Paparazzi 2.0 plugin); app bytecode **JVM 17** |
| Compose BOM | **2026.05.01** |
| CameraX | **1.6.1** |
| androidx.core | **1.19.0** |

## Last gates (2026-06-18 — wireless ADB session)

| Gate | Result | Artifact / notes |
|------|--------|------------------|
| `pns_local_dev_parallel.ps1` | **PASS** | 8/8 (SBOM baseline refreshed) |
| `pns_capture_pipeline_verify.ps1` | **PASS** | `hfr-runs/photo_capture_verify_20260618_120404` |
| `pns_chrome_ux_gate.ps1` | **PASS** | `hfr-runs/chrome_ux_gate_20260618_120428` |
| `pns_prerelease_gate.ps1 -IncludeUsb` | **PASS** | capture + chrome; eye-AF skipped (`-SkipEyeAfPixelGate`) |
| `pns_milestone_h_host_gate.ps1` | **PASS** | `-SkipGradle` |
| `pns_about_links_verify.ps1` | **PASS** | Venmo + GitHub releases HTTP + USB settingsAbout |
| `pns_github_pages_smoke.ps1` | **PASS** | `hfr-runs/github_pages_smoke_20260618_080455` |
| `pns_eye_af_pixel_gate.ps1` | **FAIL** | face_box OK, `eyes`/`markers` empty — `eye_af_pixel_gate_20260618_075936` |
| `pns_video_hdr10_metadata_verify.ps1` | **FAIL** | CPH2583 — no DCG session / video saved |
| `pns_dual_video_verify.ps1` | **FAIL** | dual preview log missing on CPH2583 |
| `pns_eye_af_alignment_probe.ps1 -HostOnly` | **PASS** | JVM overlay source |
| GitHub CI @ `d5a4037` | **PASS** | Toolchain + Security + CodeQL |
| Wireless ADB | **ON** | `10.0.0.9:44487` — disconnect duplicate mDNS serial if `adb devices` shows two |

## Open blockers

| ID | Area | Status |
|----|------|--------|
| **CRI-032** | Eye-AF overlay | **FAIL** — markers not drawn (not “no face”) |
| **CRI-033…035** | Human | DCG colors, store/PRIVACY, OP13 ACR |
| **H.9** | Release | **Shipped** `v0.14.0-beta.12` (2026-06-18) — human PRIVACY/signing_pins remain |

## Backlog (not ship blockers on CPH2583)

- `dng_aesthetic_gate` — informational; skip with `-SkipDngAestheticGate` on Milestone H host gate
- Lint baseline ~83 warnings (PropertyEscape on `local.properties` fixed locally with escaped `sdk.dir`)
- ADR-0009 deferred: full PreviewEngineScreen / RawCaptureSupport extraction

## Immediate next steps

1. **[HUMAN]** Milestone H checklist (**CRI-032…035**) + **H.9** release sign-off
2. Remove CodeQL Kotlin **2.3.21** CI pin when github/codeql#21938 ships (restore scan on **2.4.0**)

---

**Document control:** 2026-06-18 — AUDIT2 + Dependabot Gradle stack closed; refresh at Milestone H ship.
