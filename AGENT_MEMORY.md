# Agent memory (ephemeral)

**Not** bisect history — that stays in [`docs/AGENT_REGRESSION_MEMORY.md`](docs/AGENT_REGRESSION_MEMORY.md). Update this file only at **session startup**, **milestone boundary**, or **architectural pivot** (see [`AGENTS.md`](AGENTS.md) Template file map).

---

## Current focus (updated 2026-06-18 — `/audit` #2)

| Field | Value |
|-------|--------|
| **Active milestone** | **H** — human & publication |
| **Active sprint** | **AUDIT2-2026-06-18** — post-ship CI hygiene (agent items closed) |
| **Primary USB device** | OnePlus 12 **CPH2583** — Wi‑Fi ADB `adb-b5214fc6-D4ZwCF._adb-tls-connect._tcp` (`scripts/pns_adb_device.env`) |
| **Agent lanes closed** | **H.CRI-5**, **T.14**, **H.HYGIENE**, **AUDIT-2026-06-18**, **AUDIT2.1–2, AUDIT2.5** |

## Last gates (2026-06-18)

| Gate | Result | Artifact / notes |
|------|--------|------------------|
| `pns_validate_bootstrap.ps1` | **PASS** | Template 0.10.0, batch commands |
| `pns_local_dev_parallel.ps1` | **PASS** | Tier 0 — 8/8 |
| `pns_verify_toolchain.ps1 -RunTests` | **PASS** | detekt, lint, JVM tests, Kover |
| `:pns-*:detekt` | **PASS** | Wired in Tier 2 |
| `pns_capture_pipeline_verify.ps1` | **PASS** | `hfr-runs/photo_capture_verify_20260618_025139` |
| `pns_chrome_ux_gate.ps1` | **PASS** | `hfr-runs/chrome_ux_gate_20260618_025206` |
| `pns_check_github_ci.ps1` | **PASS** | Toolchain + Security @ `e65baad`; CodeQL fallback |
| GitHub Dependabot alerts | **0** open | 5 Gradle PRs deferred (#4, #8, #9, #11, #12) |

## Open blockers

| ID | Area | Status |
|----|------|--------|
| **CRI-032…035** | Human | Eye-AF face, DCG colors, store/PRIVACY, OP13 ACR |
| **H.9** | Release | Owner sign-off → `pns_github_release.ps1` |
| **AUDIT2.3** | Dependencies | Gradle 9.5 / AGP / CameraX / Compose — separate sprint |
| **AUDIT2.4** | Host | `dng_aesthetic_gate` informational scene delta |

## Backlog (not ship blockers on CPH2583)

- Lint baseline ~170 warnings
- Branch protection optional (required checks on `main`)
- ADR-0009 deferred: full PreviewEngineScreen / RawCaptureSupport extraction

## Immediate next steps

1. **[HUMAN]** Milestone H checklist (**CRI-032…035**) + **H.9** release sign-off
2. **[AGENT]** When approved: Gradle Dependabot sprint (**AUDIT2.3**) with Tier 2 + USB per bump
3. **[AGENT]** Optional: triage `dng_aesthetic_gate` fixture expectations (**AUDIT2.4**)

---

**Document control:** 2026-06-18 — `/audit` #2 closure; refresh at Milestone H ship or next audit.
