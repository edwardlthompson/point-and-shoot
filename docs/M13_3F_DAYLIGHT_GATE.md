# Milestone 13.3f — daylight USB gates

**Prerequisite:** Sprint **13.3g** openability **PASS** (automated + human ACR open **3/3** when closing Milestone H).

**Orchestrator:** `scripts/pns_m13_3f_gate.ps1`

## What the gate runs

1. `pns_capture_pipeline_verify.ps1 -Fast`
2. `pns_aux_dng_capture_analyze.ps1 -PreviewDial A -NoFast`
3. `pns_m13_3g2_gate.ps1` (openability + logcat diag)
4. `dng_referenceapp_parity_gate.py` vs `tests/fixtures/referenceapp_legacy_sku/`
5. Optional `-RunProshotSession` → `pns_dng_referenceapp_pns_session.ps1` (side-by-side)
6. Optional `-RefreshProshotRefs` → live forensics + fixture sync

**Exit 0:** pipeline + capture **3/3** + openability **PASS** (parity may **FAIL**).  
**Exit 1:** capture/openability/pipeline failure, or `-RequireParityPass` with parity **FAIL**.

## USB results (legacy SKU `legacy serial`, 2026-05-20)

| Gate | Result |
|------|--------|
| Pipeline verify | **PASS** (`photo_capture_verify_20260520_012406`) |
| Capture M14/M23/M73 | **PASS** (`m13_3f_gate_20260520_012341/pns_capture/`) |
| Openability | **PASS** |
| ReferenceCam parity (fixtures) | **FAIL** — UW/tele green cast vs `referenceapp_legacy_sku` (HAL CM2; not fixed by 13.3e/13.3h) |
| Side-by-side session | `dng_referenceapp_pns_session_20260520_012826/` (ReferenceCam pull = latest DCIM trio; use **live forensics** refs for slot-aligned compare) |

**Human:** `ACR_HUMAN_VERIFY.md` in capture folder — record with `-RecordAcrPass` / `-AcrColorAcceptable`.

## Finding

Automated parity **FAIL** is the **documented** outcome for legacy SKU aux on the **13.3g** pure `DngCreator` path. Closing **13.3f** means gates were run and evidence recorded; **color acceptability** is a **human** call (Milestone H), not rawpy threshold pass.

---

*May 2026*
