# Phase 3 — Parity checklist

**Use with:** [`INVENTORY.md`](INVENTORY.md), [`DESIGN_BRIEF.md`](DESIGN_BRIEF.md), [`TECH_DECISION.md`](TECH_DECISION.md), `BUILD_PLAN.md` MIG.* rows.

**Rule:** A feature is not “ported” until this row is Durable or an honest Fallback/Omit/PARTIAL, on **each** platform in scope, with the gate passed. Blank cells are bugs. Silent drops are forbidden.

**Freeze:** Android consumer behavior is frozen at migration start (Q15) except the `DCIM/PointAndShoot` folder bug (separate PR from `main`).

Tags: **D** Durable · **F** Fallback · **O** Omit · **P** PARTIAL (all platforms; do not implement in migration) · **n/a** not in that product role.

Desktop column is the **companion** (Q1), not a phone clone.

---

## How to tick

- Android: existing USB/host gates in `AGENTS.md` still apply; do not re-prove the whole camera unless a freeze break is suspected.
- iOS: device (not simulator-only) for capture/ProRAW/Vision; simulator OK for chrome layout.
- Desktop: host smoke + at least one real UVC device **or** a phone tether session.
- Design fidelity: DESIGN_BRIEF eight-band stack on handheld; companion nav on desktop; charcoal/`#FF5C00`/mono HUD.

---

## A. Consumer screens (inventory C*)

| ID | Surface | Android | iOS | Desktop | Gate |
|----|---------|---------|-----|---------|------|
| C1 | Welcome / permissions (incl. informational Vibration + DND cards) | D | F (Info.plist + Focus copy) | F (OS prompts) | Launch cold start; no crash if optional perms denied |
| C2 | Live preview engine | D | D (SwiftUI + AVFoundation) | n/a as home; F inspector if tether live-view | iOS: still+video session; Android: existing chrome/capture gates |
| C3 | In-app gallery | D | D PhotoKit | D filesystem/library | Open last file; pinch/double-tap; delete+undo |
| C4 | Library desk (all tabs) | D | D | D (primary) | Each tab reachable; Immich HTTPS-only still holds |
| C5 | About / heritage | D | D | D | Venmo/update/license links platform-appropriate |
| C6 | Calibrate | D | D | D (file/LUT) | Chart flow or honest Omit if no camera |
| C7 | Import LUT | D | D | D | `.cube` appears in picker |
| C8 | QR scan dedicated | D | D | F (webcam) or O | Decode a fixture QR |
| C9 | HUD settings standalone | D | D | F (subset) | Flags persist via SettingsExportBundle |
| C10 | Share receive | D | F share extension | F OS open-with | |
| C11 | Mock preview | D | F (offline pattern) | F | Optional engineering |
| C12 | Wear remote | D | O this wave (Q5) | O | |

---

## B. In-preview sheets (D1–D29)

All **D** on iOS as the same *fields* (SwiftUI sheets). Desktop: **n/a** on finder; **D/F** where the companion exposes the same setting (FPS, LUT, geotag, etc.).

| ID | Surface | iOS | Desktop companion | Notes |
|----|---------|-----|-------------------|-------|
| D1–D10 | Settings rail tree | D | D (window) | Same search index |
| D11–D17 | ISO/SS/F/WB/AF/LUT pickers | D | F on UVC if the camera exposes the control; else O | Hide F if single iris |
| D18 | Still format picker | D (HEIF/JPEG/ProRAW) | F JPEG (no DNG from UVC) | IMG chip long-press + tray FAB |
| D19 | Video format picker | D | F | Honesty labels |
| D20 | Command dial | D | n/a | Dual hide if no multi-cam |
| D21–D23 | Timer / flash / false color | D | F / n/a | |
| D24 | Snackbars | D | D | |
| D25 | Front/rear spotlight | D | n/a | |
| D26–D27 | Self-timer / burst HUD | D | n/a | |
| D28 | Settings search | D | D | |
| D29 | Donate / update prompts | D | F (desktop updater) | Airplane-safe still suppresses |

---

## C. Finder overlays

| Overlay | iOS | Desktop | PARTIAL? |
|---------|-----|---------|----------|
| Luma / RGB histogram | D | F on UVC/tether frame | |
| Zebras / false color | D | F | |
| Focus peaking | D Metal | F | |
| Horizon | D CoreMotion | O unless tether overlays phone data | |
| Eye-AF / faces | D Vision | O/F | |
| Crop guides / grids | D | F | |
| Selfie ring | D | n/a | |
| Video pillar / tally / remaining time | D | n/a | |
| Concurrent rear PiP preview | O if no multi-cam; D if session OK | O | Encode PiP overlay is **out** (Q18) |
| Scene vendor hints chip | O (Android OEM keys) | O | Probe stays Android |

---

## D. Capture formats and pipeline

| Item | iOS | Desktop | Gate |
|------|-----|---------|------|
| JPEG / HEIF | D | D JPEG from UVC | File opens in Photos / OS viewer |
| ProRAW | D (Q3) | O capture; D review of phone files | iOS: Photos + a desktop raw viewer can open |
| Camera2 DNG strip / DngCreator pairing | O | O capture; D review | Android locks unchanged |
| AVIF / JXL / TIFF / motion photo | F if encoder exists; else O (hide) | F transcode later; else O | |
| H.264 / HEVC video | D | D from UVC/tether | |
| AV1 / VP9 | F/O per device | F | |
| HDR10 / DCG session | F (Apple HDR) | O | |
| RAW `.mcraw` | O | O | Hide lane |
| Dual stacked video | D or O (hide Dual) | O | No PiP overlay |
| Time-lapse | D | F | |
| Audio hi-fi / wind / gain | D as hardware allows | F | |

---

## E. Controls (inventory §1.4.6)

| Control | iOS | Desktop |
|---------|-----|---------|
| ISO / SS / WB / AF / EC (H) | D where `AVCaptureDevice` allows; H = F (bias / custom meter) | F if UVC; else auto-only |
| Focal / lens switch | D DiscoverySession; 73/85/150 = F crop | n/a |
| Flash / torch | D | O |
| Timer / burst | D | F interval on UVC |
| FPS | D honest formats | F |
| OIS/EIS | D / F | O |
| LUT still/video | D Metal | D on review; F on live UVC |
| Smile still | D Vision | O |
| Rack / breathing / auto-frame | D or O hide | O |
| Intervalometer | D | F |
| Motion trip | **P** | **P** |
| Bulb / focus stack prefs | F labels only until Android engine exists | n/a |
| Concert / Museum / Airplane recipes | D | n/a (phone-side) |
| Astro / Document recipes | **P** | **P** |
| Power profile | D | F |
| Workflow presets | D via SettingsExportBundle | D |

---

## F. Gallery / storage / metadata

| Item | iOS | Desktop |
|------|-----|---------|
| Folder / album `PointAndShoot` (Q11) | F PhotoKit | D |
| `pns_{utc}_{profile}_{seq}` names | D | D |
| DNG in-place metadata policy | n/a (ProRAW) | D when touching Android DNGs — never ExifInterface-style rewrite |
| Geotag off/coarse/precise | D | D if file has GPS |
| Privacy strip / redact / evidence | D | D |
| Vault / Immich / WebDAV / SAF | D (Files / HTTPS) | D |
| Capture journal UI | **P** | **P** |

---

## G. Settings persistence

| Store | iOS | Desktop |
|-------|-----|---------|
| `pns_preview_chrome`, `pns_hud_settings`, tray, UX, guides | D (App Group / files) | D JSON |
| `SettingsExportBundle` round-trip (Q12) | D | D |
| `pns_product_systems` / `pns_power_profile` (not in Android Auto Backup today) | D include in export | D |
| `pns_updates` | O APK; F App Store | F installer |

---

## H. Platform entry points

| Item | iOS | Desktop |
|------|-----|---------|
| Default camera intents / IMAGE_CAPTURE | O | O |
| QS tiles | F Control Center | O |
| Widget | F deep-link | O |
| Lockscreen camera | F | O |
| Volume shutter | D | F keyboard |
| Hardware camera key | O | O |
| USB gadget webcam | O | O |
| USB UVC **host** | n/a | D |
| Tether HTTP / MJPEG / LAN | D client+optional server | D |
| Wear | O this wave | O |
| Root / vendor / max-res | O | O |

---

## I. Explicit PARTIAL (all platforms)

Do not tick these as shipped. Do not build them in MIG.* .

| Item | Inventory |
|------|-----------|
| Motion trip fire hook | §1.4.6 |
| Astro recipe capture path | `CaptureRecipe.Astro` |
| Document recipe capture path | `CaptureRecipe.Document` |
| Capture journal consumer UI | `CaptureJournal` vs README |
| Dual-video PiP overlay | Q18 / M14.12 |

---

## J. Design-fidelity checklist (handheld)

- [ ] Eight-band stack order unchanged
- [ ] 3:4 finder target; slack above tile
- [ ] Readout chip set matches photo vs video lock
- [ ] 7×3 slot meanings; Settings r2c6; empty cell when hidden
- [ ] Tray: center shutter; Photo/Video/(Webcam-or-hidden) left of dial
- [ ] Charcoal chrome; `#FF5C00`; JetBrains Mono HUD
- [ ] Dynamic Island / safe area is the top band (iOS), not an overlay on the finder
- [ ] Unavailable hidden, not greyed
- [ ] Fallback labeled when the user would confuse it with Android (ProRAW vs DNG, Focus vs DND)

## K. Design-fidelity checklist (desktop companion)

- [ ] Home is Tether / Library / Camera(UVC) / Presets / About — not 7×3
- [ ] MJPEG `:28770` and control `:28765` work against a running Android (or later iOS) engine
- [ ] UVC capture files are not labeled DNG
- [ ] Library desk tabs present
- [ ] SettingsExportBundle import/export

---

## Sign-off

Final MIG sprint ticks this whole file. Edward signs DESIGN_BRIEF Deviations if anything here was waived.

| Platform | Owner | Date | Result |
|----------|-------|------|--------|
| Android freeze | | | |
| iOS | | | |
| Desktop | | | |
