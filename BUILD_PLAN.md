## Build plan (Point & Shoot)

**Purpose:** Active milestones and open tasks. Shipped work → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)**.

| SoT | Path |
|-----|------|
| Agent entry | `docs/START_HERE.md` · `docs/FOR_AGENTS.md` · `AGENTS.md` |
| Bootstrap alignment | `docs/BOOTSTRAP_ALIGNMENT.md` · `TEMPLATE_INDEX.json` |
| Settings / pipeline | `docs/PNS_TECHNICAL_SETTINGS.md` |
| Regression locks | `docs/AGENT_REGRESSION_MEMORY.md` · `docs/REVERTED_FEATURES_RESTORE_LIST.md` §8 |
| Fleet / DNG | `docs/FLEET_DEVICE_CAPABILITY_MATRIX.md` · `AGENTS.md` CRITICAL sections |
| Code review intake | `docs/CODE_REVIEW_PLANNING_INTAKE.json` · ephemeral `CODE_REVIEW.md` (gitignored; from [`CODE_REVIEW.md.example`](CODE_REVIEW.md.example)) |
| Parity debt | `docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json` |
| Peer benchmark + M28 program | `docs/CAMERA_APP_PIPELINE_BENCHMARK.md` (Sprint **28.0**) · plan `.cursor/plans/camera_pipeline_benchmark_ba492901.plan.md` |
| Human backlog | `HUMAN_BACKLOG.md` |

**Primary device:** OnePlus 12 **CPH2583** · wireless ADB mDNS `adb-b5214fc6-D4ZwCF._adb-tls-connect._tcp` (`scripts/pns_adb_device.env` — refresh `PNS_ADB_SERIAL` when IP changes).

### Owner label legend

| Label | Owner | When to use |
|-------|-------|-------------|
| `[AGENT]` | Cursor Agent | Code, docs, scaffolding, tests, CI config |
| `[HUMAN]` | Human developer | Approvals, credentials, GitHub settings, product decisions |
| `[ADB]` | Human (Android) | USB device testing, F-Droid submission, ACR viewer sign-off |
| `[AUTO]` | CI/scripts/bots | GitHub Actions, Dependabot, pre-commit, update checker |

### Status markers

Use **emoji markers** (not `- [ ]` GitHub checkboxes). **Intentional deviation** from upstream bootstrap `🟡/🔴`: this repo keeps **`🔲` / `✅` / `❌`**.

| Marker | State |
|--------|-------|
| 🔲 | Open |
| ✅ | Done |
| ❌ | Blocked — append reason |

**Task format:** `🔲 [OWNER] Description`

**Lanes:** execute **Sequential** `[AGENT]` first → **Parallel** (isolated paths) → **Human & device (after automation)**.

---

### How agents must execute

1. **One milestone at a time** — **Milestone T** + **H.CRI-5** + **H-RESTORE** + **Milestone 28** closed (agent). **Active:** **Milestone H** (human + residual agent). Serialize **HUMAN** blockers (dual-video framing, secure-camera PRIVACY review, **CRI-033** subjective DCG) per sprint tags below.
2. **Local-first:** Tier 0 `pns_local_dev_parallel.ps1` · Tier 1 `pns_prerelease_gate.ps1 -SkipGradle` · Tier 2 `pns_verify_toolchain.ps1 -RunTests` · Tier 3 USB (one serial) · Tier 4 prerelease + `-IncludeUsb` before ship. See **`docs/LOCAL_FIRST_DEV_LOOP.md`**.
3. **Never ✅ without Appendix A** — host Tier 2 + `ReadLints`; device = `hfr-runs/` on **CPH2583** unless a named lane (e.g. H.7-OP13).
4. **USB hygiene:** capture **then** chrome **sequentially**; `force-stop` after every session (**`AGENTS.md`**).
5. **Hard rules:** No §4a `streamHints` or §2 RAW10-first without USB proof; DNG/chrome/fleet locks in **`.cursor/rules/`**; parity sweep requires **`-Mode Full|Delta`**.

Full rules (multi-agent, git, changelog): prior BUILD_PLAN §How agents must execute items 8–16 — unchanged policy, omitted here for brevity.

---

### Global toolkit

| Gate | Script |
|------|--------|
| Host + tests | `pns_verify_toolchain.ps1 -RunTests` |
| RAW still | `pns_capture_pipeline_verify.ps1` |
| Chrome UX | `pns_chrome_ux_gate.ps1` (`-FocalMmSlot` tele proof) |
| Highlight (H) metering | `pns_highlight_meter_verify.ps1` |
| Aux DNG openability | `pns_aux_dng_capture_analyze.ps1` |
| Milestone H host | `pns_milestone_h_host_gate.ps1` |
| Pre-release | `pns_prerelease_gate.ps1` (`-IncludeUsb`) |
| Fleet matrix | `pns_fleet_matrix_scan.ps1` · `pns_fleet_parity_sweep.ps1 -Mode Delta` |
| M28 peer benchmark | `pns_camera_app_pipeline_scan.ps1` (Sprint **28.0**) |
| Still export closure | `pns_still_export_verify.ps1` · `pns_independent_tonal_verify.ps1` · `pns_mono_capture_verify.ps1` |
| Video format closure | `pns_video_format_test.ps1` · `pns_av1_parity_verify.ps1` · `pns_4k_regular_verify.ps1` |
| HDR10 DCG | `pns_video_hdr10_metadata_verify.ps1` |
| Release | `pns_github_release.ps1` — **`.cursor/skills/github-release/SKILL.md`** |

Full index: **`AGENTS.md`**.

---

## Sprint IDEAS-2026-08-21m — USB webcam mode ✅ CLOSED (agent)

- ✅ `[AGENT]` Tray Photo → Video → Webcam; `PnsUsbWebcam` USB_STATE + UVC/RNDIS/NCM
- ✅ `[AGENT]` Open USB Webcam settings (Windows inbox `usbvideo.sys` / USB Video Device)
- ✅ `[AGENT]` Same-cable MJPEG via USB tether + `scripts/pns_usb_webcam_windows.ps1` (`adb forward`)

**USB:** Windows Camera listed **Android Webcam** (`usbvideo.sys`) after Lineage **USB → Webcam** / `svc usb setFunctions uvc`. P&S cannot own kernel UVC (`DeviceAsWebcam` is system-signed). Portrait chrome geometry unchanged except the locked tray toggle now has a third **Webcam** state (explicit request).

---

## Sprint IDEAS-2026-08-21k — Library desk + Wear + HDMI ✅ CLOSED (agent)

- ✅ `[AGENT]` Wear OS `:wear` remote **or** timer (BLE rescan, cancel, vibrate; LAN `/remote`) — no Play Services
- ✅ `[AGENT]` HDMI Presentation clean feed + MJPEG `:28770` (settle/retry, last-good JPEG, HDMI-first rank, no 2nd session)
- ✅ `[AGENT]` Gallery desk: compare, cull, days, keywords, travel, trim/frame, bake, export, vault, publish, proofing, redact, evidence, bug pack
- ✅ `[AGENT]` Geotag Off/Coarse/Precise · recipes · interval ramp · imported LUT + negative invert · XMP sidecar
- ✅ `[AGENT]` Landscape adaptive chrome (portrait weights unchanged) · airplane-safe prompts
- ⏭ Guest / kids mode skipped (user)

**USB:** Wear / HDMI / landscape not USB-proven. DNG locks unchanged. Guest mode not shipped.

---

## Sprint IDEAS-2026-08-21j — Review + session + product systems ✅ CLOSED (agent)

- ✅ `[AGENT]` Gallery pair / stack / share format / 30s undo / file card / TalkBack labels
- ✅ `[AGENT]` Capture journal + last-good session hint on camera disconnect
- ✅ `[AGENT]` Remaining-shots estimate + power profile + “why the finder changed”
- ✅ `[AGENT]` Widget one-tap still (`pointandshoot://preview?shoot=1`) + LAN roll 200
- ✅ `[AGENT]` Chapter marks while recording · settings pack share · privacy receipt
- ✅ `[AGENT]` Interval ramp / focus stack / motion trip / geotag mode helpers (wired where safe)

**USB:** gallery / widget / disconnect toast not USB-proven. Portrait chrome geometry and DNG locks unchanged. Landscape finder / foldable two-pane / in-app DNG develop left as chrome-unlock follow-ups.

---

## Sprint IDEAS-2026-08-21i — Install harden + storage + prompt honesty ✅ CLOSED (agent)

- ✅ `[AGENT]` Pin APK dest under `cache/updates` (safe `.apk` name, ZIP magic, 150 MB cap)
- ✅ `[AGENT]` Null archive info / timeout-IO stay blocked (no GitHub browser fallback)
- ✅ `[AGENT]` One in-flight install; progress shows received / total MB; prune leftover cache
- ✅ `[AGENT]` Still-queue refuse + abort; video-lapse uses encode remaining; few-stills toast
- ✅ `[AGENT]` Hold-burst RAW+JPEG dual-file budget; NightScape / BKT re-check each frame
- ✅ `[AGENT]` Shared `PreviewFreeSpace` StatFs helper (fail open)
- ✅ `[AGENT]` Hide donate/update during hold-burst, still-queue, BKT, NightScape, install UI
- ✅ `[AGENT]` Skip auto-check in Battery Saver / Wi-Fi-only / in-flight install; 403/429 toast

**USB:** install / storage / prompt flags not USB-proven. No chrome-geometry or DNG lock change.

---

## Sprint IDEAS-2026-08-21h — APK space + timed-capture storage ✅ CLOSED (agent)

- ✅ `[AGENT]` Refuse APK download when cache cannot hold the asset
- ✅ `[AGENT]` Scale intervalometer tick by planned still (HDR / NightScape / burst / BKT)
- ✅ `[AGENT]` Preflight hold-burst before the first shot
- ✅ `[AGENT]` Toast at ~5 minutes of video left even if overlay is hidden
- ✅ `[AGENT]` Reuse cached APK when SHA-256 already matches
- ✅ `[AGENT]` HTTPS-only `networkSecurityConfig` (loopback cleartext for tether)
- ✅ `[AGENT]` Hide launch donate/update during intervalometer or self-timer
- ✅ `[AGENT]` Toast when APK download is blocked (redirect / size / space)

**USB:** storage / 5-minute toast not USB-proven. No session-create or chrome-geometry change.

---

## Sprint IDEAS-2026-08-21g — Bracket storage + install redirects ✅ CLOSED (agent)

- ✅ `[AGENT]` Scale still-storage floor for BKT / AE bracket
- ✅ `[AGENT]` Allowlist APK download redirect hosts
- ✅ `[AGENT]` Stop in-app video when remaining time hits 0
- ✅ `[AGENT]` Re-check storage on intervalometer tick and hold-burst shot
- ✅ `[AGENT]` Re-check storage when the self-timer fires
- ✅ `[AGENT]` Refuse download when GitHub size and Content-Length disagree
- ✅ `[AGENT]` Clear pending install when installed version already matches
- ✅ `[AGENT]` Hide launch donate/update while Settings or About is open

**USB:** storage re-check / empty-card stop not USB-proven. No session-create or chrome-geometry change.

---

## Sprint IDEAS-2026-08-21f — Storage scale + install guards ✅ CLOSED (agent)

- ✅ `[AGENT]` Scale still-storage floor by planned frame count
- ✅ `[AGENT]` Reject APK when bytes ≠ GitHub `assets[].size`
- ✅ `[AGENT]` Refuse video record start under 1 minute remaining
- ✅ `[AGENT]` Refuse install when APK versionCode ≤ installed
- ✅ `[AGENT]` Sanitize cached GitHub release notes
- ✅ `[AGENT]` Intervalometer preflight (two still floors)
- ✅ `[AGENT]` About → Open LICENSE
- ✅ `[AGENT]` Short SHA-256 on Install dialog

**USB:** storage refuse / video-start refuse not USB-proven. No session-create change.

---

## Sprint IDEAS-2026-08-21e — Storage, credits, install hygiene ✅ CLOSED (agent)

- ✅ `[AGENT]` Block still save below one DNG-sized free-space floor
- ✅ `[AGENT]` Skip launch donate/update dialogs while recording
- ✅ `[AGENT]` Metered confirm uses GitHub asset `size`
- ✅ `[AGENT]` Reject truncated APK vs Content-Length
- ✅ `[AGENT]` About → NOTICE / licenses
- ✅ `[AGENT]` Prune stale `cache/updates` APKs
- ✅ `[AGENT]` Settings search for What’s new
- ✅ `[AGENT]` Optional JPEG artist / copyright (never on DNG)

**USB:** still-storage toast not USB-proven (fail-open if StatFs missing). No session-create change.

---

## Sprint IDEAS-2026-08-21d — Install follow-through ✅ CLOSED (agent)

- ✅ `[AGENT]` Dismiss update only after installer starts
- ✅ `[AGENT]` Same-signer check vs installed cert
- ✅ `[AGENT]` Match APK `versionName` to prompted GitHub version
- ✅ `[AGENT]` Resume Install after unknown-sources Settings
- ✅ `[AGENT]` Cancel in-flight APK download
- ✅ `[AGENT]` Cached notes on the Update dialog
- ✅ `[AGENT]` About last successful check time
- ✅ `[AGENT]` Publish SHA-256 into F-Droid Builds comment

**USB:** not required (no session-create change).

---

## Sprint IDEAS-2026-08-21c — Install safety + About honesty ✅ CLOSED (agent)

- ✅ `[AGENT]` Verify APK package is `dev.pointandshoot` before installer
- ✅ `[AGENT]` Confirm before APK download on metered networks
- ✅ `[AGENT]` Download progress on Install
- ✅ `[AGENT]` Publish `{apk}.sha256` and verify when present
- ✅ `[AGENT]` About → Add in Obtainium
- ✅ `[AGENT]` In-app What’s new from cached GitHub `body`
- ✅ `[AGENT]` Toast when opening unknown-sources Settings
- ✅ `[AGENT]` About line when the install is debug/test-signed

**USB:** not required (no session-create change).

---

## Sprint IDEAS-2026-08-21b — Update/release follow-through ✅ CLOSED (agent)

**Objective:** Ship the second `/ideas` set (tagged What's new, no publish auto-bump, PackageInstaller, About GitHub version, F-Droid CurrentVersion, ETag, changelog parser test, Keep-a-Changelog order).

- ✅ `[AGENT]` About What's new uses tagged release URL
- ✅ `[AGENT]` `-Publish -SkipPrepare` without `-Tag` uses gradle versionName
- ✅ `[AGENT]` Install uses PackageInstaller (browser fallback)
- ✅ `[AGENT]` About shows last-known GitHub APK version
- ✅ `[AGENT]` F-Droid `CurrentVersion` / `CurrentVersionCode`
- ✅ `[AGENT]` Conditional GitHub fetch (`If-None-Match`)
- ✅ `[AGENT]` Host test for `Get-ChangelogSectionForTag`
- ✅ `[AGENT]` CHANGELOG Unreleased-first, dated sections newest-first

**USB:** not required (no session-create change).

---

## Sprint IDEAS-2026-08-21 — Donate/update follow-through ✅ CLOSED (agent)

**Objective:** Ship the eight `/ideas` items after beta.21 (prerelease-aware check, failed-fetch quota, About check, F-Droid prepare sync, changelog body parser, metered skip, `/ideas`+`/coach`, HDR10 §6 honesty).

- ✅ `[AGENT]` Prerelease-aware GitHub `/releases` list + APK-filename pick
- ✅ `[AGENT]` Do not mark the daily check after a failed fetch
- ✅ `[AGENT]` About → Check for updates (same Install / Later dialog)
- ✅ `[AGENT]` `-PrepareOnly` syncs F-Droid `metadata.yml` + changelog excerpt
- ✅ `[AGENT]` `Get-ChangelogSectionForTag` stops at `## Unreleased`
- ✅ `[AGENT]` Skip automatic GitHub fetch on metered / no-network
- ✅ `[AGENT]` Register `/ideas` and `/coach` (23 atomic + 5 super)
- ✅ `[AGENT]` HDR10 live preview remains **IN TREE**, default **off** (no default-on without USB)

**USB:** not required (no session-create change; §6 default unchanged).

---

## Sprint AUDIT6-2026-08-03 — Weekly health + Trivy Netty ✅ CLOSED

Archive: [BUILD_PLAN_COMPLETED.md — Sprint AUDIT6](BUILD_PLAN_COMPLETED.md#sprint-audit6-2026-08-03--weekly-health--trivy-netty-agent-closed).

---

## Sprint AUDIT5-2026-07-11 — Post-beta.16 security scan + memory ✅ CLOSED

Archive: [BUILD_PLAN_COMPLETED.md — Sprint AUDIT5](BUILD_PLAN_COMPLETED.md#sprint-audit5-2026-07-11--post-beta16-security-scan--memory-agent-closed).

---

## Sprint AUDIT4-2026-06-21 — H metering + QR restore ✅ CLOSED

Archive: [BUILD_PLAN_COMPLETED.md — Sprint AUDIT4](BUILD_PLAN_COMPLETED.md#sprint-audit4-2026-06-21--h-metering--qr-restore-agent-closed).

---

## Sprint AUDIT-2026-06-21 — Post-M28 hygiene ✅ CLOSED

Archive: [BUILD_PLAN_COMPLETED.md — Sprint AUDIT-2026-06-21](BUILD_PLAN_COMPLETED.md#sprint-audit-2026-06-21--post-m28-hygiene-agent-closed).

---

## Sprint H-RESTORE-2026-06-19 — Highlight (H) metering + pure-HAL DNG ✅ CLOSED

Archive: [BUILD_PLAN_COMPLETED.md — Sprint H-RESTORE](BUILD_PLAN_COMPLETED.md#sprint-h-restore-2026-06-19--highlight-h-metering--pure-hal-dng-agent-closed).

---

## Sprint AUDIT3-2026-06-18 — CI green + overlay wiring ✅ CLOSED

Archive: [BUILD_PLAN_COMPLETED.md — Sprint AUDIT3](BUILD_PLAN_COMPLETED.md#sprint-audit3-2026-06-18--ci-green--overlay-wiring-agent-closed).

---

## Sprint AUDIT2-2026-06-18 — Post-ship hygiene ✅ CLOSED

Archive: [BUILD_PLAN_COMPLETED.md — Sprint AUDIT2](BUILD_PLAN_COMPLETED.md#sprint-audit2-2026-06-18--post-ship-hygiene-agent-closed).

---

## Sprint AUDIT-2026-06-18 — Weekly triage ✅ CLOSED

Archive: [BUILD_PLAN_COMPLETED.md — Sprint AUDIT-2026-06-18](BUILD_PLAN_COMPLETED.md#sprint-audit-2026-06-18--ci--tm-ship-agent-closed).

---

## Milestone T — Template alignment ✅ CLOSED

Archive: [BUILD_PLAN_COMPLETED.md — Milestone T](BUILD_PLAN_COMPLETED.md#milestone-t--template-alignment).

---

## Sprint TM — Template Migration (bootstrap + modularization) ✅ CLOSED

Archive: [BUILD_PLAN_COMPLETED.md — Sprint TM](BUILD_PLAN_COMPLETED.md#sprint-tm--template-migration).

---

## Sprint BOOTSTRAP-0.15 — Template alignment (agent-project-bootstrap v0.15.0) ✅ CLOSED (agent)

**Shipped:** [v0.14.0-beta.19](https://github.com/edwardlthompson/point-and-shoot/releases/tag/v0.14.0-beta.19) · record [`docs/BOOTSTRAP_ALIGNMENT.md`](docs/BOOTSTRAP_ALIGNMENT.md).

### Human & device (after automation)

- 🔲 `[HUMAN]` Confirm whether OpenSSF Scorecard should become a required branch-protection check
- 🔲 `[HUMAN]` Dependabot backlog triage (`/dependabot`) — unchanged product maintenance
- ✅ `[ADB]` No USB required for bootstrap process alignment (N/A — host-only)
- 🔲 `[AUTO]` Weekly Scorecard / template-update cadence on `main`

Archive note: agent Sequential work closed with beta.19; leave Human rows open above.

---

## Sprint DNG-FLEET-EXPOSURE-2026-07 — UW RAW exposure bisect (agent)

**Objective:** Close leaf/aux **RAW still underexposure** (OP13 UW 14 mm black-crush with TIFF OK) via a **fleet-generic** exposure-first matrix; record dead ends so ASN/map/CM mistakes are not repeated.

| SoT | Path |
|-----|------|
| Plan | `.cursor/plans/fleet_dng_bisect_matrix_30e23d6a.plan.md` |
| Matrix | [`docs/DNG_FLEET_EXPOSURE_BISECT_MATRIX.md`](docs/DNG_FLEET_EXPOSURE_BISECT_MATRIX.md) |
| Host metric | `scripts/dng_same_scene_exposure_metric.py` |
| Same-scene UI | `scripts/pns_proshot_pns_same_scene_ps01.ps1` (calibrated ProShot taps) |
| Dead ends | [`docs/AGENT_REGRESSION_MEMORY.md`](docs/AGENT_REGRESSION_MEMORY.md) REG-20260713-001…003 · [`docs/PROSHOT_APK_FLEET_ANALYSIS.md`](docs/PROSHOT_APK_FLEET_ANALYSIS.md) |

**Status (2026-07-13):** **PS01 process shipped** as fleet default (REG-20260713-004). Full PS01 ADB extras remain optional. OP13 residual shadow crush deferred.

### Fleet promotion plan (main app)

| Step | Work | Status |
|------|------|--------|
| **P0** | Keep E\* EV bisect ADB-only | **done** |
| **P1** | CPH2583 baseline + PS01 mosaics healthy | **done** 2026-07-13 (`b5214fc6`) |
| **P2** | Default = `ProShotStyleAePrecapture` **process only** | **done** (beta.17) |
| **P3** | OP13 residual shadow/`frac&lt;bl` — deferred | deferred |
| **P4** | REG-20260713-004 + settings + release | **done** |

**Gates:** `pns_capture_pipeline_verify.ps1` · `pns_aux_dng_capture_analyze.ps1` · `pns_proshot_pns_same_scene_ps01.ps1` · never capture ∥ chrome · force-stop after USB.

---

## Milestone H — Human & publication

**Objective:** Irreducible human judgment; agent hygiene + release when owner approves.

### Active at a glance

| Lane | Open work |
|------|-----------|
| **Agent** | **DNG-FLEET-EXPOSURE-2026-07** UW RAW exposure matrix · **H.6** CRI-032 overlay pixel gate (face in frame) |
| **Human** | **H.2–H.5** calibration / accounts / store copy · **H.6/H.8** subjective UX · **H.7-OP13** ACR · **H.9** PRIVACY / signing |

**CRI program:** **H.CRI-0…6** + **H.CRI-5** + **H-RESTORE** archived → [COMPLETED](BUILD_PLAN_COMPLETED.md#milestone-h--completed-sprints). **H.CRI-7** = human (**CRI-032/033/034/035**).

**Last CPH2583 USB (2026-06-21):** AUDIT4 — `highlight_meter_verify_20260621_152031` · `qr_scan_verify_20260621_152424` PASS · ship **beta.16**. **This host (2026-07-11):** online ADB **`8bf09993`** (OP13); env still `b5214fc6`.

---

### Code review recommendations (2026-08-03 AUDIT6)

Ephemeral: `CODE_REVIEW.md` (gitignored). Host: Tier 0 **8/8 PASS** · Tier 2 **PASS**. Trivy Netty **4.1.136.Final** shipped (confirm next Security scan schedule).

| Priority | Item | Owner | Notes |
|----------|------|-------|-------|
| **P1** | Human Milestone H closure | Human | **CRI-032** eye-AF · **CRI-033** DCG · **CRI-034** store/PRIVACY · **CRI-035** OP13 ACR |
| **P1** | Production signing | Human | **H.9** — still debug-key fallback without keystore |
| **P2** | Dependabot backlog (10 PRs) | Maintainer | `/dependabot`; gate AGP/Compose |
| **P2** | PreviewEngineScreen monolith | Deferred | ~22k lines — incremental ADR-0009 only |

### Code review recommendations (2026-07-11 AUDIT5 — superseded)

**AUDIT5** gitleaks allowlist → [COMPLETED](BUILD_PLAN_COMPLETED.md#sprint-audit5-2026-07-11--post-beta16-security-scan--memory-agent-closed).
---

### Sprint H.CRI-7 — Human + release (cross-tagged)

| CRI | Home | Status |
|-----|------|--------|
| **032** | H.6, H.8.1 | Agent — HUD seed fix shipped (AUDIT3); pixel gate **FAIL** without face in frame (`mlFaceHud boxes=0`) |
| **033** | H.8.3 | Human — H.265 DCG @4K colors (fail 2026-05-26) |
| **034** | H.5, H.9 | Human — store copy + PRIVACY/metadata |
| **035** | H.7-OP13 | Human — ACR on OP13 aux DNGs (integrity PASS; scene parity FAIL) |
| Release | H.9 | ❌ Blocked on owner sign-off |

---

### Sprint H.2 — Physical calibration

- 🔲 **[HUMAN]** ColorChecker under controlled illuminant
- ❌ **[AGENT]** `pns_colorchecker_de2000_gate.py` — **blocked on HUMAN**

### Sprint H.3 — Account ownership

- 🔲 **[HUMAN]** Owner GitLab login confirmed

### Sprint H.4 — Signing authority

- 🔲 **[HUMAN]** Keystore custody confirmed

### Sprint H.5 — Publication & community

- 🔲 **[HUMAN] CRI-034** Store listing — `metadata/en-US/` + Play listing
- 🔲 **[HUMAN]** Community announcements

### Sprint H.6 — Subjective UX

- 🔲 **[AGENT] CRI-032** `pns_eye_af_pixel_gate.ps1` — **FAIL** without face in frame (`eye_af_pixel_gate_20260618_183819`; ADB `eyeAfOverlay` seed fixed in AUDIT3)
- 🔲 **[HUMAN]** HUD / LUT aesthetics · immersive mode feel

### Sprint H.7-OP13 — Optional OP13 regression lane

> Legacy device only. Agent automation closed → [COMPLETED](BUILD_PLAN_COMPLETED.md#sprint-h7-op13--optional-op13-regression-lane-agent-closed-2026-06-13).

- 🔲 **[HUMAN] CRI-035** ACR sign-off on `hfr-runs/aux_dng_capture_analyze_20260613_014424`

### Sprint H.8 — M14 + M15 subjective sign-off

- 🔲 **[HUMAN] H.8.1** Eye/face overlay on glass
- 🔲 **[HUMAN] H.8.2** Dual-video stacked framing
- 🔲 **[HUMAN] H.8.3 CRI-033** All codecs/scenes — **H.265 DCG @4K** colors fail
- 🔲 **[HUMAN] H.8.5** False color on grey card + highlight scene

### Sprint H.9 — Publication / signing handoff

- 🔲 **[HUMAN] CRI-034** `PRIVACY.md`, `NOTICE`, F-Droid `metadata/` creative review
- 🔲 **[HUMAN]** `leaderboard-ingest/config/signing_pins.json` release cert SHA-256
- ✅ **[AGENT]** GitHub Pages smoke after deploy — `pns_github_pages_smoke.ps1` + `gh run list` **PASS** (2026-06-18)
- ✅ **[AGENT]** Release cut — `v0.14.0-beta.12` minified release APK (~48 MB) on GitHub (2026-06-18; debug-key signed)

**Milestone H gate:** Human checklist **H.2–H.9** + `pns_prerelease_gate.ps1 -IncludeUsb` on CPH2583.

---

## Milestone 28 — Feature richness + pipeline parity ✅ CLOSED

Archive: [BUILD_PLAN_COMPLETED.md — Milestone 28](BUILD_PLAN_COMPLETED.md#milestone-28--feature-richness-waves-a-d) (Waves A–D · **beta.13–beta.15** · catalog **v6** · USB CPH2583 `b5214fc6` 2026-06-21).

---

Parity intake refresh: `pns_fleet_parity_sweep.ps1 -Mode Delta` · promote `PBI-*` from `docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json`.

<!-- AUTO_HAL_HONESTY_GAPS_START -->
- Generated: 2026-06-21T04:37:53Z
- Source: `hfr-runs/parity_sweep_20260621_043558`
- Open honesty gaps: **0**
<!-- AUTO_HAL_HONESTY_GAPS_END -->

---

## Appendix A — Verification protocol

1. Tier 2 `pns_verify_toolchain.ps1 -RunTests` (or sprint-specific gate)
2. `ReadLints` on touched Kotlin
3. USB: artifact under `hfr-runs/` on CPH2583; `force-stop` after session
4. User-visible ship: `CHANGELOG.md` + `changelog_coverage.v1.json` (`pns_changelog_gate.ps1`)

## Appendix B — Shipped baseline (summary)

Template alignment (T), CRI program (0–6), H.CRI-5 monolith extraction, **H-RESTORE** (H metering + pure-HAL DNG), fleet parity (M18–M27), M14 chrome/video, **Milestone 28** (Waves A–D), DNG loadability locks — **BUILD_PLAN_COMPLETED.md** · **KNOWLEDGE_BASE.md**. **Active:** Milestone H (human).

## Appendix C — Quick grep

| Need | Pattern |
|------|---------|
| Open human | `^- 🔲 \[HUMAN\]` |
| Open agent | `^- 🔲 \[AGENT\]` |
| Open ADB | `^- 🔲 \[ADB\]` |
| Open AUTO | `^- 🔲 \[AUTO\]` |
| Blocked | `^- ❌` |
| Done | `^- ✅` |
| Sprint headers | `^### Sprint` |

---

## Document control

- **Version:** 2026-08-03 — **AUDIT6** (Trivy Netty 4.1.136.Final + hygiene); **Milestone H** remains active.
- **Owner:** Maintainer closes Milestone H after human checklist + release sign-off.

---
