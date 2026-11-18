# Phase 2 — Technology decision

**Binding for later implementation.** No code in this PR. Camera pipelines are **platform-native either way** (Camera2 / AVFoundation / UVC) — this file only picks how **shared logic** and **UI** are structured.

**Read with:** [`INVENTORY.md`](INVENTORY.md) Decisions, [`DESIGN_BRIEF.md`](DESIGN_BRIEF.md).

A numbered ADR (0011+) should be cut from this document **when Phase 4 starts**. Until then this file is the stack SoT.

---

## Decision (one sentence)

**Kotlin Multiplatform shared core** + **Compose Multiplatform for companion / library / settings UI that exists on more than one OS** + **platform-native shooting chrome and capture** (Android keeps today’s Compose + Camera2 + GLES; iOS is SwiftUI + AVFoundation + Metal).

That is not “CMP for the finder on every screen” and not “rewrite Android in Swift.” It is the best fit for *this* repo’s current modules, ADR-0001/0009, Edward’s COMPANION desktop, and iOS ADAPT-not-pixels.

---

## Why this app (constraints that pick the stack)

| Constraint | Source | Implication |
|------------|--------|-------------|
| Production preview is Camera2 + `GLSurfaceView` `setGeometry` **only** from `PreviewMainViewport` | ADR-0001, AGENTS.md GLES lock | The finder pipeline **cannot** be a shared Compose-MP camera widget |
| iOS capture is AVFoundation; ProRAW is acceptable | Q2, Q3 | iOS stills/video are a **native** pipeline, not a Camera2 port |
| Desktop is COMPANION (tether, MJPEG, UVC host, library, presets) | Q1 | Desktop UI is **not** `PreviewEngineScreen`; it *is* the library desk + HTTP + UVC — already Compose on Android |
| iOS chrome **adapts** to Dynamic Island / safe areas / platform conventions; not pixel-identical | Q4 | Fighting UIKit with a pixel-locked CMP finder is the wrong fidelity target |
| `:pns-core` / `:pns-capture` / `:pns-fleet` already hold pure JVM policy | ADR-0009 | KMP extraction has a home; session glue stays out of shared UI |
| No app-wide Hilt/ViewModel | ADR-0001 | Do not introduce a new DI fashion as part of the port |
| Apache-2.0, no Play Services; ML Kit is already a F-Droid exception | ADR-0005, 0006, Q6 | iOS Vision on-device; Android should move toward a FOSS detector — not a new Google SDK |
| `SettingsExportBundle` is the cross-device preset format | Q12 | Shared Kotlin serialization of that JSON is mandatory |
| Feature freeze on Android at migration start | Q15 | Do not couple the port to HAL bisects or parity-debt |

---

## Options weighed

### Option A — KMP + Compose Multiplatform for shared logic *and* UI (including finder)

**Idea:** One Compose tree for shooting chrome, gallery, settings; expect/actual for Camera2 vs AVFoundation vs UVC.

**Fits:** Charcoal/orange tokens and 7×3 *flow* could be one source. Library desk would not be rewritten three times. Desktop companion could share that desk.

**Does not fit:**

- The locked GLES `setGeometry` contract and 23k-line `PreviewEngineScreen` session create are Android HAL code. Wrapping them in CMP does not share the camera.
- Q4 asks iOS to **adapt** (Dynamic Island, sheets, platform conventions). A single Compose finder optimized for the Android lock fights that.
- Desktop **must not** use that finder as home (Q1). Sharing “the UI” would pressure agents to clone the phone frame on a monitor — the failure mode the inventory warned about.
- CMP camera interop (interop UIView / SurfaceView) is the fragile-hack zone Edward forbade.

**Verdict:** Use CMP where the UI is **already** cross-surface (library, settings, tether, About). Do **not** make CMP the iOS finder.

### Option B — Native SwiftUI + AVFoundation capture, shared KMP core only (no shared UI)

**Idea:** iOS shooting chrome and every other screen reimplemented in SwiftUI. Kotlin stays Android + maybe a headless KMP core. Desktop would be a third native UI (WinUI / Swift / GTK).

**Fits:** iOS platform conventions, AVFoundation, Metal LUT, PhotoKit. Clean Omit/Fallback hiding. Matches “full camera, no hacks.”

**Does not fit:**

- Library desk, settings search, LUT importer, tether client, About, `SettingsExportBundle` UI would be written **three** times. The inventory forbids silent drops; triple UI is how features get dropped.
- Desktop companion **is** those screens. They are Compose today. Rebuilding them in WinUI/Swift/GTK for a FOSS Apache-2.0 app is the wrong cost center.
- ADR-0009 already invested in `modules/pns-*`. Option B under-uses them.

**Verdict:** Take Option B for **iOS capture + shooting chrome**. Do not take it for library/companion.

### Chosen combination (A for companion UI, B for shooting)

| Layer | Android | iOS | Desktop |
|-------|---------|-----|---------|
| Capture / preview session | Camera2 + GLES (`LutCameraPreviewRenderer`) | AVFoundation + Metal LUT | UVC host + optional tether MJPEG **view** of the phone |
| Shooting chrome (8-band stack) | Existing Jetpack Compose (locked) | **SwiftUI**, matching DESIGN_BRIEF flow | **Not primary**; optional inspector pane |
| Gallery, library desk, LUT import, About, settings search, tether console, preset import/export | Jetpack Compose today → extract to **Compose Multiplatform** where expect/actual is honest | CMP **or** SwiftUI wrapping shared CMP for these screens — prefer **CMP** so desktop and iOS do not diverge | **Compose Multiplatform** (Skiko) window |
| Models, catalog honesty, crop math, LUT files, histogram/zebra math, naming, `SettingsExportBundle`, tether JSON, Wear protocol (phone side) | **KMP** `:pns-core` (expect/actual only at OS edges) | same | same |
| Face / eye | Camera2 faces + FOSS/flavored detector goal (Q6); ML Kit remains Android until a FOSS path is proven | **Vision** on-device | Optional; not a desktop selling point |
| Fleet matrix / engineering hub | Stays Android | Omit consumer | Optional developer pane later |

**Camera pipelines stay native.** Shared code never calls Camera2 or AVFoundation from commonMain.

---

## Module sketch (Phase 4 — do not implement now)

```
modules/pns-core        → KMP commonMain (models, catalog types, naming, LUT math, export JSON)
modules/pns-capture     → Android Camera2/DNG remain; do not “KMP” DngCreator
modules/pns-fleet       → Android matrix builder stays; honesty *policy* types can go commonMain
ios/                    → Xcode/SPM: SwiftUI chrome + AVFoundation session (new)
composeApp/             → CMP for companion + library (desktop + shared screens)
:app                    → Android composition root (unchanged role)
:wear                   → unchanged this wave (Q5)
```

Exact Gradle/Xcode names are Phase 4. DAG in ADR-0009 stays acyclic; `:app` remains composition root on Android.

---

## Language / runtime notes

- **Kotlin 2.4.x** already in `gradle/libs.versions.toml`. KMP versions must not silently jump the Android freeze (Q15) without a toolchain gate.
- **Compose BOM 2026.09.00** — CMP for companion should track a compatible Compose Multiplatform release; finder on Android stays Jetpack Compose.
- **Swift / SwiftUI** — current Apple toolchain at implementation time; no UIKit-only mandate, but UIKit interop for `AVCaptureVideoPreviewLayer` is expected.
- **Desktop packaging (Q9):** Windows MSIX/installer, Linux Flathub/AppImage + GitHub Releases, macOS best-effort. CMP Desktop is the UI toolkit; packagers are a later MIG sprint.
- **Not chosen:** Flutter, React Native, Unity, a web Electron camera, or CameraX-as-the-iOS-backend. They fight Camera2 locks, FOSS policy, or Q4.

---

## Face / eye (Q6)

| Platform | Path |
|----------|------|
| iOS | Apple Vision, on-device, no cloud. Eye-AF overlay is Durable under DESIGN_BRIEF |
| Android | Prefer a **FOSS or flavor-gated** detector so F-Droid is not permanently blocked by ML Kit (ADR-0006 exception). Out of *migration parity* to finish that swap; do not add more Google ML Kit modules |
| Desktop | No requirement |

---

## What this decision does **not** authorize

- Phase 4 code, new Gradle modules, or an Xcode project in this PR.
- Flipping Android DNG / GLES / tele / fleet locks.
- Implementing PARTIAL items (motion trip, Astro/Document, journal UI).
- Using CMP `expect/actual` to pretend iOS has Camera2 RAW12.
- Shipping iOS DNG that imitates Camera2 strip layout (Q3: ProRAW is the iPhone RAW).

---

## New questions (do not block Phases 1–3)

Defaulted here; Edward may override:

1. **iOS library UI toolkit:** prefer CMP shared with desktop vs SwiftUI PhotoKit-first with KMP models only. **Default:** CMP for library/desk so desktop and iOS cannot drift; SwiftUI for shooting only.
2. **Desktop UVC stack:** OS APIs (MediaFoundation / AVFoundation / V4L2) vs libuvc. **Default:** OS APIs first, libuvc only if an OS API cannot see a device.
3. **Engineering hub on desktop:** **Default:** omit from consumer builds; optional later developer window.
4. **iPad / macOS Catalyst:** **Default:** iPhone-first; iPad uses the landscape band split; Mac camera app is **not** in Q1 (Mac is companion + best-effort packaging).

---

## Document control

| Field | Value |
|-------|-------|
| Phase | 2 — tech decision |
| App code | Untouched |
| ADR | File as ADR-0011+ when Phase 4 starts |
| Next | [`BUILD_PLAN.md`](../../BUILD_PLAN.md) MIG.* + [`PARITY_CHECKLIST.md`](PARITY_CHECKLIST.md) |
