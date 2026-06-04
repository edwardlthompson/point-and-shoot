# Parity automation coverage (M21/M22)

Generated reference — catalog rows with `parityProofScript` vs `GAP_UNAUTOMATED` in Full sweep.
M22 adds host proof-pack merge (`scripts/pns_parity_proof_pack.ps1`) with schema `parity_proof_results.v1`.

| Proof hook | Example catalog ids |
|------------|---------------------|
| `pns_aux_dng_capture_analyze.ps1` | `raw.dng`, `still.referenceapp_leaf` |
| `pns_pip_preview_verify.ps1` | `preview.pip` |
| `pns_multicam_melt_verify.ps1` | `video.multicam_melt` |
| `pns_video_format_test.ps1` | `video.vp9` |
| `pns_raw_video_verify.ps1` | `video.raw_picker` |
| `pns_workflow_test.ps1` | `workflow.preset.*` |
| JVM tests | `video.dual_iso` → `DualIsoVideoMergerTest` |

Rows without a proof script and `appStatus=Partial|Shipped` in **Full** mode surface as `GAP_UNAUTOMATED` (planning only, does not fail Quick gate).

M22 merge notes:
- `pass=true` in `parity_proof_results.json` maps row to `provenOk=true` in merged in-app parity report.
- `skippedReason=matrix_gate:*` maps to honest matrix-gated closure (not treated as failure).
- Residual `GAP_UNAUTOMATED` rows must be resolved by adding `parityProofScript` and proof-manifest mapping before M22 close.

Host gates: `scripts/pns_m21_gate.ps1`, `scripts/pns_m22_gate.ps1` · sweep: `scripts/pns_fleet_parity_sweep.ps1`
