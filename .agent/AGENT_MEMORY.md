# Agent memory (ephemeral)

**Not** bisect history — that stays in [`docs/AGENT_REGRESSION_MEMORY.md`](docs/AGENT_REGRESSION_MEMORY.md). Update this file only at **session startup**, **milestone boundary**, or **architectural pivot** (see [`AGENTS.md`](AGENTS.md) Template file map).

---

## Current focus (updated 2026-08-22 — AUDIT)

| Field | Value |
|-------|--------|
| **Active milestone** | **H** — human & publication |
| **Active sprint** | **AUDIT-2026-08-22** closed (agent) · human H.2–H.9 remain |
| **Template pin** | **0.15.0** (`.template-version`) — see `docs/BOOTSTRAP_ALIGNMENT.md` |
| **Ship line** | **Point & Shoot 0.14.1** / `versionCode` **22018** / tag **v0.14.1** (prepare; publish on `/ship`) |
| **GitHub release** | [v0.14.0](https://github.com/edwardlthompson/point-and-shoot/releases/tag/v0.14.0) until `v0.14.1` publishes |
| **Primary USB device** | OnePlus 12 **CPH2583** serial **`b5214fc6`**. Gitignored env may still say `8bf09993` (OP13) — pass `-Serial` |
| **Agent lanes closed** | **AUDIT-2026-08-22**, **AUDIT6**, **AUDIT5**, **AUDIT4**, **Milestone 28**, **H.CRI-5**, **T.14**, **BOOTSTRAP-0.15** |
| **Session protocol** | `docs/START_HERE.md` → `CURSOR_MODES` → `FOR_AGENTS` → `AGENTS.md` → BUILD_PLAN Sequential |

## Toolchain stack (2026-08-22)

| Component | Version |
|-----------|---------|
| Gradle | **9.5.1** |
| AGP | **9.1.1** |
| Kotlin | **2.4.0** |
| compileSdk | **37** |
| Compose BOM | **2026.05.01** |
| CameraX | **1.6.1** |
| Netty force (Trivy) | **4.1.136.Final** |

## Last gates (2026-08-22 AUDIT)

| Gate | Result | Artifact / notes |
|------|--------|------------------|
| `pns_validate_bootstrap.ps1` | **PASS** | `/audit` step 1 |
| `pns_local_dev_parallel.ps1` (Tier 0) | **PASS** | 8/8 |
| `pns_verify_toolchain.ps1 -RunTests` | **PASS** | assemble + detekt + lint + tests + kover |
| Dependabot Critical/High alerts | **0** open | 0 open alerts total |
| CodeQL / Security scan | **PASS** | Latest on `0f2ee6e` |
| Build signed (tag `v0.14.0`) | **FAIL** then **fixed** | Redundant toolchain verify; workflow no longer re-runs it |
| USB gallery title | **PASS** | CPH2583 `b5214fc6`; force-stop after |
| USB capture / chrome | **SKIP** | No session/chrome-geometry change |

## Open blockers

| ID | Area | Status |
|----|------|--------|
| **CRI-032** | Eye-AF pixel gate | Needs face in frame + USB |
| **CRI-033…035** | Human | DCG colors, store/PRIVACY, OP13 ACR |
| **H.9** | Signing custody | Production keystore exists locally + GH secrets; human backup + `signing_pins.json` |

## Immediate next steps

1. **[HUMAN]** Back up `release.keystore` + `keystore.properties`; pin release cert SHA-256 in `leaderboard-ingest/config/signing_pins.json`
2. **[HUMAN]** Milestone H **CRI-032…035**
3. **[MAINTAINER]** Point gitignored `PNS_ADB_SERIAL` at the phone you want as default (`b5214fc6` vs OP13)

---

**Document control:** 2026-08-22 — AUDIT agent lane closed; refresh at Milestone H ship.
