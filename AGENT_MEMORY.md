# Agent memory (ephemeral)

**Not** bisect history — that stays in [`docs/AGENT_REGRESSION_MEMORY.md`](docs/AGENT_REGRESSION_MEMORY.md). Update this file only at **session startup**, **milestone boundary**, or **architectural pivot** (see [`AGENTS.md`](AGENTS.md) Template file map).

---

## Current focus (updated 2026-06-18 — AUDIT2 + Dependabot closure)

| Field | Value |
|-------|--------|
| **Active milestone** | **H** — human & publication |
| **Active sprint** | None (AUDIT2 closed) |
| **Primary USB device** | OnePlus 12 **CPH2583** — Wi‑Fi ADB `adb-b5214fc6-D4ZwCF._adb-tls-connect._tcp` (`scripts/pns_adb_device.env`) |
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

## Last gates (2026-06-18)

| Gate | Result | Artifact / notes |
|------|--------|------------------|
| `pns_verify_toolchain.ps1 -RunTests` | **PASS** | `:app:koverVerify` (not `koverVerifyDebug`) |
| `pns_capture_pipeline_verify.ps1` | **PASS** | `hfr-runs/photo_capture_verify_20260618_110420` |
| `pns_chrome_ux_gate.ps1` | **PASS** | `hfr-runs/chrome_ux_gate_20260618_110452` |
| Dependabot Gradle PRs | **0** open | #4, #8, #9, #11, #12 closed on `main` |
| Dependabot security alerts | **0** open | |

## Open blockers

| ID | Area | Status |
|----|------|--------|
| **CRI-032…035** | Human | Eye-AF face, DCG colors, store/PRIVACY, OP13 ACR |
| **H.9** | Release | Owner sign-off → `pns_github_release.ps1` |

## Backlog (not ship blockers on CPH2583)

- `dng_aesthetic_gate` — informational; skip with `-SkipDngAestheticGate` on Milestone H host gate
- Lint baseline ~83 warnings (PropertyEscape on `local.properties` fixed locally with escaped `sdk.dir`)
- ADR-0009 deferred: full PreviewEngineScreen / RawCaptureSupport extraction

## Immediate next steps

1. **[HUMAN]** Milestone H checklist (**CRI-032…035**) + **H.9** release sign-off
2. **[AGENT]** CI installs `platforms;android-37.0` + `android-37` symlink (sdkmanager naming quirk)

---

**Document control:** 2026-06-18 — AUDIT2 + Dependabot Gradle stack closed; refresh at Milestone H ship.
