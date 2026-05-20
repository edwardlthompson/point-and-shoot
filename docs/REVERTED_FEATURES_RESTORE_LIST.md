# Reverted features — bisect log and restore checklist

> **STOP — agents & humans (May 2026):** Do **not** tie **`automationSuppressFacePipeline`** to **`pns_preview_raw_count`** / sequential RAW-only automation. That suppressed the H-dial **YUV** path (`wantYuv=false`), broke **RAW still** session create on **CPH2655-class** devices (`SESSION_CREATE_THROW` / `CAMERA_DISCONNECTED`), and left scripted capture red until reverted. **Rule:** `automationSuppressFacePipeline` **only** when **`adbBracketPattern != null`**. **Also:** do **not** bulk-restore every bisect § without **per-step** **`pns_photo_capture_verify.ps1`** — see **§8** (CPH2655 incremental proof) and the **§8** subsection **What agents must avoid** (fleet checklist: **§4a** stream hints, **§2** RAW10-first `Default`, bulk restore). See **`README.md`** (STOP banner), **`AGENTS.md`**, **`BUILD_PLAN.md`** item **11**. After restoring capture code, run **`scripts/pns_capture_restore_verified.ps1`** on USB.

**Purpose:** Track capture-pipeline bisect steps so nothing is lost permanently. When the root cause is confirmed, re-apply rows marked **REVERTED** from this file (or only the subsets that are safe on your fleet).

**Automated gate (BUILD_PLAN item 11):** from repo root run **`.\scripts\pns_capture_pipeline_verify.ps1 -BisectStep <n>`** (forwards the same flags as **`pns_photo_capture_verify.ps1`**). It appends **`docs/CAPTURE_PIPELINE_VERIFY_HISTORY.jsonl`** and overwrites **`docs/CAPTURE_PIPELINE_VERIFY_LATEST.json`** with **`pass`**, **`exitCode`**, **`gitRevShort`**, and the latest **`hfr-runs/photo_capture_verify_*`** path.

**Full bisect loop (USB):** **`.\scripts\pns_capture_bisect_device.ps1`** applies cumulative steps **1..UpToStep** from **`docs/REVERTED_FEATURES_RESTORE_LIST.md`**, **`assembleDebug`**, and **`pns_capture_pipeline_verify`** per step; writes **`hfr-runs/capture_bisect_device_*/report.md`**. Dot-source **`pns_resolve_adb.ps1 -PrependToPath`** first; optional **`-DryRun`**, **`-Fast`**, **`-MaxAttempts 2`**.

**Related:** `BUILD_PLAN.md` — *How agents must execute* item **11** (capture regression gate).

---

## Bisect order (by likelihood)

| Step | Likelihood | What changed (original intent) | Status |
|------|------------|--------------------------------|--------|
| **1** | Highest | **`PreviewStabilization.applyToRequest`** on **RAW still** and **bracket still** `TEMPLATE_STILL_CAPTURE` builders (lens OIS default-on still path; Milestone 3/4/7) | **RESTORED** (May 2026, USB **8bf09993**) — with §4a off + §2 bisected; see §8 |
| **2** | High | **`RawCaptureSupport`** default **RAW12 → RAW10 → RAW_SENSOR** (Milestone 10.1 ship); bisect **#2** → **RAW12 → RAW_SENSOR → RAW10** | **KEEP bisect #2** on CPH2655 fleet (May 2026): RAW10-first caused **`DngCreator` Unsupported image format 37**; see §8 |
| **3** | Medium | Imaging profile **`remember`**: Milestone 9 **`runCatching`** / singleton-touch + **`StandardPro` fallback**; **`SideEffect { setImagingProfileForStreams }`** kept | **In tree** (unchanged) |
| **4** | Medium | **§4a** stream hints off. **§4b** 0 ms debounce: **rejected** (crash). **§4e:** scripted post-**`stopRepeating`** delay **≥420 ms**. **§4d+:** texture / Ultra-Max settle — **pending** | **§4a KEEP bisect (off)** on CPH2655 (May 2026): stream hints on → RAW still timeout; **§4e** **shipped** |
| **5** | Lower | **`PreviewPostRawSensitivity.applyIfCompatible`** on RAW still + bracket still `TEMPLATE_STILL_CAPTURE` (default pref off) | **RESTORED** (May 2026, USB **8bf09993**) — with §4a off + §2 bisected; see §8 |
| **6** | Lower | **`PreviewHdrSessionSupport`** dynamic range on preview output (`enableHdr10LivePreview`, default off) | **Pending** |

After each step: run **`scripts/pns_capture_pipeline_verify.ps1 -BisectStep <n>`** (or **`scripts/pns_photo_capture_verify.ps1`**) and your usual **`pns_milestone6_gate.ps1`** when appropriate. If capture succeeds, the last **REVERTED** step is the prime suspect; consider a **narrow fix** (e.g. OIS off for RAW still only) instead of keeping the full revert.

---

## §1 — PreviewStabilization on still captures (**RESTORED** on CPH2655 max combo — see §8)

**Introduced:** Milestone 3/4/7 (`7bf0723`).

**Rationale for revert:** Before that commit, still `CaptureRequest` builders did not call `PreviewStabilization`. OEM HALs may time out or raise `ERROR_CAMERA_DEVICE` when lens OIS (or related keys) are set on RAW still templates.

**File:** `app/src/main/java/dev/pointandshoot/PreviewEngineScreen.kt`

**Locations removed:**

1. **`captureRawStill`** — after `PreviewAeAntibanding.applyToRequest(this, chars)`, the block calling `PreviewStabilization.applyToRequest(..., isStillCapture = true)` was removed.
2. **Bracket `scheduleShot`** — same removal after `PreviewAeAntibanding.applyToRequest(this, chars)`.

**Preview and macro session paths unchanged:** `PreviewStabilization.applyToRequest` remains on repeating preview requests and macro session-parameter probe builders.

### Restore (copy back both sites)

**A) `captureRawStill`** — after `PreviewAeAntibanding.applyToRequest(this, chars)`:

```kotlin
                PreviewStabilization.applyToRequest(
                    this,
                    chars,
                    readHudCapturePrefs(),
                    previewFpsRange = null,
                    manualSensor = manualSensorStill,
                    isStillCapture = true,
                )
```

**B) Bracket `scheduleShot`** — after `PreviewAeAntibanding.applyToRequest(this, chars)`:

```kotlin
                    PreviewStabilization.applyToRequest(
                        this,
                        chars,
                        readHudCapturePrefs(),
                        previewFpsRange = null,
                        manualSensor = manualSensorBracket,
                        isStillCapture = true,
                    )
```

**Safer alternative than full restore:** Extend `PreviewStabilization.applyToRequest` (or still-only call sites) to **skip lens OIS** when the active profile is RAW / DNG still, or honor a new `HudSettings` flag, then keep preview stabilization unchanged.

---

## §2 — Default RAW tier RAW10 before RAW_SENSOR (**KEEP bisect #2** on CPH2655)

**Introduced:** `86bcbdc` (Milestone 10.1): **`RawStreamPreference.Default`** used **RAW12 → RAW10 → RAW_SENSOR**.

**Rationale for revert:** On **CPH2655** (`8bf09993`, May 2026), that order selected **RAW10** (`ImageFormat` **37**). Scripted save failed: **`DngCreator.writeImage`** → **`Unsupported image format 37`** (`PNS.CaptureStill` **`save ok=false`**).

**File:** `app/src/main/java/dev/pointandshoot/RawCaptureSupport.kt` — `pickRawOutputFromMaps`, branch **`RawStreamPreference.Default`**.

**In tree:** **RAW12 → RAW_SENSOR → RAW10** (bisect #2 order).

### Restore (Milestone 10.1 ordering) — **device-proof only**

```kotlin
            RawStreamPreference.Default ->
                largest(raw12)?.let { ImageFormat.RAW12 to it }
                    ?: largest(raw10)?.let { ImageFormat.RAW10 to it }
                    ?: largest(rawSensor)?.let { ImageFormat.RAW_SENSOR to it }
```

---

## §3 — Imaging profile `runCatching` / singleton-touch hardening (**REVERTED** / bisect #3, partial)

**Introduced:** `b5cd942` (Milestone 9).

**Rationale for revert:** Bisect isolates whether **`runCatching { … r.id … }.getOrElse { StandardPro }`** + **`listOf(StandardPro, UltraMax)`** touch causes bad profile / timing on some OEM RAW still paths.

**In tree:** **`SideEffect { controller.setImagingProfileForStreams(imagingProfile) }`** is **kept** — it is the only caller syncing **`imagingProfileForStreams`** for JPEG companion / stream shape; removing it breaks profile switches without a replacement.

**File:** `app/src/main/java/dev/pointandshoot/PreviewEngineScreen.kt` — `var imagingProfile by remember(adbInitialImagingProfile) { … }`.

### Restore (Milestone 9 `remember` block)

```kotlin
    var imagingProfile by remember(adbInitialImagingProfile) {
        // JVM: sealed `data object` singleton fields can be observed null during early companion init;
        // touch both before prefs / intent paths return an [ImagingProfile] (see [EncoderRoute.downgradedProfiles]).
        listOf(ImagingProfile.StandardPro, ImagingProfile.UltraMax)
        mutableStateOf(
            runCatching {
                val r = adbInitialImagingProfile ?: HudSettings.loadImagingProfile(context)
                // Touch [.id] so a null / half-built singleton fails here instead of in SideEffect → controller.
                r.id
                r
            }.getOrElse { ImagingProfile.StandardPro },
        )
    }
```

**Script:** **`scripts/pns_capture_bisect_device.ps1`** step **3** applies **`Apply-T3_ImagingProfileSimpleRemember`** (legacy → simple `mutableStateOf` + bisect comment; does **not** remove **`SideEffect`**).

---

## §4 — Session / Surface / restart debounce

**Introduced:** `7bf0723` (large `PreviewEngineScreen` / `PreviewController` change set).

### §4a — Stream use case hints on REGULAR session (**KEEP bisect / off** on CPH2655 scripted path)

**Rationale for revert:** On **CPH2655** (`8bf09993`, May 2026), restoring **`val streamHints = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU`** while **§1** was still bisected caused **RAW still timed out** / **`onError … error=4`** after **`captureRawStill afterStopRepeatingDebounceMs=420`**. With **§1** + **§5** restored and **§2** bisected, **§4a** on still failed the same gate.

**In tree:** `val streamHints = false` plus bisect comments before **`captureSessionAsyncConfigurePending`**.

### Restore (§4a) — **device-proof only**

```kotlin
            val streamHints = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
```

Do not merge without **`pns_photo_capture_verify.ps1`** on your target fleet.

### §4b — `maybeRestart` debounce = **0 ms** (**negative experiment — do not ship**)

**Tried (May 2026, CPH2655):** **`MAYBE_RESTART_DEBOUNCE_MS = 0L`** caused **`CameraAccessException` / `CAMERA_DISCONNECTED`** inside **`onConfigured` → `startRepeating` → `createCaptureRequest`** (fatal **`PNS.Cam`** thread). Likely **`closeCamera` / restart** coalescing race without the **48 ms** delay.

**In tree:** keep **`MAYBE_RESTART_DEBOUNCE_MS = 48L`** (Milestone behavior).

### §4e — Scripted RAW still: longer post-`stopRepeating` delay (**shipped forward fix**)

**Problem:** Some logical-camera HALs need extra time after **`stopRepeating()`** before **`capture`** accepts a RAW still; the default **160 ms** debounce was marginal on scripted cold paths.

**Fix:** When **`adbValidationShotLabel != null`**, use **`maxOf(RAW_STILL_AFTER_STOP_REPEATING_DEBOUNCE_MS, 420L)`** before **`sess.capture`**. In-app capture (no label) keeps **160 ms**.

**Log:** **`PNS.AdbValidation`** **`captureRawStill afterStopRepeatingDebounceMs=…`**.

### Restore (disable §4e)

Always use **`RAW_STILL_AFTER_STOP_REPEATING_DEBOUNCE_MS`** for **`postDelayed(fireStillCapture, …)`** with no **`shotTag`** branch.

### §4d+ — Texture `setDefaultBufferSize`, Ultra-Max ADB settle (**Pending**)

**Revert idea:** Use `git show 7bf0723 -- app/.../PreviewEngineScreen.kt` and reverse specific hunks only with device proof after §4a / §4e outcomes are known.

---

## §5 — PreviewPostRawSensitivity on still (**RESTORED** on CPH2655 max combo — see §8)

**Introduced:** `7bf0723`.

**Rationale for revert:** Even when the HUD pref is default-off, the call site may still affect request assembly or OEM HAL behavior; bisect removes it from **RAW still** and **bracket still** `TEMPLATE_STILL_CAPTURE` builders.

**File:** `app/src/main/java/dev/pointandshoot/PreviewEngineScreen.kt`

**Locations removed:** after `PreviewAeAntibanding.applyToRequest(this, chars)` in **`captureRawStill`** and in bracket **`scheduleShot`**, the block calling **`PreviewPostRawSensitivity.applyIfCompatible(...)`** was removed (preview repeating and other paths unchanged).

### Restore (both sites)

**A) `captureRawStill`** — after `PreviewAeAntibanding.applyToRequest(this, chars)` (before `RawStillProcessingHints.applyLinearRawFriendlyProcessing`):

```kotlin
                PreviewPostRawSensitivity.applyIfCompatible(
                    this,
                    chars,
                    readHudCapturePrefs(),
                    manualIsoOverride,
                    manualExposureNsOverride,
                )
```

**B) Bracket `scheduleShot`** — after `PreviewAeAntibanding.applyToRequest(this, chars)` (before `set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, aeInts[idx])`): same block as **A)** (bracket closure already defines `manualIsoOverride` / `manualExposureNsOverride`).

---

## §6 — HDR preview dynamic range on outputs (**Pending**)

**Introduced:** `7bf0723` / `PreviewHdrSessionSupport.kt`.

Default `enableHdr10LivePreview` is **off**. If enabled and implicated, keep pref off or remove `chosenPreviewDr` wiring after confirmation.

---

## §8 — Incremental restore proof (USB **CPH2655** **`8bf09993`**, 2026-05-13)

**Procedure:** Start from **`pns_capture_bisect_device.ps1 -UpToStep 5 -FromStep 5 -NoRestore`** (or equivalent **T1–T5** transforms on your snapshot), **`assembleDebug`**, then **`pns_photo_capture_verify.ps1 -Fast -MaxAttempts 2 -WaitSec 70`** — baseline must show **`captureRawStill 1/1 ok=true saved=`**. Re-apply each **undo** (restore) in isolation; **never** skip USB verify between hunks.

| Restore step | Symptom when enabled alone (relative to prior green state) | Gate |
|--------------|--------------------------------------------------------------|------|
| **§5** `PreviewPostRawSensitivity` on RAW + bracket still | *(none observed)* | **PASS** (`photo_capture_verify_20260513_035852`) |
| **§4a** `streamHints = SDK_INT >= TIRAMISU` | **`RAW still timed out`**, then **`onError … error=4`** | **FAIL** (`photo_capture_verify_20260513_040047`) |
| **§2** Milestone 10.1 **RAW10** before **RAW_SENSOR** | Capture completes but **`save ok=false err=Unsupported image format 37`** (`DngCreator`) | **FAIL** (`photo_capture_verify_20260513_040401`) |
| **§1** `PreviewStabilization` on still + bracket (with **§5** on, **§4a** off, **§2** bisected) | *(none observed)* | **PASS** (`photo_capture_verify_20260513_040723`) |

**Max verified combo for this device:** **§1** + **§5** restored; **§4a** and **§2** remain at bisect values; **§3** unchanged; **§4e** shipped debounce unchanged.

### What agents must avoid (until device-proven otherwise)

Use this as a **fleet checklist** for **CPH2655-class** / logical-primary stacks like **`8bf09993`**. Other devices may differ; still run **`pns_photo_capture_verify.ps1`** after any of these areas change.

| Avoid | Why (observed May 2026) |
|--------|-------------------------|
| **`val streamHints = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU`** on the REGULAR preview session path (`PreviewEngineScreen` → `createRegularCaptureSessionWithRetries` / bisect **§4a**) | Scripted RAW still **hangs** then **`RAW still timed out`**; camera **`onError … error=4`** (`ERROR_CAMERA_DEVICE`). HAL did not complete capture after **`afterStopRepeatingDebounceMs=420`**. Evidence: **`hfr-runs/photo_capture_verify_20260513_040047`**. |
| **`RawStreamPreference.Default` → RAW12 → RAW10 → RAW_SENSOR** (Milestone 10.1 order; bisect **§2** undo) in **`RawCaptureSupport.pickRawOutputFromMaps`** | **`Default`** picks **RAW10** (`ImageFormat` **37**). Buffer arrives, but **`DngCreator.writeImage`** throws **`Unsupported image format 37`** — current DNG saver path is **not** compatible with that format on this device. Evidence: **`hfr-runs/photo_capture_verify_20260513_040401`**. Use **`pns_preview_raw_stream`** / matrix prefs to probe RAW10; do not silently make **Default** RAW10-first without a **working** DNG path + USB proof. |
| **Bulk-restore** “all Milestone §1–§5 shipping hunks” in **one** change without **per-hunk** USB **`pns_photo_capture_verify`** | Combined regressions mask **which** hunk broke capture; this repo already saw **full restore red** then **incremental** isolation (this table). |
| **Assuming “Milestone doc = safe to merge”** for **`PreviewEngineScreen.kt`** / **`RawCaptureSupport.kt`** | **§1** and **§5** were safe to restore here **only** with **§4a** off and **§2** bisected. Treat **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** as **evidence-backed** constraints, not a blind revert checklist. |

**Safe to merge on this fleet without re-proving §4a/§2:** keep **`streamHints = false`** (plus bisect comments) for REGULAR session; keep **`Default`** as **RAW12 → RAW_SENSOR → RAW10**; **`pns_photo_capture_verify`** green after your diff.

---

## §7 — ADB automation evidence (CPH2655, `8bf09993`, May 2026)

Automated runs: **`pns_capture_pipeline_verify.ps1`**, **`pns_raw_capture_matrix.ps1 -Quick`**, and manual **`am start`** with **`pns_preview_camera_id`**.

| Finding | Evidence |
|--------|----------|
| **`wantYuv=true`** while **`suppressFacePipeline=true`** | Log **`PNS.PreviewSessionCtx`** showed **`wantYuv=true`** with scripted RAW; H-dial path did not gate on **`automationSuppressFacePipeline`**. **Fix shipped:** gate H / histogram / zebra YUV when automation suppresses the face pipeline (`PreviewEngineScreen` `wantYuv`). After fix, logs show **`wantYuv=false`**. |
| **Preview FPS 90** on scripted RAW cold start | **`selectedFps`** defaulted to **90** for photo-primary while **`DESIRED_FPS_DEFAULT_BEFORE_UI_SYNC`** was **60** until UI sync. **Fix shipped:** seed **`selectedFps=60`** when **`adbSequentialRawStills`** or bracket automation is active. Logs then show **`desiredFps=60`**. |
| **HAL still fails on this device** | With **`wantYuv=false`** and **`desiredFps=60`**, **`captureRawStill`** still ends **`RAW still timed out`** / **`onError … error=4`** for default seed **cameraId=2** and for **`pns_preview_camera_id=1`**. **`pns_preview_camera_id=0`**: **`No RAW buffer`** (different failure; likely no RAW stream on that id for this map). **Quick RAW matrix (4 cells):** all **`ok=false`**. |
| **Conclusion** | **§4e** longer post-**`stopRepeating`** delay for scripted RAW; **`pns_photo_capture_verify`** logcat fallbacks + **`-SweepCameraIds`**. Re-run gate on CPH2655. |

---

| Date (UTC) | Action |
|------------|--------|
| 2026-05-13 | Bisect **#2**: default **`RawStreamPreference.Default`** order **RAW12 → RAW_SENSOR → RAW10**; **`pns_raw_regression_bisect`** “wrong” variant flipped to **RAW12 → RAW10 → RAW_SENSOR**. |
| 2026-05-13 | Bisect **#3** (partial): imaging profile **`remember`** without **`runCatching`** / **`StandardPro` fallback**; **`SideEffect`** → **`setImagingProfileForStreams`** retained. Bisect **#5**: **`PreviewPostRawSensitivity`** removed from RAW still + bracket still templates. **`pns_capture_bisect_device.ps1`** T3 no longer strips **`SideEffect`**. |
| 2026-05-13 | **§8** incremental restore matrix on **`8bf09993`**: **§5**/**§1** green; **§4a**/**§2** ship-order red; **`pns_capture_bisect_device`** T3 **`fromLegacy`** switched to single-quoted here-string so **`Apply-T3`** matches Kotlin backticks. |

---

## §9 — Milestone 13 lock ladder (L2–L9)

**Purpose:** Record **USB-proven** still-DNG policy flips for **CPH2655**. Full runbook: **`docs/M13_3E_LOCK_BISECT_RUNBOOK.md`**. Openability ledger: **`docs/DNG_OPENABILITY_REGRESSIONS.md`**.

| Lock | Shipped default (May 2026) | Status | Notes |
|------|----------------------------|--------|-------|
| **L9** | **OFF** — no `LeafDngHalReconcile` / `useWideLeafCalibrationForAuxDng` on leaf | **SHIPPED (13.3g)** | Pure `DngCreator`; `ProShotPipelineContract`; gate **`dng_desktop_open_gate.py`**. Wide-cal only in **13.3h** bisect. |
| **L2** | `allowPhysicalTotalResultPairing=false` at save call sites | **KEEP (13.3e)** | E1: pairing **true** — open OK, parity FAIL; unused on leaf. |
| **L3** | `useOp13AsnReconcileOnly=false` | **SHIPPED** | E2: **true** no-op under pure ProShot save. |
| **L6** | `useHalColorCalibrationReconcile=false` | **SHIPPED** | E3: **true** no-op under pure ProShot save. |
| **L4** | `streamHints=false` (§4a) | **KEEP bisect** | E4: **true** — RAW still **timeout** 0/3 (§8). |
| **L5** | Default RAW **RAW12→RAW_SENSOR→RAW10** | **KEEP bisect** | E5: RAW10-first ineffective on leaf (`rawFmt=32` still). |
| **L7** | Preview JPEG hints **on** RAW still | **SHIPPED** | E6: skip hints — open OK, parity **worse**. |

**13.3g automated evidence (2026-05-19, `8bf09993`):** `hfr-runs/aux_dng_capture_analyze_20260519_155213/` — capture **3/3**, desktop open gate **PASS**, logcat `reconcile=false wideCal=false` per cam **3/2/4**. Human ACR **3/3** still required for gate close.

### 13.3h wide-cal bisect (H1–H3, `8bf09993`, 2026-05-20)

**Artifacts:** `hfr-runs/m13_3h_wide_cal_bisect_20260520_003542/` — orchestrator **`scripts/pns_m13_3h_wide_cal_bisect.ps1`**.

| Step | Flags | Capture 3/3 | Open gate | wide-cal reconcile (logcat) | Ship? |
|------|-------|-------------|-----------|-----------------------------|-------|
| **H1** | `useWideLeafCalibrationForAuxDng=true` | yes | **FAIL** — CM2[0,0]=1.4337 on UW+tele matches wide (R2 leak) | **no** |
| **H2** | H1 + `useOp13AsnReconcileOnly=true` | yes | **FAIL** (same leak) | **no** |
| **H3** | H2 + exposure latch (auto when wideCal) | yes | **FAIL** (same leak) | **no** |

**Conclusion:** Wide CM/FM on aux RAW **reproduces R2** on CPH2655 — automated open gate fails before ACR color scoring. **Keep L9 shipped:** `useWideLeafCalibrationForAuxDng=false`. Human ACR on H1–H3 DNGs optional (expected reject per May 2026 regression doc); do not promote without open gate **PASS**.

**H1 log needles (example):** `PNS.LeafDng: wide-cal reconcile auxCam=3 cm2_before=1.8549 cm2_after=1.4337`; tele `cm2_before=1.4253 cm2_after=1.4337`.

### 13.3e lock ladder bisect (E1–E6, `8bf09993`, 2026-05-20)

**Artifacts:** `hfr-runs/m13_3e_lock_bisect_20260520_005414/report.md` (consolidated); **`scripts/pns_m13_3e_lock_bisect.ps1`**.

| Step | Lock | Open gate | Capture | Ship? |
|------|------|-----------|---------|-------|
| E1 | L2 physical pairing **true** | PASS | 3/3 | **no** (unused on leaf; parity FAIL) |
| E2 | L3 ASN reconcile only | PASS | 3/3 | **no** (no-op under pure ProShot save) |
| E3 | L6 HAL cal reconcile | PASS | 3/3 | **no** (no-op under pure ProShot save) |
| E4 | L4 `streamHints=true` | — | **0/3** timeout | **no** (§4a regression) |
| E5 | L5 RAW10 before RAW_SENSOR on Default | PASS | 3/3 | **no** (leaf still `rawFmt=32`) |
| E6 | L7 skip JPEG hints on RAW still | PASS | 3/3 | **no** (parity worse) |

**Conclusion:** No **L2–L7** flip fixes aux color on CPH2655 without breaking capture or openability. **Shipped:** **13.3g** table (L9 off, L4/L5 bisect defaults, pairing **false** at call sites).
