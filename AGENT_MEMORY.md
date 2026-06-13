# Agent memory (ephemeral)

**Not** bisect history — that stays in [`docs/AGENT_REGRESSION_MEMORY.md`](docs/AGENT_REGRESSION_MEMORY.md). Update this file only at **session startup**, **milestone boundary**, or **architectural pivot** (see [`AGENTS.md`](AGENTS.md) Template file map).

---

## Current focus (updated 2026-06-13 — Milestone H automation pass)

| Field | Value |
|-------|--------|
| **Active milestone** | **H** — human & publication |
| **USB device (this session)** | OP13 **CPH2655** serial **`8bf09993`** (optional regression lane) |
| **Primary fleet device** | OnePlus 12 **CPH2583** — not used this session |

## Last gates (2026-06-13)

| Gate | Result | Artifact |
|------|--------|----------|
| `pns_local_dev_parallel.ps1` | PASS | Tier 0 — 7/7 |
| `pns_prerelease_gate.ps1 -SkipGradle` | PASS | Tier 1 host |
| `pns_aux_dng_capture_analyze.ps1` | PASS | `hfr-runs/aux_dng_capture_analyze_20260613_000901` |
| `pns_m13_3g2_gate.ps1` | PASS | same dir + `acr_signoff.json` |
| `dng_referenceapp_parity_gate.py` | FAIL | scene vs `referenceapp_cph2655` fixtures |
| `pns_fleet_matrix_scan.ps1 -LegacyOp13FleetPolicy` | PASS | `hfr-runs/fleet_matrix_20260613_001343/` |
| `pns_fleet_parity_sweep.ps1 -Mode Full` | PASS | `hfr-runs/parity_sweep_20260613_001426/` |
| `pns_eye_af_pixel_gate.ps1` | FAIL | no face in frame (unattended) |
| `pns_milestone_h_host_gate.ps1 -SkipGradle` | FAIL | aesthetic gate — missing `referenceapp_legacy_sku` fixtures |

## Open blockers

| ID | Area | Status |
|----|------|--------|
| H.1a | `video.raw` / `video.raw_picker` honesty | Open in BUILD_PLAN.md (OP13 Full sweep) |
| H.6 / H.8.1 | Eye-AF pixel gate | FAIL unattended — needs face in frame |
| H.7 | ReferenceApp color parity | FAIL scene vs fixture refs |
| H.5 | Store copy | Human |
| H.9 | PRIVACY / metadata sign-off | Human |
| H.8.3 | H.265 DCG @4K colors | Human visual fail |

## Archived this session

Completed agent rows → [BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md#sprint-h7-op13--optional-op13-regression-lane-agent-closed-2026-06-13) · [host validators](BUILD_PLAN_COMPLETED.md#sprint-h--host-validators--publication-prep-agent-closed-2026-06-13)

## Immediate next steps

1. **[HUMAN]** H.5 store copy · H.9 PRIVACY sign-off · H.8 perceptual rows
2. **[AGENT]** H.1a honesty fixes (Kotlin/catalog) if pursuing ship blockers on OP13
3. **[AGENT]** Re-run eye-AF gate with face in frame, or accept H.8.1 human rubber-stamp

---

*Refresh this file when switching milestones or starting a new agent session.*
