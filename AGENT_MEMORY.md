# Agent memory (ephemeral)

**Not** bisect history — that stays in [`docs/AGENT_REGRESSION_MEMORY.md`](docs/AGENT_REGRESSION_MEMORY.md). Update this file only at **session startup**, **milestone boundary**, or **architectural pivot** (see [`AGENTS.md`](AGENTS.md) Template file map).

---

## Current focus (updated 2026-07-22 — BOOTSTRAP-0.15)

| Field | Value |
|-------|--------|
| **Active milestone** | **H** — human & publication |
| **Active sprint** | **BOOTSTRAP-0.15** — template v0.15.0 Reference alignment (host-only) |
| **Template pin** | **0.15.0** (`.template-version`) — see `docs/BOOTSTRAP_ALIGNMENT.md` |
| **Ship line** | **0.14.0-beta.19** / `versionCode` **22013** |
| **GitHub release** | [v0.14.0-beta.19](https://github.com/edwardlthompson/point-and-shoot/releases/tag/v0.14.0-beta.19) |
| **Primary USB device** | OnePlus 12 **CPH2583** — env `b5214fc6`; **online AUDIT5:** `8bf09993` (OP13) |
| **Agent lanes closed** | **AUDIT5**, **AUDIT4**, **Milestone 28**, **H.CRI-5**, **T.14** |
| **Session protocol** | `docs/START_HERE.md` → `CURSOR_MODES` → `FOR_AGENTS` → `AGENTS.md` → BUILD_PLAN Sequential |

## Toolchain stack (2026-07-11)

| Component | Version |
|-----------|---------|
| Gradle | **9.5.1** |
| AGP | **9.1.1** |
| Kotlin | **2.4.0** |
| compileSdk | **37** |
| Compose BOM | **2026.05.01** |
| CameraX | **1.6.1** |

## Last gates (2026-07-11 AUDIT5)

| Gate | Result | Artifact / notes |
|------|--------|------------------|
| `pns_validate_bootstrap.ps1` | **PASS** | `/audit` step 1 |
| `pns_local_dev_parallel.ps1` (Tier 0) | **PASS** | 8/8 (pre + post A5.1) |
| `pns_verify_toolchain.ps1 -RunTests` | **PASS** | Pre + post A5.1 |
| CI Toolchain (`0616f5e`) | **PASS** | beta.16 push |
| CodeQL scheduled | **PASS** | 2026-07-06 |
| Security scan | **FAIL** → allowlist shipped | Confirm next schedule after commit |
| Dependabot Critical/High | **0** open alerts | **10** open Dependabot PRs |
| `ffprobe` | **On PATH** | Desktop FFmpeg 7.0.2 |

## Open blockers

| ID | Area | Status |
|----|------|--------|
| **CRI-032** | Eye-AF pixel gate | Needs face in frame for green markers |
| **CRI-033…035** | Human | DCG colors, store/PRIVACY, OP13 ACR |
| **H.9** | Signing | Debug-key release shipped; production keystore open |

## Immediate next steps

1. **[HUMAN]** Milestone H checklist (**CRI-032…035**) + **H.9** signing
2. **[MAINTAINER]** Commit AUDIT5 (`.gitleaks.toml` + memory/docs) when ready; `/dependabot` triage
3. **[MAINTAINER]** Refresh `PNS_ADB_SERIAL` for CPH2583 (or use `-Serial 8bf09993` for OP13 lane)

---

**Document control:** 2026-07-11 — AUDIT5 agent lane closed; refresh at Milestone H ship.
