# Agent memory (ephemeral)

**Not** bisect history — that stays in [`docs/AGENT_REGRESSION_MEMORY.md`](docs/AGENT_REGRESSION_MEMORY.md). Update this file only at **session startup**, **milestone boundary**, or **architectural pivot** (see [`AGENTS.md`](AGENTS.md) Template file map).

---

## Current focus (updated 2026-06-18 — `/audit` + Sprint AUDIT)

| Field | Value |
|-------|--------|
| **Active milestone** | **H** — human & publication |
| **Active sprint** | **AUDIT-2026-06-18** — CI fixes + TM commit hygiene |
| **Primary USB device** | OnePlus 12 **CPH2583** — Wi‑Fi ADB `adb-b5214fc6-D4ZwCF._adb-tls-connect._tcp` (update `scripts/pns_adb_device.env`) |
| **Agent lanes closed** | **H.CRI-5**, **T.14**, **H.HYGIENE**, **AUDIT.1–4** (local) |

## Last gates (2026-06-18)

| Gate | Result | Artifact / notes |
|------|--------|------------------|
| `pns_validate_bootstrap.ps1` | **PASS** | Template 0.10.0, batch commands |
| `pns_local_dev_parallel.ps1` | **PASS** | Tier 0 — 8/8 |
| `pns_verify_toolchain.ps1 -RunTests` | **PASS** | detekt, lint, JVM tests, Kover |
| `:pns-*:detekt` | **PASS** | After baseline patch (LeafDngFleetPolicies) |
| `pns_capture_pipeline_verify.ps1` | **PASS** | `hfr-runs/photo_capture_verify_20260618_013837` |
| `pns_chrome_ux_gate.ps1` | **PASS** | `hfr-runs/chrome_ux_gate_20260618_013908` |
| GitHub CI (`main`) | **PASS** | Toolchain + Security + CodeQL (manual dispatch) |

## Open blockers

| ID | Area | Status |
|----|------|--------|
| **AUDIT.6** | Git | Large uncommitted TM + CI worktree — needs integration commit + push |
| **AUDIT.5** | ADB env | `pns_adb_device.env` still has stale `192.168.1.2:44891` |
| **CRI-032…035** | Human | Eye-AF face, DCG colors, store/PRIVACY, OP13 ACR |
| **H.9** | Release | Owner sign-off → `pns_github_release.ps1` |

## Backlog (not ship blockers on CPH2583)

- `dng_aesthetic_gate` host self-test
- Lint baseline ~170 warnings
- Dependabot **13** open PRs (merge Actions after CI green)
- Gradle 9.5 / major AGP — separate sprint

## Immediate next steps

1. **[AGENT]** Commit + push CI workflow fixes + TM modularization (AUDIT.6)
2. **[AGENT]** Update `pns_adb_device.env` wireless serial (AUDIT.5)
3. **[HUMAN]** Milestone H checklist + Dependabot triage after CI green

---

**Document control:** 2026-06-18 — `/audit` session; refresh at Milestone H closure or next audit.
