# Phase 1 — Design brief (binding)

**Product:** Point & Shoot — FOSS pro camera.  
**Authority:** Edward’s Phase 0 answers, 2026-11-16, recorded in [`INVENTORY.md`](INVENTORY.md) **Decisions**.  
**This document is binding.** Implementation (Phase 4+, not this PR) must **match this brief**. Any departure goes in **Deviations** and needs **Edward’s approval** before code.

**Read before any migration work:** this file, [`INVENTORY.md`](INVENTORY.md), [`TECH_DECISION.md`](TECH_DECISION.md), [`PARITY_CHECKLIST.md`](PARITY_CHECKLIST.md). Re-read after every MIG.* sprint.

Android portrait chrome remains locked in code by [`.cursor/rules/preview-chrome-ui-lock.mdc`](../../.cursor/rules/preview-chrome-ui-lock.mdc) and [`docs/preview-chrome-layout-style-guide.md`](../preview-chrome-layout-style-guide.md). This brief does **not** unlock Android pixel tweaks. It defines how **iOS** and **desktop** inherit the same **look language** and **information architecture** without a redesign.

---

## Match-the-brief statement (binding)

Ship Point & Shoot as:

1. **Android** — the existing Camera2 camera, feature-frozen at migration start (Q15). Consumer chrome stays charcoal/orange, **3:4 finder**, **7×3 grid flow**, as already locked. Storage tree **`DCIM/PointAndShoot`**.
2. **iOS** — a **full camera app** with **all** Android consumer functionality that public AVFoundation / PhotoKit / UIKit-or-SwiftUI APIs can host **without hacks**. Same **general design flow** as the locked chrome (inset band → 3:4 finder → readout → 7×3 rail → tray), **adapted** to Dynamic Island, safe areas, and iPhone conventions. **Not pixel-identical.** RAW stills may be **Apple ProRAW** (Q3). Hide anything iOS cannot honestly do (same policy as `FleetUiVisibilityGate`: unavailable → hidden, not greyed-out theater).
3. **Desktop (Windows, Linux; macOS best-effort)** — a **COMPANION**, not a phone-camera clone. Tether console (`:28765`), MJPEG live view (`:28770`), **UVC host** capture (phone-as-webcam and real USB cameras; **not DNG**), RAW/DNG review + library desk, LAN roll/proofing/offload, `SettingsExportBundle` sync. Do **not** make the 7×3 portrait finder the primary desktop UI.

**No redesign.** Do not invent a new visual language, a new shooting-mode IA, or a “simpler” subset. If a control exists on Android and the platform can host it Durably or with an honest Fallback, it ships. If it cannot, it is **Omit** and listed — never dropped silently.

**No fragile hacks.** Edward’s standing rule. Private APIs, root, undocumented Camera2-on-iOS shims, and fake DNG strip layouts on iPhone are forbidden.

---

## Roles by platform

| Platform | Role | Primary surfaces |
|----------|------|------------------|
| Android | Handheld camera (source of truth for capture behavior) | Preview engine C2, gallery C3–C4, settings sheets D1–D29 |
| iOS | Handheld camera (parity of **function + flow**) | Same consumer screens/sheets/overlays as inventory C1–C9, D1–D29, HUD layers — adapted chrome |
| Desktop | Companion + connected-camera host | Tether console, MJPEG pane, UVC recorder, library desk, settings/preset sync. Welcome/permissions as OS prompts. **No** Wear, QS tiles, lockscreen camera, or 7×3 as home |

Wear OS (`C12`) stays Android-only until a later watchOS sprint (Q5 defaulted). Engineering hub (`§1.3.3`) stays Android consumer-hidden; iOS/desktop store builds **Omit** the probe hub unless Edward later unlocks a hidden diagnostic (defaulted).

---

## Visual language (preserve)

Do not restyle. Tokens from `PnsTheme.kt` / inventory §1.14:

| Token | Value | Rule |
|-------|-------|------|
| Photo orange | `#FF5C00` | Shutter accent, status, selected chips |
| Record red | `#E00000` | Tally / recording |
| Charcoal | `#181A1B` | Preview chrome and photo-mode surfaces **stay dark** even if system is Light |
| Warn amber / ok green / root blue | `#E6A23C` / `#4CAF50` / `#4DA3FF` | Root blue **Omit** on iOS and desktop (Q7) |
| Menu surface | `#1A1A1A`, ~16 dp corners, low elevation | Settings sheets |
| Type | JetBrains Mono for HUD / readout / timecode | iOS Dynamic Type may scale **body** copy; HUD mono stays tabular |
| Touch | ≥48 dp / 44 pt | |
| Icons | Same icon *meanings* as the 7×3 slots | SF Symbols allowed only where they map 1:1 to an existing slot; do not replace the grid with a different icon set as a “redesign” |
| Photo chrome vs app theme | System/Light/Dark for non-preview; **finder chrome stays charcoal** | |

Brand assets: `docs/branding/`, `docs/BRAND_ICON.md`.

---

## Information architecture (locked flow, adapted geometry)

### Handheld shooting (Android + iOS)

Portrait stack, **top → bottom**, same *bands* as the style guide:

1. **Top inset band** — status bar + cutout / **Dynamic Island** / notch as a **real layout slot** (one inset, not doubled). Timecode / tally / selfie ring live here.
2. Thin section divider.
3. **Finder** — target **3:4** (width:height), full width when the band is tall enough; extra slack **above** the tile (toward the island / status bar), preview flush above the readout.
4. Divider.
5. **Readout chips** — photo: ISO · SS · F (if hardware) · WB · AF · Still LUT · IMG. Video: ISO · SS · F · WB · AF · Video LUT · STAB (no IMG / Still LUT).
6. Divider.
7. **7×3 rail** — focal-length row + **two** sticky shortcut rows; **Settings at r2c6**. Unavailable slots render **empty**, not removed (geometry lock).
8. **Bottom tray** — gallery thumb, format FAB, **center shutter**, Photo / Video / Webcam toggle **left of** the command dial.

iOS **must** keep this band order and the meaning of each band. It **may** change padding, corner radii, and sheet presentation to match UIKit/SwiftUI (page sheets, large titles off the finder, home-indicator exclusion). Landscape may use the Android landscape split (finder `~1.65` vs chrome column `1`) or an iPad-adaptive split — same bands, not a new IA.

**Webcam toggle on iOS:** UVC gadget is **Omit** (Q8). The tray Webcam affordance is **Fallback**: LAN MJPEG/H.264 preview for OBS-class clients on the local network, or hidden if the build has no honest server. Do not fake Zoom “iPhone as USB webcam” without a supported API.

### Desktop companion (not the phone stack)

Default desktop home is **not** the 7×3 finder. Primary navigation:

1. **Tether** — status, capture, flash, live MJPEG from the phone (`docs/TETHER_API.md`, `:28765` / `:28770`).
2. **Library** — the library desk tabs (Days, Cull, Library, Travel, Finish, Offload, Privacy) from inventory C4.
3. **Camera** — UVC host: choose a connected camera, record/capture **non-DNG** (JPEG/H.264 or what the device actually delivers).
4. **Presets** — import/export `SettingsExportBundle`.
5. **About** — heritage, license, updates appropriate to the platform.

A tether live-view pane **may** reuse readout chips as a compact inspector. That is a companion pane, not a port of `PreviewEngineScreen`.

---

## Platform adaptation (iOS)

Allowed without a Deviation:

- Safe-area / Dynamic Island / home indicator as the top and bottom inset bands.
- System permission sheets (Camera, Mic, Photos, Local Network, Location, Bluetooth).
- iOS sheets / popovers instead of Android dialogs for D1–D29 — **same sections and fields**.
- Volume-up shutter (**Durable**); Control Center / Shortcuts as **Fallback** vs QS tiles; WidgetKit still **without** a live HAL.
- Focus modes as **Fallback** vs DND-while-preview.
- PhotoKit albums named **`PointAndShoot`** (Q11), not a second brand folder.
- Haptics via `UIImpactFeedbackGenerator` mapped to the existing 30 ms still tick.

Not allowed without a Deviation:

- Moving readout into the finder, merging tray into the rail, dropping the focal row, or replacing 7×3 with a “simpler” control strip.
- Light-themed finder chrome.
- A unique iOS-only shooting mode that has no Android inventory counterpart (except honest Fallbacks listed below).

---

## PARTIAL / out of migration (do not implement)

These exist as labels or stubs on Android. Edward marked them **PARTIAL everywhere** or **out of parity**. Do **not** build them on iOS or desktop “to catch up,” and do **not** finish them on Android in a migration sprint.

| Item | Inventory | Rule |
|------|-----------|------|
| Motion trip auto-shutter | `PnsMotionTrip`, `trip_armed` | PARTIAL all platforms |
| Astro recipe | `CaptureRecipe.Astro` | PARTIAL (label only) |
| Document recipe | `CaptureRecipe.Document` | PARTIAL (label only) |
| Capture journal UI | `CaptureJournal` / README claim | PARTIAL (bug-pack line only) |
| Dual-video PiP overlay | `docs/M14_12_DUAL_VIDEO.md` later sprint | Out of migration parity (Q18) |
| watchOS remote | Q5 | Later, after iOS capture parity |
| Planned / ProbeOnly / N/A catalog | §2.4 | Android-only future (Q10) |
| News-inbox P3 | §2.7 | Out of scope (Q14) |
| Fleet parity-debt ledger | Q15 | Stays Android work |

Concert / Museum (mute shutter + flash Off) and Airplane-safe (suppress launch prompts) **are** wired and **are** in parity.

---

## Fallback / Omit tables (no silent drops)

Every platform-specific Android behavior from inventory §1.19 plus shooting-adjacent items. **iOS = full camera**; **Desktop = companion**.

### iOS (full camera)

| Android behavior | Tag | Honest iOS equivalent |
|------------------|-----|------------------------|
| Camera2 RAW12 / DNG strip loadability / `DngCreator` pairing | **Omit** identical DNG; **Durable** ProRAW (Q3) | `AVCapturePhotoOutput` ProRAW / HEIF / JPEG |
| Logical multicam physical-total pairing | **Omit** | Single-device / virtual device session |
| Qualcomm session vendor keys, DCG session params, max-res unlock, root | **Omit** (Q7) | Hidden |
| CameraX Night / Bokeh extensions | **Fallback** | AVFoundation Night / Portrait quality where advertised; hide if absent |
| NightScape JPEG stack | **Fallback** | Same as Night photo if available; else hide |
| Variable aperture cycle | **Omit** | iPhone iris is not user-cyclic; hide F chip when a single aperture |
| Dedicated mono sensor / MONO dial | **Omit** unless a future device exposes a mono camera | Hide slot |
| Dodge 73 / 85 / 150 on one mid-tele + `SCALER_CROP_REGION` | **Fallback** | Native primes via `DiscoverySession`; digital crop on the tele device when equivalent mm is requested; do not fake HAL crop tags |
| RAW video `.mcraw` | **Omit** | Hide RAW-video lane |
| 4K120 / 240 / 480 honesty classes | **Fallback** | Advertise only what `AVCaptureDevice` formats actually do |
| Dual video stacked MP4 | **Durable** if multi-cam session works on that device; else **Omit** (hide Dual dial) | No PiP overlay (Q18) |
| Lineage USB UVC gadget | **Omit** (Q8) | Webcam tray → LAN preview Fallback or hide |
| `ACTION_IMAGE_CAPTURE` provider, QS tiles, lockscreen secure camera | **Omit** / **Fallback** Control Center + Shortcuts | |
| Home widget live HAL | **Fallback** | WidgetKit deep-link to camera; no live session |
| Dedicated hardware camera key | **Omit** | |
| Wear BLE GATT | **Omit** this wave (Q5) | |
| DND while preview | **Fallback** | Focus / interruption filter analogue |
| In-app GitHub APK install, F-Droid, Obtainium | **Omit** | TestFlight / App Store (Q9) |
| ML Kit face/eye | **Durable** | Vision on-device (Q6) |
| ZXing QR | **Durable** | Vision + ZXing or Vision-only barcode |
| GLES LUT preview | **Durable** | Metal 3D LUT, same `Lut3D` files |
| HDMI `Presentation` | **Fallback** | AirPlay / USB-C video when the OS offers it |
| Engineering probe hub | **Omit** consumer | |
| Root-blue chrome | **Omit** | |

### Desktop (companion)

| Android behavior | Tag | Honest desktop equivalent |
|------------------|-----|---------------------------|
| Phone 7×3 finder as home | **Omit** | Companion IA above |
| Camera2 / ProRAW / NightScape / DCG / 4K120 HAL | **Omit** | |
| UVC / laptop webcam as DNG camera | **Omit** | UVC host records JPEG/YUY2/H.264 **as delivered** |
| UVC gadget from the PC | **Omit** | Host only (Q8) |
| Tether `:28765` / MJPEG `:28770` / LAN `:28766` | **Durable** | Primary desktop job |
| Library desk + DNG review | **Durable** | |
| `SettingsExportBundle` | **Durable** | Q12 |
| QS / widget / intents / lockscreen / Wear | **Omit** | |
| Root / vendor keys | **Omit** | |
| Volume keys | **Fallback** | Keyboard shutter |

---

## Fidelity criteria (how we know we matched)

A surface **matches this brief** when:

1. **Inventory coverage** — it appears on [`PARITY_CHECKLIST.md`](PARITY_CHECKLIST.md) with Durable / Fallback / Omit / PARTIAL, never blank.
2. **Flow** — handheld still uses the eight-band stack; desktop still uses the companion nav, not a cloned phone frame.
3. **Look** — charcoal finder chrome, `#FF5C00` accent, JetBrains Mono HUD, 3:4 finder target, 7×3 **slot meanings** including empty cells.
4. **Honesty** — hide Unavailable; no greyed-out theater; Fallbacks are labeled in UI copy when the user would otherwise think they got the Android behavior (e.g. “ProRAW” not “DNG”, “Focus” not “DND”).
5. **PARTIAL** items are absent or clearly non-functional on **all** platforms, including Android, until a non-migration sprint.

---

## Deviations

Any implementation that breaks a rule in this brief (new band order, new palette, shipping PARTIAL items, pixel-forcing iOS chrome, desktop finder-as-home, silent feature drop) **must** be added here **and** approved by Edward before merge.

| Date | What | Why | Edward |
|------|------|-----|--------|
| — | *(none yet — Phase 4 not started)* | | |

Process: open a BUILD_PLAN `[HUMAN]` row, quote the brief sentence being broken, wait for approval, then append a row to this table in the same commit as the code.

---

## Document control

| Field | Value |
|-------|-------|
| Phase | 1 — design brief |
| Binding | Yes |
| App code | Untouched in this PR |
| Next | [`TECH_DECISION.md`](TECH_DECISION.md) (Phase 2, same PR) |
