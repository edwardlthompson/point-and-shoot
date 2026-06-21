# Agent memory (ephemeral)

**Not** bisect history — that stays in [`docs/AGENT_REGRESSION_MEMORY.md`](docs/AGENT_REGRESSION_MEMORY.md). Update this file only at **session startup**, **milestone boundary**, or **architectural pivot** (see [`AGENTS.md`](AGENTS.md) Template file map).

---

## Current focus (updated 2026-06-21 — AUDIT-2026-06-21)

| Field | Value |
|-------|--------|
| **Active milestone** | **H** — human & publication |
| **Active sprint** | None (AUDIT-2026-06-21 closed) |
| **Ship line** | **0.14.0-beta.15** / `versionCode` **22009** / catalog **v6** |
| **Primary USB device** | OnePlus 12 **CPH2583** — wireless ADB `b5214fc6` (`scripts/pns_adb_device.env`) |
| **Agent lanes closed** | **Milestone 28** (Waves A–D), **AUDIT-2026-06-21**, **AUDIT3**, **AUDIT2**, **H.CRI-5**, **T.14**, **H.HYGIENE** |

## Toolchain stack (2026-06-21)

| Component | Version |
|-----------|---------|
| Gradle | **9.5.1** |
| AGP | **9.1.1** |
| Kotlin | **2.4.0** |
| compileSdk | **37** |
| Compose BOM | **2026.05.01** |
| CameraX | **1.6.1** |

## Last gates (2026-06-21)

| Gate | Result | Artifact / notes |
|------|--------|------------------|
| `pns_validate_bootstrap.ps1` | **PASS** | `/audit` step 1 |
| `pns_local_dev_parallel.ps1` (Tier 0) | **FAIL** | `python` not on PATH (fixture gate) |
| `pns_verify_toolchain.ps1 -RunTests` | **PASS** | After `$PSHOME` PowerShell host fix |
| `:app:detekt` | **PASS** | `MediaCodecVideoRecorder` muxer helper |
| USB capture + chrome | **PASS** | `photo_capture_verify_20260621_043524`, `chrome_ux_gate_20260621_043540` |
| Parity Delta | **PASS** | `parity_sweep_20260621_043558` (0 ship blockers) |
| `pns_eye_af_pixel_gate.ps1` | **FAIL** | No face in frame; open **CRI-032** |
| `pns_mediacodec_hfr_verify -GateProfile vf` | **FAIL** | `ffprobe` not on PATH (host) |
| Dependabot / CodeQL (`gh`) | **SKIP** | `gh` not on PATH |

## Open blockers

| ID | Area | Status |
|----|------|--------|
| **CRI-032** | Eye-AF pixel gate | Only open **[AGENT]** row — needs face in frame for green markers |
| **CRI-033…035** | Human | DCG colors, store/PRIVACY, OP13 ACR |
| **H.9** | Signing | Debug-key release; human keystore + `signing_pins.json` |
| **Host** | Tier 0 / VF | Install **Python 3** + **FFmpeg/ffprobe** on PATH for full local gates |

## Immediate next steps

1. **[HUMAN]** Milestone H checklist (**CRI-032…035**) + subjective UX (**H.8.3** DCG colors)
2. **[AGENT]** Re-run `pns_eye_af_pixel_gate.ps1` with face in finder when capture lane free
3. **[HUMAN]** **H.9** release sign-off — GitHub release for **beta.15** when approved

---

**Document control:** 2026-06-21 — AUDIT-2026-06-21 + M28 closure; refresh at Milestone H ship.
