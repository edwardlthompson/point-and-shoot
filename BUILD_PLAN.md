## Build plan (Point & Shoot)

**Purpose:** Single roadmap for shipping the Parts 1–5 spec with **milestones → sprints → gates**. Execution order: **foundations → probes → mapping → capture engine (incl. Milestone 4 Sprint 4.5: system camera intents, BKT shutter parity, hardware JPEG tuning) → HUD/UX (Milestone 9) → color/LUT → quality bar → CI automation → Milestone 10 (post-M9 backlog) → Milestone 11 (WB, focal routing, face/eye, in-app video) → human publication (Milestone H).**

**Living docs:** `PROBE_BUILD_PLAN.md` (§5 audit log; **§6** probe/infra checklist ↔ **milestones** mapping table), `CHANGELOG.md`, `CLI_BUILD_AND_SIDELOAD.md`, `DODGE_PROFILE.md`, `COLOR_PIPELINE.md`, `NDK_PLAN.md`, **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** (capture bisect / restore checklist). **Milestone 10** backlog: fleet + probe Phases A–E + video/QR/chrome-unlock (ordered sprints). **Milestone 4 Sprint 4.5** (main plan): system **`ACTION_VIDEO_CAPTURE`**, BKT shutter parity, hardware JPEG / ISP tuning + **Milestone 5 Sprint 5.1** HUD controls row.

**Engineering roadmap (audit 2026-05-15, OnePlus 13 / LineageOS 23):** P0–P3 backlog covering DNG mmap patching (`TiffUniqueCameraModel50708` / `Dng12Saver`), preview metadata atomics, Compose poll consolidation (`PreviewEnginePollState` + `produceState`), JPEG companion pool + pause drain, `VendorKeyGuard` legacy-setter cache, ADB intent hardening (`pns_intent_fuzz.ps1`), optional `captureBurst` bracketing, and cross-cutting scripts (`pns_probe_device.ps1`). Track implementation vs gates in this file and `CHANGELOG.md`; device verification per `AGENTS.md`.

---

### How agents must execute (nonstop discipline)

1. **Work inside one milestone at a time.** Finish **every sprint** in that milestone (all checkboxes that belong to that sprint) before starting the next milestone.
2. **Within a sprint, complete tasks in order** (top to bottom). If a task is blocked, log the blocker in `PROBE_BUILD_PLAN.md` §5 and fix or escalate; do not silently skip.
3. **After each sprint:** run the **Sprint check** row in that sprint’s gate table. If any check fails, **stop**, fix, re-run.
4. **After all sprints in a milestone:** run the full **Milestone gate**. Only then proceed to the next milestone.
5. **Tick rules:** Never mark `[x]` without meeting **Appendix A — Verification protocol**. Host work requires `scripts/pns_verify_toolchain.ps1 -RunTests` (and `ReadLints` on touched Kotlin). Device work requires evidence in `PROBE_BUILD_PLAN.md` §5.
6. **UI work gate (mandatory — blocks completion of any UI task):** If the change touches **user-visible surfaces** (Compose screens/layouts, preview/chrome/readout, themes, `res/` drawables or dimensions, navigation, dialogs, `MainActivity` window flags), the task is **not done** until all of the following succeed on a **physical device** (emulator only if no USB device is available — say so in §5):
   1. **`.\gradlew.bat :app:assembleDebug`** (or `assembleDebug` as part of your workflow) — zero compile errors.
   2. **Install + launch** the debug APK on the device — use **`scripts/pns_sideload_and_launch.ps1`** (build + install + grant + `am start` preview) or equivalent **`adb install -r -t`** + **`am start -n dev.pointandshoot/.MainActivity --es pns_screen preview`**. Prefer **`scripts/pns_adb_device.env`** for `PNS_ADB_SERIAL` when `-Serial` is omitted.
   3. **Verify** on the glass that the UI matches the intent (layout, spacing, rotation behavior, no obvious regressions).
   4. **Capture proof:** **`scripts/pns_device_screencap.ps1 -OutPath docs\screenshots\ui_<area>_YYYYMMDD.png`** (raw PNG; do not corrupt via naive PowerShell piping — see script header). Record the path + device serial in **`PROBE_BUILD_PLAN.md` §5** or the PR body.
   Pure refactors with **no** visual or behavioral UI change (rename-only, dead-code removal) may skip the device step with a one-line justification. **Agents:** treat this gate as **non-optional** for normal UI work; do not declare UI changes “complete” from IDE previews or chat screenshots alone.
7. **JAVA_HOME (Windows):** use `C:\Program Files\Android\Android Studio\jbr` when Gradle fails with “no java”.
8. **ADB:** prefer `%LOCALAPPDATA%\Android\Sdk\platform-tools` first on `PATH` (pair/connect vs legacy adb).
9. **ADB serial:** optional **`scripts/pns_adb_device.env`** (copy **`scripts/pns_adb_device.env.example`**) sets **`PNS_ADB_SERIAL`** to the USB serial from **`adb devices`** for **`pns_sideload_and_launch.ps1`**, **`pns_adb_preview_validate.ps1`**, **`pns_milestone6_gate.ps1`**, **`pns_device_screencap.ps1`** when **`-Serial`** is omitted. Use **`-Serial`** when more than one device is connected or you need to override the env file.
10. **Git after each milestone (agents):** When a **numbered milestone** (0–11, excluding H) is complete — all sprint checkboxes for that milestone are `[x]` and the **Milestone gate** for that milestone passes per items 3–5 above — **`git commit`** the closing changes with a message that names the milestone (for example `Milestone 7: storage and failure-matrix gates`) and **`git push`** to the branch’s upstream **before** starting work on the next milestone. Do not accumulate finished milestone work across long-lived local branches without pushing; humans and CI rely on the remote for review and bisect.
11. **Capture / preview pipeline regression gate (mandatory when the change affects still capture, RAW/DNG, bracketing, `PreviewController` session surfaces, YUV analysis / highlight metering streams, stabilization keys on requests, imaging profile stream wiring, or material edits to `PreviewEngineScreen.kt` / `RawCaptureSupport.kt`):** Before marking the task complete, run **`scripts/pns_capture_pipeline_verify.ps1`** (wraps **`pns_photo_capture_verify.ps1`** in a child process, records **`docs/CAPTURE_PIPELINE_VERIFY_LATEST.json`** + **`docs/CAPTURE_PIPELINE_VERIFY_HISTORY.jsonl`**, and writes **`hfr-runs/capture_pipeline_gate_*`**) on a **USB device**, or run **`scripts/pns_photo_capture_verify.ps1`** directly with the same device expectations. For **automated cumulative bisect** (apply steps **1..N** from **`docs/REVERTED_FEATURES_RESTORE_LIST.md`**, **assembleDebug**, and verify each step), use **`scripts/pns_capture_bisect_device.ps1`**. After **re-applying** reverted capture features from that doc, run **`scripts/pns_capture_restore_verified.ps1`** (assemble + same USB gate) so restore work cannot ship without a green **`captureRawStill 1/1 ok=true saved=`** needle. Alternatively run **`scripts/pns_milestone6_gate.ps1`** when that pack covers the behavior. Keep artifacts under **`hfr-runs/`**. If a device is unavailable, state that explicitly in **`PROBE_BUILD_PLAN.md`** §5 and get a human waiver before merge. **If a revert is required** to restore stable capture, append a row to **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** with **what was reverted**, **why**, and **exact restore steps** (code snippet or commit range) so features can be re-applied safely later. Prefer a **narrow follow-up fix** over leaving a revert undocumented.
12. **Build plan archive:** When **every** checkbox in a **`### Sprint`** is **`[x]`**, move that sprint’s body off **`BUILD_PLAN.md`** into **`BUILD_PLAN_COMPLETED.md`** and refresh pointers (archive table, backlog consolidation, execution order). Full procedure: **`### Archiving completed sprints — procedure`** under **Completed milestones & sprints** below. Do not duplicate long shipped sprint text in the active plan.

**Hard rule (May 2026 — do not regress):** Never gate **`automationSuppressFacePipeline`** on **`adbSequentialRawStills > 0`** alone. Doing so skipped the H-dial YUV path for **`pns_preview_raw_count`** / sequential RAW and broke RAW still session create on **CPH2655-class** devices (`CAMERA_DISCONNECTED`). **Only bracket automation** may set **`automationSuppressFacePipeline`**. See **`README.md`** (STOP banner), **`AGENTS.md`** (capture warning), and **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** (top).

**Incremental restore rule:** Re-applying **all** bisect doc §1–§5 “Milestone shipping” hunks at once failed **`pns_photo_capture_verify`** on USB **CPH2655**; **§4a** (REGULAR **`OutputConfiguration` stream-use-case** tags) and **§2** (RAW10-before-RAW_SENSOR **`Default`** tier) required separate device proof and stayed **reverted** on that fleet while **§1** + **§5** were restored with a green gate. Evidence and ordering: **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** §8.

**Agents must not ship (without new USB proof on target hardware):** (1) **`streamHints = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU`** on the REGULAR session — observed **RAW still timeout** + **`ERROR_CAMERA_DEVICE`**; (2) **`Default` = RAW12 → RAW10 → RAW_SENSOR`** in **`RawCaptureSupport`** — observed **`DngCreator` Unsupported image format 37**. Avoidance checklist: **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** §8 *What agents must avoid*; narrative: **`AGENTS.md`** *CRITICAL — REGULAR session stream hints*.

**Human work:** Only **Milestone H — Human & publication** contains tasks that require a person (accounts, subjective judgment, physical charts, desktop apps). Agents prepare artifacts; humans close **[HUMAN]** items.

---

### Global toolkit (used in gates)

| Tool | Role |
|------|------|
| `scripts/pns_verify_toolchain.ps1 -RunTests` | Host gate: assembleDebug, unit tests, FOSS dep-audit, license/SBOM, script UTF-8 |
| `scripts/pns_hfr_autorun.ps1` | Device probe automation (`-RunProbeSmoke`, `-RunFullSuite`, …); **`-PerfReport`** → `perf-runs/perf_*.md` (cold start `am start -W`, `dumpsys meminfo`, `PNS.Reader` drop tail; optional **`-Serial`** / `pns_adb_device.env`); Perfetto / `gfxinfo` protocol → **`PERFORMANCE_BUDGETS.md`** § *Perfetto & frame jank* |
| `scripts/pns_adb_preview_validate.ps1` | Scripted preview / RAW / BKT + log capture; **`-ChromeUxPack`** → **`chrome_ux_smoke.json`**; else **7 s** settle → **`mediastore_probe.json`** + DCIM **`ls`** + MediaStore tail |
| `scripts/pns_milestone6_gate.ps1` | Milestone 6 pack: `assembleDebug` + `-Milestone6Pack` (DNG 50708, LUT FPS probe, Calibrate + GLES smoke) → **`milestone6_gate.json`**; optional **`-Serial`** selects the device when **`pns_adb_device.env`** is unset or you need to override **`PNS_ADB_SERIAL`** |
| `scripts/pns_failure_matrix_smoke.ps1` | Milestone 7 smoke: preview cold start + CAMERA revoked preview → **`failure_matrix_smoke.json`** (no AndroidRuntime fatal for `dev.pointandshoot`); optional **`-AppendSection5`** / **`-ProbePlan`** after **`pass: true`** |
| `scripts/pns_root_capability_adb.ps1` | **ADB transport root** probe: optional **`adb root`**, **`adb shell id`** / best-effort **`su -c id`**, **`root_capability_adb.json`** (**`pns.root_capability_adb.v1`**) for **`pns_probe_append_section5.ps1`** |
| `scripts/pns_chrome_ux_gate.ps1` | Milestone 9 pack: toolchain + optional device **`PNS.ChromeUx`** checks (**`seedOk`** … **`grid7x3=`** (or legacy **`grid7=`**), **`modeDialPopout=`**, **`readoutCapture=`**, **`selfTimerSec=`**) → **`chrome_ux_gate.json`** |
| `scripts/pns_automation_smoke.ps1` | Fleet: verify toolchain → chrome UX gate → failure-matrix smoke → optional **`-ChromeUxPack`**; optional **`-RunFullAdbPreviewValidate`** + **`-RequireMediaStoreDcim`**; **`-AppendSection5`** (+ **`-ProbePlan`**) appends **§5** when the smoke passes (**`chrome_ux_gate`** only if adb was authorized); **`mediastore_probe`** §5 uses **`-PassOnly`** only when **`dcimHasPnsCapture`** is true so empty DCIM still logs; **`-TryAdbRoot`** → **`automation_smoke.json`** |
| `scripts/pns_device_screencap.ps1` | **`adb exec-out screencap -p`** to PNG via **`Process` stdout stream** (avoids broken PS pipelines); use for **`BUILD_PLAN`** UI verification artifacts |
| `scripts/pns_pull_dcim_captures.ps1` | **`adb pull`** **`/sdcard/DCIM/Point & Shoot`** to **`hfr-runs/pull_dcim_*`** (or **`-OutDir`**); supports **`pns_adb_device.env`** / **`-Serial`** — desktop half of Sprint **7.3** / **Milestone H.1** |
| `scripts/pns_sideload_and_launch.ps1` | **`assembleDebug`** + **`adb install -r -t`** + runtime grants + **`am start`** preview (`--es pns_screen preview`); primary fast path for **UI work gate** (“How agents must execute”, item 6) |
| `scripts/pns_photo_capture_verify.ps1` | Scripted H-dial RAW still cold loop until **`PNS.AdbValidation`** `captureRawStill 1/1 ok=true saved=`; core gate for **How agents must execute** item **11** (artifacts **`hfr-runs/photo_capture_verify_*`**). Prefer **`pns_capture_pipeline_verify.ps1`** when you need **`docs/CAPTURE_PIPELINE_VERIFY_*.json`** records. |
| `scripts/pns_capture_pipeline_verify.ps1` | Wraps **`pns_photo_capture_verify.ps1`** (child **`powershell.exe`**); writes **`hfr-runs/capture_pipeline_gate_*/gate.json`**, **`docs/CAPTURE_PIPELINE_VERIFY_LATEST.json`**, appends **`docs/CAPTURE_PIPELINE_VERIFY_HISTORY.jsonl`**. Optional **`-BisectStep`**, **`-Notes`**, **`-NoHistoryAppend`**. |
| `scripts/pns_capture_bisect_device.ps1` | Cumulative bisect **1..N**: patch **`PreviewEngineScreen.kt`** + **`RawCaptureSupport.kt`** per **`docs/REVERTED_FEATURES_RESTORE_LIST.md`**, **`assembleDebug`**, **`pns_capture_pipeline_verify`** each step; **`hfr-runs/capture_bisect_device_*/report.md`**. **`-DryRun`**, **`-Fast`**, **`-FromStep`**, **`-NoRestore`**. |
| `scripts/pns_capture_restore_verified.ps1` | After restoring bisect-reverted capture code from **`docs/REVERTED_FEATURES_RESTORE_LIST.md`**, runs **`assembleDebug`** + **`pns_capture_pipeline_verify.ps1`** (USB **`captureRawStill 1/1 ok=true saved=`** gate). Run before merging capture restores. |
| `scripts/pns_in_app_video_verify.ps1` | Cold video-primary preview + **`pns_preview_automation_in_app_video_sec`**: **`assembleDebug`** (optional) → install → assert **`PNS.AdbValidation`** **`inAppVideoSaved ok=true`** with **`bytes ≥ MinBytes`**; artifacts **`hfr-runs/in_app_video_verify_*`**. Uses **`adb exec-out logcat`** (tag **`-s`**) for reliable dumps vs some **`shell logcat *:S`** stacks. Gate for in-app **`MediaRecorder`** session wiring. |
| `scripts/pns_adb_device.env` (gitignored; copy `.example`) | Default **`PNS_ADB_SERIAL`** (USB serial) for scripts when **`-Serial`** omitted |
| `.github/workflows/toolchain-verify.yml` | CI mirror of toolchain |

**Lint / static analysis:** `pns_verify_toolchain.ps1 -RunTests` runs `:app:detekt`, `:app:lintDebug`, and `:app:testDebugUnitTest`. Detekt uses `config/detekt/detekt.yml` plus `config/detekt/baseline.xml` (regenerate with `:app:detektBaseline` when intentionally bulk-fixing legacy debt). Android Lint uses `app/lint-baseline.xml` (regenerate with `:app:updateLintBaseline` after fixing or accepting new findings). AGP is **8.8.2** on **Gradle 8.10.2**; release builds use **R8** (`isMinifyEnabled` + `isShrinkResources`) with `app/proguard-rules.pro` (keep **UTF-8** without BOM).

### Performance & responsiveness backlog (2026 audit) — archived

All seven rows are **`[x]`** — full table in **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** (*Performance & responsiveness backlog*).

### Follow-on recommendations (performance, evidence, and maintenance)

Ongoing practices beyond the closed rows above; align evidence with **`PERFORMANCE_BUDGETS.md`** and **`PROBE_BUILD_PLAN.md`** section 5 when device-backed.

| Area | Recommendation |
|------|----------------|
| **Cold-start evidence** | Pair **`adb shell am start -W -n dev.pointandshoot/.MainActivity`** (**`TotalTime`**) with **PSS** via **`adb shell dumpsys meminfo dev.pointandshoot`**. Use **`scripts/pns_cold_start_capture.ps1`** (wraps **`pns_hfr_autorun.ps1 -PerfReport`**) or **`-PerfReport`** directly; writes **`perf-runs/perf_<stamp>.md`**. |
| **Release prep** | At least once per **release prep**, repeat **`am start -W`** on an **installed release** APK that includes merged **baseline / startup** profiles and **R8** (`assembleRelease` / Play-equivalent), not debug-only, and record **TotalTime** (+ optional PSS) in **`perf-runs/`** or **PROBE_BUILD_PLAN.md** section 5. |
| **ADB stability** | Use **one** **`adb`** on **`PATH`**, same build as **Android Studio** / **`%LOCALAPPDATA%\Android\Sdk\platform-tools`** (see *How agents must execute* item **8**), to avoid **client/server version mismatch** daemon restarts that flake **`mergeReleaseBaselineProfile`**, Macrobenchmark, and other **connected** Gradle tasks. |
| **Baseline profile workflow** | Treat **`:app:generateBaselineProfile`** as an **explicit** step (**`scripts/pns_baseline_profile_generate.ps1`**, stable USB). Do **not** assume random CI jobs run profile generation without a **device lab** or **scheduled** hardware job. |
| **First frame “ready to shoot”** | **Shipped:** `PnsStartupTrace` + `PnsApplication` + preview **`updatePreviewMetadata`** gate (FPS, sensor readout, AE/AF stable). **`pns_hfr_autorun.ps1 -PerfReport`** parses **`pns.firstFrameReady`** from logcat tail (<= **1200 ms** budget). |
| **Compose jank** | If preview/chrome still stutters after structural fixes (e.g. stable **`cameraIds`**), capture a **Compose layout / system trace** on the preview route before adding speculative **`derivedStateOf`** or broad refactors. |
| **Baseline profile diffs** | When **`baseline-prof.txt`** / **`startup-prof.txt`** churn heavily, regenerate on a **fixed reference device class** (manufacturer + **Android / API level**) and note **device class + API** in the PR so reviewers can interpret large diffs. |
| **Merge hygiene** | **CI:** `.github/workflows/toolchain-verify.yml` runs **`scripts/pns_verify_toolchain.ps1 -RunTests`**. Locally, keep the same before merges (*How agents must execute* item **5**); do not skip Detekt, **lintDebug**, or unit tests unless explicitly waived with rationale. |

### Backlog consolidation (**post–Milestone 9 work**)

Remaining open **`[ ]`** work is under **`## Milestone 10`** (Sprint **10.16**, gate), **`## Milestone 11`** (Sprint **11.3**, gate), and **`## Milestone 9 → Sprint 9.13`** (three human finder-geometry rows in the archive). Shipped sprints **10.1–10.13** / **10.15**, Milestones **8–9**, and Milestone **11** sprints **11.1** / **11.2** / **11.4** bodies live in **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)**. **Chrome lock** policy is unchanged (**`docs/preview-chrome-layout-style-guide.md`**). Shipped items from removed **`[x]`** rows remain in **`CHANGELOG.md`** / **Appendix B**.

### Future features (deferred — unscheduled)

- **Smile-triggered still (backlog):** Revisit after the main capture + preview stack is stable. No portable Camera2 smile signal; a later implementation would likely use ML Kit **face classification** (smiling probability) on the existing YUV path (`MlKitFaceTrackSupport` currently uses `CLASSIFICATION_MODE_NONE`), with UX/perf/debounce work — not a Milestone 0–10/H commitment until explicitly reprioritized.
- **OpenCamera-style toolbox (descoped):** Former **Sprint 10.14** (AF stack, AE/AF lock, audio trigger, remote shutter, shortcut profiles, pause/resume video, distortion/shading toggle, focus peaking, intervalometer, anti-shock delay) — **removed from the roadmap**; do not re-add without an explicit product request.

---

## Completed milestones & sprints (archive)

| Archive | Contents |
|---------|----------|
| **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** | **Milestones 0–7**; **Milestone 8–9**; **2026 performance backlog**; **Milestone 10** sprints **10.1–10.13**, **10.15**; **Milestone 11** sprints **11.1**, **11.2**, **11.4** |

**Still open in this file:** Milestone **10** sprint **10.16** and **Milestone 10 gate**; **Milestone 11** sprint **11.3** and **Milestone 11 gate**; **Milestone H**; **Sprint 9.13** human finder-geometry rows (see archive — three `[ ]` items).

### Archiving completed sprints — procedure (repeatable; future automation)

Use this checklist whenever **shipped** sprint bodies should leave the active plan and live only in **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)**. Goal: **`BUILD_PLAN.md`** stays a short **remaining work** index; history stays grep-able in the completed file.

1. **Eligibility — move only fully closed sprints**  
   - A **`### Sprint …`** block may move when **every** task line is **`- [x]`** (or the sprint is **non-checkbox** narrative that is explicitly done — rare).  
   - **Do not** move a sprint that still contains **`- [ ]`** (e.g. human finder geometry in archived **9.13**). Split or leave the whole sprint in the active file until those rows close or are descoped.  
   - **Whole milestones (0–7 style):** When **all** sprints + gate for a numbered milestone are done, the entire milestone body may already live in the archive; keep **Milestone H** and any **post-archive pointers** in the active plan as today.

2. **Cut / paste**  
   - **From:** `BUILD_PLAN.md` — the sprint section from **`### Sprint …`** through its **`**Sprint check:**`** line (inclusive), and any sprint-only notes that belong with it.  
   - **To:** `BUILD_PLAN_COMPLETED.md` — append under the correct **`## Milestone …`** heading. If this is the first archived chunk for a milestone still active elsewhere, add a subsection title such as **`## Milestone N — completed sprints (a.b, c.d)`** and a one-line pointer: **Open:** sprint **x.y** + **Milestone N gate** remain in **`BUILD_PLAN.md`**.

3. **Replace in `BUILD_PLAN.md` with pointers**  
   - Under the milestone **`##`**, keep **Objective**, **Key references** (if remaining sprints need them), and **`**Milestone N gate**`** until the milestone is finished.  
   - Add or refresh a single line: **`**Completed sprints** … → [BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md) (*anchor / section title*)`**.  
   - Update **`**Suggested execution order (remaining):**`** so it lists only open sprints + gate.

4. **Sync index prose (both files)**  
   - **Archive table** (above): extend the **Contents** cell with new sprint numbers or milestone names.  
   - **`### Backlog consolidation`**: list which milestones still have **`[ ]`** in the active file vs what shipped to the archive.  
   - **`BUILD_PLAN_COMPLETED.md` top blurb** (first paragraph): mirror dates / milestone ranges so agents opening the archive cold see the same scope.

5. **Descoped or cancelled sprints**  
   - **Do not** copy to **`BUILD_PLAN_COMPLETED.md`** as “completed.” Remove from the active plan; record intent under **`### Future features (deferred — unscheduled)`** (one bullet) if the team should remember why it vanished (example: former **10.14**).

6. **Gate tables**  
   - Keep **`**Milestone N gate**`** in **`BUILD_PLAN.md`** while **any** sprint for milestone **N** is still open in this file. When the milestone is fully shipped, the gate row may move with the last body or stay as a one-line “gate satisfied — see §5” in the archive — **match existing style** for milestones **8–11** in the completed file.

7. **Future script / agent automation (optional)**  
   - **Pre-flight:** For each **`### Sprint x.y`**, ensure no `^- \[ \]` appears before the next `### Sprint` or `**Milestone` gate.  
   - **Grep aids:** Open work: **`BUILD_PLAN.md`** with **`^- \[ \]`**; sprint headers: **`^### Sprint`**.  
   - A maintainer script could: (1) parse markdown headings under **`## Milestone`**, (2) split children by **`### Sprint`**, (3) flag sprints with mixed `[ ]`/`[x]`, (4) emit a diff-friendly move list — **not shipped in-repo yet**; follow steps **1–6** manually until such a script exists.

---

## Milestone 10 — Post–Milestone 9 product expansion (**remaining**)

**Objective:** Ship multi-device readiness, ordered capture/video/QR UX, and probe-driven quality **after** Milestone 9 chrome is stable. **Depends on:** Milestone **9** gate (archived; toolchain + `pns_chrome_ux_gate.ps1` when device-attached); **Sprint 9.13** for finder proof when UI touches the finder. **Does not replace** deep ADB matrices (`pns_hfr_autorun`, session exhaustive) — those stay developer automation.

**Completed sprints** **10.1–10.13**, **10.15** → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** (*Milestone 10 — completed sprints*).

**Suggested execution order (remaining):** **10.16** → **Milestone 10 gate**.

### Sprint 10.16 — Milestone H handoff queue (non-code)

- [ ] **[MIXED]** **Gallery / desktop open** — coordinates with **Milestone H.1** + **`scripts/pns_pull_dcim_captures.ps1`**; stays `[ ]` until human sign-off recorded in §5 (supersedes the duplicate **Sprint 7.3** row, now marked moved in **Milestone 7**).

**Milestone 10 gate**

| Check | Pass criterion |
|-------|----------------|
| Host | `pns_verify_toolchain.ps1 -RunTests` exit 0 on every sprint merge that touches Kotlin/scripts |
| Device | §5 rows for each closed **[MIXED]** / **[ADB]** sprint that ships preview/capture behavior |
| Chrome | Any finder/tray/grid geometry change: maintainer unlock + **Sprint 9.13** screenshots + gate script updates |
| Human | **10.16** items close only with **Milestone H** sign-off |

---

## Milestone 11 — Capture UX fixes (**remaining**)

**Objective:** Restore dodge-style **85 mm / 150 mm** tele digital crops on the clustered **mid-tele** sensor ([`DODGE_PROFILE.md`](DODGE_PROFILE.md)), fix **white balance** readout ordering and defaults, align **face / eye** HUD with the preview, and ship reliable **in-app video** recording plus a **video resolution** selector beside FPS. **Depends on:** Milestone **9** chrome stable; USB device for **[MIXED]**/**[ADB]** sprints. **Chrome lock** unchanged — readout **menu content** and **behavior** only; no finder/tray/grid geometry changes (**`docs/preview-chrome-layout-style-guide.md`**).

**Key references:** `DODGE_PROFILE.md` (digital tele crops); `ReadoutExposureCatalog.kt`, `PreviewReadoutStrip.kt` (WB); `BackCameraRoleResolver.kt`, `SensorCropGeometry.kt`, `resolveFocalMmSlot` (focal routing); `PreviewEngineScreen.kt` `processFaceStatistics` / `FaceDetectAdapter.kt` (eye marks); `PreviewEngineScreen.kt` `applyInAppVideoRecordingShell` / `MediaRecorder` (video). **Regression gate** when touching `PreviewEngineScreen.kt` / session surfaces: **`scripts/pns_capture_pipeline_verify.ps1`** per *How agents must execute* item **11**. Do not ship fleet bisect regressions from **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** §8 without USB proof.

**Completed sprints** **11.1** (WB menu), **11.2** (dodge tele routing), **11.4** (in-app video + RES) → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** (*Milestone 11 — completed sprints*).

**Suggested execution order (remaining):** **11.3** → **Milestone 11 gate**.

### Sprint 11.3 — Face / eye overlay calibration

- [ ] **[MIXED]** **Bisect misalignment** — portrait + reverse-landscape at **23 / 73 / 85 / 150** mm with **Eye AF** on; fix **`processFaceStatistics`** / **`mapActivePointToBufferWithScalerCrop`** / `TexturePreviewFit` parity vs GLES **`previewTextureCoverCrop`** when offsets are crop-specific or logical-vs-physical.
- [ ] **[MIXED]** **Automation** — **`scripts/pns_face_meter_probe.ps1`** before/after; extend JVM tests (**`FaceDetectAdapterTest`**, crop cases) where pure-data.
- [ ] **[HUMAN]** **Live subject sign-off** — eyes land within ~1 finder tile (pairs with **Milestone H.6** Eye-AF photo row when satisfied).
- [ ] **[ADB]** Evidence in **`PROBE_BUILD_PLAN.md`** §5 (probe JSON paths + optional **`pns_device_screencap.ps1`**).

**Sprint check:** probe script completes; human row documented or waived.

**Milestone 11 gate**

| Check | Pass criterion |
|-------|----------------|
| Host | `pns_verify_toolchain.ps1 -RunTests` exit 0 |
| Capture regression | `pns_capture_pipeline_verify.ps1` per item **11** when **`PreviewEngineScreen.kt`** / still or video session wiring changes |
| WB | Device menu coldest→warmest; **AWB** explicit; **OFF** + gray card bottom |
| Focal | **DodgeReference** default on reference device; 85/150 crop on tele — §5 logs |
| Face | **`pns_face_meter_probe.ps1`** + human overlay sign-off or H.6 waiver |
| Video | **`pns_in_app_video_verify.ps1`** green + **RES** selector in video mode |
| Chrome | No locked geometry/styling regressions (**preview-chrome UI lock**) |

---

## Milestone H — Human & publication (**all human-dependent work**)

**Objective:** Subjective validation, account ownership, and release authority. **Agents must not mark these `[x]` without human sign-off** (screenshots, portal URLs, or written approval recorded in §5).

### Sprint H.1 — Desktop & studio verification

- [ ] [HOST][HUMAN] Pull DNG/AVIF/JXL from `DCIM/Point & Shoot/…` and open in desktop RAW / color tools (Standard Pro + Ultra-Max samples). **Staging:** **`scripts/pns_pull_dcim_captures.ps1`** → then **`darktable` / `RawTherapee` / `djxl`** / AVIF viewer per **`STORAGE_STRATEGY.md`**.
- [ ] [HOST][HUMAN] Passport **Creative Enhancement** reference values transcribed from X-Rite datasheets (IP-sensitive).
- [ ] [MIXED] Bracket set **desktop regroup** sanity.

### Sprint H.2 — Real-world calibration charts

- [ ] [HUMAN] Capture physical ColorChecker / calibration charts (controlled illuminant).
- [ ] [MIXED] dE2000 / MTF50 field gates — human-operated capture sessions.

### Sprint H.3 — Git hosting & mirrors

- [ ] [HUMAN] Create/configure GitLab project and mirroring (or add GitLab remote).
- [ ] [CI][HUMAN] GitLab CI variables for signing (if used).

### Sprint H.4 — Signing & supply chain

- [ ] [HUMAN] GitHub Actions secrets: `ANDROID_KEYSTORE_BASE64`, passwords, alias (`build-signed.yml`).
- [ ] [MIXED] CI **`assembleRelease`** with real key + `apksigner verify` + **[ADB]** install smoke — evidence in §5.

### Sprint H.5 — Release & community

- [ ] [HUMAN] Tag release, upload artifacts, store listings, comms.
- [ ] [HUMAN] Post-release monitoring checklist.

### Sprint H.6 — Subjective UX

- [ ] [ADB] Reverse-landscape / Eye-AF alignment **photo sign-off** (if not closed earlier).
- [ ] [HUMAN] Aesthetic review of HUD/LUT defaults.
- [ ] [MIXED] Work through **Milestone 10 Sprint 10.15** (UX polish residual: messaging, snackbars, export errors, onboarding tweaks, geotag hint, **long-running capture progress**, probe-hub IA, a11y labels, immersive tip, gallery open fallback) without changing locked preview chrome geometry or styling. **Sprint 10.15** checklist in **`BUILD_PLAN.md`** is **`[x]`** with **`PROBE_BUILD_PLAN.md`** §5 **2026-05-13T20:00:00Z** host evidence; this umbrella row stays open until a11y / immersive / gallery follow-ups and any human sign-off are done.

**Milestone H gate:** Owner-approved checklist recorded (§5 or project wiki); no remaining **[HUMAN]** checkbox unjustified.

---

## Appendix A — Verification protocol (abbreviated)

**Before any `[ ]` → `[x]`:**

1. `pns_verify_toolchain.ps1 -RunTests` → PASSED (host code/scripts).
2. `ReadLints` clean on touched Kotlin.
3. Paths/symbols claimed in the bullet exist in repo.
4. Tests: `TEST-*.xml` shows `failures="0" errors="0"` for claimed classes.
5. `CHANGELOG.md` (Unreleased) for user-visible changes; **§5 row** in `PROBE_BUILD_PLAN.md` for gates/device/audit.
6. **[ADB]/[ROOT]:** device evidence in §5; never close on host-only scaffolding for device-gated bullets.
7. **Regression:** If toolchain fails after a tick, revert tick or fix immediately.

**Partial completion:** Parent **[MIXED]** stays `[ ]` until **every** child venue is satisfied (see legacy protocol in git history if needed).

---

## Appendix B — Baseline already shipped (high level)

**Do not duplicate execution** — this appendix prevents scope confusion. Details live in code + prior §5 rows.

| Area | Status |
|------|--------|
| FOSS gates + CI toolchain | Shipped |
| Probe JSON + encoder summaries + About hydration | Shipped |
| Dodge profile + crop geometry tests | Shipped |
| Pro HUD + dial + HUD settings + haptics/tally | Shipped |
| Host AVIF/HEIF/ISOBMFF building blocks, LUT pipeline tests | Shipped |
| Calibration math, `.cube` export, DNG sidecars (library path) | Shipped |
| Diagnostics mode, `FAILURE_MATRIX.md`, `CAPTURE_ARCHITECTURE.md` | Shipped |
| Root **UI** + fallbacks (non-root device validated) | Shipped |

For line-by-line historical checkboxes, use `git log -- BUILD_PLAN.md` and `PROBE_BUILD_PLAN.md` §5.

---

## Appendix C — Agent quick grep (unchanged intent)

| Need | Pattern |
|------|---------|
| Open host work | `^- \[ \] \[HOST\]` |
| Open device work | `^- \[ \] \[ADB\]` |
| Open human work | `^- \[ \] \[HUMAN\]` |
| Open mixed | `^- \[ \] \[MIXED\]` |

---

## Document control

- **Version:** Milestone/sprint rewrite (2026). Replaces numbered §0–§10 narrative checklist; **Milestone 11** (2026) capture UX + video backlog; technical truth remains in source + `PROBE_BUILD_PLAN.md`.
- **Owner:** Project maintainer approves Milestone H closures.
- **Archive cadence:** After closing sprints, run **`### Archiving completed sprints — procedure`** (under **Completed milestones & sprints**) so **`BUILD_PLAN.md`** does not grow unbounded.
