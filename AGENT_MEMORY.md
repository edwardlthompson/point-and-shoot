# Agent memory (ephemeral)

**Not** bisect history — that stays in [`docs/AGENT_REGRESSION_MEMORY.md`](docs/AGENT_REGRESSION_MEMORY.md). Update this file only at **session startup**, **milestone boundary**, or **architectural pivot** (see [`AGENTS.md`](AGENTS.md) Template file map).

---

## Current focus (updated 2026-08-03 — AUDIT6)

| Field | Value |
|-------|--------|
| **Active milestone** | **H** — human & publication |
| **Active sprint** | **AUDIT6** closed (agent) · human H.2–H.9 remain |
| **Template pin** | **0.15.0** (`.template-version`) — see `docs/BOOTSTRAP_ALIGNMENT.md` |
| **Ship line** | **0.14.0-beta.19** / `versionCode` **22013** |
| **GitHub release** | [v0.14.0-beta.19](https://github.com/edwardlthompson/point-and-shoot/releases/tag/v0.14.0-beta.19) |
| **Primary USB device** | OnePlus 12 **CPH2583** — refresh `scripts/pns_adb_device.env` `PNS_ADB_SERIAL` when online |
| **Agent lanes closed** | **AUDIT6**, **AUDIT5**, **AUDIT4**, **Milestone 28**, **H.CRI-5**, **T.14**, **BOOTSTRAP-0.15** |
| **Session protocol** | `docs/START_HERE.md` → `CURSOR_MODES` → `FOR_AGENTS` → `AGENTS.md` → BUILD_PLAN Sequential |
| **Workspace note** | Local folder was **empty** 2026-08-03 — recloned via `gh repo clone`; recreate `local.properties` as `sdk.dir=C\:\\Users\\…\\Android\\Sdk` (PropertyEscape) |

## Toolchain stack (2026-08-03)

| Component | Version |
|-----------|---------|
| Gradle | **9.5.1** |
| AGP | **9.1.1** |
| Kotlin | **2.4.0** |
| compileSdk | **37** |
| Compose BOM | **2026.05.01** |
| CameraX | **1.6.1** |
| Netty force (Trivy) | **4.1.136.Final** |

## Last gates (2026-08-03 AUDIT6)

| Gate | Result | Artifact / notes |
|------|--------|------------------|
| `pns_validate_bootstrap.ps1` | **PASS** | `/audit` step 1 |
| `pns_local_dev_parallel.ps1` (Tier 0) | **PASS** | 8/8 |
| `pns_verify_toolchain.ps1 -RunTests` | **PASS** | assemble + detekt + lint + tests + kover |
| Security scan (pre-fix) | **FAIL** | Trivy HIGH Netty @ 4.1.135.Final — fixed in working tree |
| CodeQL scheduled | **PASS** | 2026-08-03 |
| OpenSSF Scorecard | **PASS** | 2026-08-03 |
| Dependabot Critical/High alerts | **0** open | **10** open Dependabot PRs |
| USB capture / chrome | **SKIP** | No ADB this session |

## Open blockers

| ID | Area | Status |
|----|------|--------|
| **CRI-032** | Eye-AF pixel gate | Needs face in frame + USB |
| **CRI-033…035** | Human | DCG colors, store/PRIVACY, OP13 ACR |
| **H.9** | Signing | Debug-key release fallback; production keystore open |

## Immediate next steps

1. **[MAINTAINER]** Commit AUDIT6 when ready; confirm next **Security scan** schedule is green
2. **[HUMAN]** Milestone H checklist (**CRI-032…035**) + **H.9** signing
3. **[MAINTAINER]** `/dependabot` triage; refresh `PNS_ADB_SERIAL` when phone is online

---

**Document control:** 2026-08-03 — AUDIT6 agent lane closed; refresh at Milestone H ship.
