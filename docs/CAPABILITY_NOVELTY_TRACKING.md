# Capability Novelty Tracking (FOSS-safe)

This repo now has two complementary ways to track newly discovered camera features:

1. `scripts/pns_capability_novelty_ping.ps1` (device-aware)
2. `scripts/pns_camera2_upstream_ping.ps1` (host-only monthly upstream check)
3. `scripts/pns_monthly_capability_report.ps1` (Task Scheduler-friendly wrapper; runs both and writes one consolidated report)

## Why this is FOSS-safe

- No hidden telemetry.
- No network egress by default.
- All payloads are visible JSON artifacts in `hfr-runs/`.
- "Call home" is explicit and opt-in (`-WebhookUrl`).

## Recommended workflow

- After matrix/probe updates on USB:
  - `.\scripts\pns_capability_novelty_ping.ps1`
- If report is good and you want to advance the baseline:
  - `.\scripts\pns_capability_novelty_ping.ps1 -UpdateBaseline`
- Monthly host-only upstream check:
  - `.\scripts\pns_camera2_upstream_ping.ps1`
- If monthly changes are expected/accepted:
  - `.\scripts\pns_camera2_upstream_ping.ps1 -UpdateBaseline`
- Monthly wrapper (single run + single consolidated report):
  - `.\scripts\pns_monthly_capability_report.ps1`
- Monthly wrapper with baseline advancement:
  - `.\scripts\pns_monthly_capability_report.ps1 -UpdateBaselines`

## Optional call-home endpoint

You can post novelty-only deltas to your own endpoint:

`.\scripts\pns_capability_novelty_ping.ps1 -WebhookUrl "https://your-endpoint.example/pns/ingest"`

Or via the monthly wrapper:

`.\scripts\pns_monthly_capability_report.ps1 -WebhookUrl "https://your-endpoint.example/pns/ingest"`

The payload intentionally excludes image content and app-private user media.

Webhook behavior:

- If `hasNewDiscoveries=false`, **nothing is sent** (post is skipped).
- If `hasNewDiscoveries=true`, only the novelty delta payload is posted.

## Exactly what is sent home (only if opted in)

No network data is sent unless you provide `-WebhookUrl`.

When enabled, only the capability novelty script posts JSON with:

- `schema` and `generatedAtUtc`
- `hasNewDiscoveries`
- `newlyDiscovered`
  - `catalogIds`
  - `featureGateKeys`
  - `vendorKeyNames`
  - `cameraIds`
- `scanMeta` from the fleet matrix quick scan

Not sent:

- Photos, videos, DNG files, thumbnails, gallery metadata
- User account info, contacts, messages, location history
- Full app settings/preferences dumps
- Raw logcat streams

## Local discovery ledger (append-only)

When new discoveries exist, the script appends one JSONL entry to:

- `docs/CAPABILITY_DISCOVERY_LEDGER.jsonl`

Each entry is split into:

- `appSurfaced` — newly observed `catalogIds`, `featureGateKeys`, `cameraIds`
- `needsBuildTriage` — newly observed `vendorKeyNames`

No entry is appended when there is no new data.

## What we use the opt-in data for

- Detect newly surfaced HAL/vendor capabilities that should be triaged into the capability matrix.
- Prioritize engineering work for feature parity gaps and newly available functionality.
- Drive planned enhancements (new toggles, capture modes, and compatibility paths) based on observed capability deltas.
- Track fleet evolution over time so regressions and new opportunities are actionable in `BUILD_PLAN.md`.

## Task Scheduler (monthly) example

This wrapper is designed for non-interactive scheduling and writes one report folder per run under `hfr-runs/`.

Example Action:

`powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\edwar\AndroidStudioProjects\point-and-shoot\scripts\pns_monthly_capability_report.ps1"`

Optional webhook action:

`powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\edwar\AndroidStudioProjects\point-and-shoot\scripts\pns_monthly_capability_report.ps1" -WebhookUrl "https://your-endpoint.example/pns/ingest"`

## Baseline files

- `docs/FLEET_CAPABILITY_NOVELTY_BASELINE.json`
- `docs/CAMERA2_UPSTREAM_BASELINE.json`

These are plain JSON and can be reviewed in PRs.

## Experimental max-resolution unlock proof (CPH2583 lane)

Use the still-resolution verify gate to produce one deterministic artifact bundle covering:

- baseline stock lane,
- experimental unlock enabled lane,
- forced safe-mode recovery lane.

Command:

`.\scripts\pns_still_resolution_mode_verify.ps1`

The gate writes `still_resolution_mode_verify_summary.json` (`schema: pns.still_resolution_mode_verify.v2`) and scenario logs under one `hfr-runs/still_resolution_mode_verify_*` folder.

### Rollback criteria

Treat the experimental lane as rollback-required when either condition is true:

- `unlockProducedDelta` is false (no effective still-stream delta vs stock), or
- `safeModeForced.failClosed` is false (forced safe mode still allows unlock apply path).

This keeps the lane reversible and prevents shipping unstable "no gain" unlock behavior.
