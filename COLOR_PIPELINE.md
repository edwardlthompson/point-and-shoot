# Color pipeline

Source-of-truth for the **end-to-end color flow** in Point & Shoot, from raw
sensor photons to the encoded output (RAW DNG, AVIF, JXL, JPEG, MP4/H.265).
Satisfies BUILD_PLAN §7 ("Phase 4 - Color management, calibration & LUT
pipeline") cross-cutting requirement: "documents the reference chain and
identifies which stages live in the Camera2 hardware ISP, our Kotlin engine,
and our NDK encode path".

## Glossary

* **WB gains**: per-channel `(r, g, b)` multipliers anchored at `g = 1` so the
  average neutral patch reads as a true gray.
* **CCM**: 3x3 row-major color correction matrix that maps measured RGB to a
  reference color space (`out = M * in`).
* **LUT**: a 3D RGB lookup table - 17, 33, or 65 samples per axis, each cell
  holding an `(r, g, b)` output triple. Trilinear interpolation in between.
* **`.cube`**: Adobe Cube LUT text format (publicly documented, free to
  implement). Our primary import + export format.
* **dE_2000**: standard perceptual color-difference metric. Calibration aims
  for mean dE_2000 <= 3 on real-world charts and <= 1 on synthetic fixtures.
* **MTF50**: line-pairs per picture height where modulation transfer function
  drops to 50%. Sharpness baseline; recorded but not used to drive auto-fixes.

## The reference chain

The full pipeline is six stages; each stage runs in exactly one of three
process locations: **ISP** (vendor hardware), **engine** (our Kotlin), or
**encoder** (our NDK / framework). RAW takes a short-circuit path - it bypasses
every display-domain transform.

```
                 +---- (RAW path) ----------------------------+
                 |                                            v
sensor ---> demosaic ---> WB ---> CCM ---> tone curve ---> [LUT?] ---> encode
[ISP]         [ISP]      [ISP]   [eng]      [eng]         [eng]    [encoder]
```

| Stage | Location | What it does | RAW (DNG) | AVIF / JXL / JPEG | MP4 (H.265) |
|---|---|---|---|---|---|
| Sensor capture | ISP | Bayer 12-bit photons -> raw frame | source | source | source |
| Demosaic | ISP | Bayer -> RGB plane | NO (kept Bayer) | YES | YES |
| WB gains | ISP / engine | Apply per-channel scale; either ISP-applied + reported or applied by [`CalibrationProfile`](app/src/main/java/dev/pointandshoot/CalibrationProfile.kt) | reported in `AsShotNeutral` only | YES | YES |
| CCM | ISP / engine | Apply 3x3 color matrix; either ISP or [`CalibrationProfile.ccm`](app/src/main/java/dev/pointandshoot/CalibrationProfile.kt) | reported in `ColorMatrix1` / `ForwardMatrix1` only | YES | YES |
| Tone curve | engine | Display-gamma encoding (sRGB / Display P3 / Rec.2020) | NO (linear sensor data) | YES | YES |
| LUT | engine (CPU stills) / encoder (GLES preview + video) | Apply selected LUT via [`LutPipeline.applyTrilinear`](app/src/main/java/dev/pointandshoot/LutPipeline.kt) (CPU) or `sampler3D` (GLES) | NO (sidecar only) | YES if non-identity | YES if non-identity |
| Encode | encoder | Compress RGB plane to AVIF / JXL / JPEG / H.265 | DNG container, raw Bayer | encoded RGB | encoded YUV |

**The single most important rule** in this doc: **RAW (DNG) is never baked
through a LUT**. RAW lives in the sensor domain; LUTs are display-domain
transforms. Baking a LUT into a DNG would defeat the entire point of shooting
RAW (post-capture flexibility). The LUT name + SHA256 are recorded as sidecar
metadata so desktop tools (`darktable`, `RawTherapee`) can reproduce the look
optionally.

## Where the math lives

| Concern | File | Status |
|---|---|---|
| Pure-data 3D LUT type | [`Lut3D.kt`](app/src/main/java/dev/pointandshoot/Lut3D.kt) | shipped |
| Cube parser / serializer / CPU apply | [`LutPipeline.kt`](app/src/main/java/dev/pointandshoot/LutPipeline.kt) | shipped |
| Code-generated public-domain + Apache-2.0 LUTs | [`BuiltInLuts.kt`](app/src/main/java/dev/pointandshoot/BuiltInLuts.kt) | shipped |
| Runtime LUT enumeration | [`LutCatalog.kt`](app/src/main/java/dev/pointandshoot/LutCatalog.kt) | shipped |
| Calibration profile data class | [`CalibrationProfile.kt`](app/src/main/java/dev/pointandshoot/CalibrationProfile.kt) | shipped |
| WB + CCM solvers | [`CalibrationMath.kt`](app/src/main/java/dev/pointandshoot/CalibrationMath.kt) | shipped |
| Profile -> 33^3 cube exporter | [`CalibrationToLut.kt`](app/src/main/java/dev/pointandshoot/CalibrationToLut.kt) | shipped |
| GLES `sampler3D` shader + 3D-texture upload | `LutShader.kt` | pending Phase 4 capture engine |
| Manual 4-corner tap UI | `CalibrationCaptureScreen.kt` | pending Compose work |
| Patch sampler (homography + variance reject) | `CalibrationSampler.kt` | pending Phase 4 capture engine |
| Slanted-edge MTF50 measurement | `CalibrationSampler.measureMtf50` | pending FFT helper |
| Gradle `downloadBundledLuts` task (ACES, Filmic) | `app/build.gradle.kts` | pending Phase 4 capture engine |

The split is intentional: **everything pure-data is shipped now and unit-tested
on the JVM**. Anything that needs Camera2 frames (sampler), GLES context
(shader), or build-time downloads (ACES / Filmic) lands when the surrounding
capture engine arrives.

## Pinned numbers

These are pinned in source AND tested against in JUnit so doc/code drift is
caught at gate time.

| Constant | Value | Source-of-truth file |
|---|---|---|
| Supported LUT grid sizes | `[17, 33, 65]` | [`Lut3D.SUPPORTED_SIZES`](app/src/main/java/dev/pointandshoot/Lut3D.kt) |
| Default LUT grid size | 33 | [`BuiltInLuts.DEFAULT_SIZE`](app/src/main/java/dev/pointandshoot/BuiltInLuts.kt) |
| BT.601 luma weights | (0.299, 0.587, 0.114) | [`BuiltInLuts.bwBt601`](app/src/main/java/dev/pointandshoot/BuiltInLuts.kt) |
| BT.709 luma weights | (0.2126, 0.7152, 0.0722) | [`BuiltInLuts.bwBt709`](app/src/main/java/dev/pointandshoot/BuiltInLuts.kt) |
| Cinematic shadow tint | (0.30, 0.55, 0.70) (teal) | [`BuiltInLuts.pnsCinematic`](app/src/main/java/dev/pointandshoot/BuiltInLuts.kt) |
| Cinematic highlight tint | (1.00, 0.65, 0.35) (orange) | [`BuiltInLuts.pnsCinematic`](app/src/main/java/dev/pointandshoot/BuiltInLuts.kt) |
| Cinematic strength cap | 0.30 | [`BuiltInLuts.pnsCinematic`](app/src/main/java/dev/pointandshoot/BuiltInLuts.kt) |
| Allowed bundled-LUT SPDXs | `{Apache-2.0, BSD-2-Clause, BSD-3-Clause, MIT, CC0-1.0, public-domain}` | [`LutCatalog.ALLOWED_SPDX`](app/src/main/java/dev/pointandshoot/LutCatalog.kt) |
| WB-solve floor (G average) | 1e-4 | [`CalibrationMath.MIN_AVG_FOR_NEUTRAL_SOLVE`](app/src/main/java/dev/pointandshoot/CalibrationMath.kt) |
| Min patches for CCM solve | 3 | [`CalibrationMath.MIN_PATCHES_FOR_CCM`](app/src/main/java/dev/pointandshoot/CalibrationMath.kt) |
| Calibration target dE_2000 (synthetic) | <= 1.0 mean | BUILD_PLAN §7 V&V gate |
| Calibration target dE_2000 (real-world) | <= 3.0 mean, <= 6.0 max | BUILD_PLAN §7 V&V gate |
| MTF50 sanity floor (f/1.6 main wide) | >= 1500 lp/ph | BUILD_PLAN §7 V&V gate |
| LUT round-trip dE shrinkage | >= 80% | BUILD_PLAN §7 V&V gate |

## Encoder integration

When the still-encode path lands, the LUT stage applies CPU-side **after**
the YUV->RGB conversion and tone curve, **before** AVIF / JXL / JPEG
compression:

```
ImageReader (RGB plane)
  -> tone curve (engine)
  -> [identity-bypass] LutPipeline.applyTrilinear (engine)
  -> AVIF / JXL / JPEG encoder (NDK)
  -> CaptureStorage.openOutput().write(...)
  -> sibling .cube.txt OR .lutref.txt (if non-identity)
```

For video, the LUT applies **on the GLES surface** before the H.265 encoder
input. The shader accepts a `sampler3D` 3D RGB texture; identity bypass uses
[`Lut3D.isIdentity`](app/src/main/java/dev/pointandshoot/Lut3D.kt) to avoid
binding the 3D texture entirely (saves the per-frame fragment cost).

For preview, the LUT applies on the same GLES surface as video so the user
sees what they'll record.

## Calibration mode flow (when shipped)

1. User taps **Calibrate** in the HUD.
2. App enters a stripped-down preview with a 24-patch grid overlay.
3. User points at their printed reference chart and taps the 4 chart corners
   in clockwise order starting top-left.
4. App computes the homography from corner taps to the canonical chart layout
   (stored in `assets/calibration/targets/<targetId>.json`).
5. `CalibrationSampler.sample()` extracts mean RGB + variance per patch from
   the linear-light preview frame; rejects patches where variance exceeds
   `Defaults.MAX_PATCH_VARIANCE` (chart not flat / out of focus).
6. `CalibrationMath.computeWbGains(neutrals)` solves WB gains from the 6
   neutral patches.
7. `CalibrationMath.computeCcm(measured, target)` solves the 3x3 CCM via
   linear least squares.
8. `CalibrationSampler.measureMtf50()` measures sharpness from the 4 slanted-
   edge ROIs (corner targets on the generic 24-patch chart).
9. App displays per-patch dE_2000 + the mean / max numbers.
10. User taps **Save**; app persists `CalibrationProfile` JSON +
    `CalibrationToLut.toCube(profile)` text to `getExternalFilesDir(null)/calibration/`.
11. Subsequent captures reference the active profile via its `cameraId` +
    `illuminant`.
12. Host script `pns_hfr_autorun.ps1 -PullCalibration` copies the JSON +
    `.cube` to `hfr-runs/calibration/` for reproducibility.

The 4-corner tap design avoids an OpenCV dependency entirely. We trade a
small amount of user friction (4 taps instead of automatic alignment) for an
~ 8 MB binary saving and a much simpler license story.

## License posture

* **Bundled LUTs** must use one of the SPDX entries in
  [`LutCatalog.ALLOWED_SPDX`](app/src/main/java/dev/pointandshoot/LutCatalog.kt).
  No proprietary "free" LUT (Lightroom presets, DaVinci, FilmConvert, etc.)
  may be reverse-engineered into a generator function or vendored as a binary.
* **User-imported LUTs** via SAF skip the SPDX whitelist - the user owns
  their license compliance for imported content. The app neither mirrors nor
  redistributes user-imported `.cube` files.
* **Reference target Lab values** (X-Rite ColorChecker classic) are **facts**
  (CIE 1931 / D50 measurements); facts are not copyrightable, so the values
  are bundled. The chart **image** + the "ColorChecker" trademark are NOT
  bundled - the user prints or buys their own chart.

The `scripts/pns_license_inventory.ps1` LUT walker (planned in BUILD_PLAN §7)
will validate the on-disk asset folder layout (`LICENSE.txt` + `SOURCE.txt` +
`SHA256.txt` per leaf) AND that every leaf is referenced by `LICENSES.md` and
by `LutCatalog`.

## Cross-references

* BUILD_PLAN §7 - the parent plan for everything in this doc.
* `LICENSES.md` § "Bundled LUTs (planned - Phase 4)" - SPDX whitelist + per-
  LUT sourcing.
* `PERFORMANCE_BUDGETS.md` - LUT-apply rows (preview shader <= 2 ms / frame at
  1080p; still LUT CPU pass <= 80 ms for 12 MP) land here when shipped.
* `CAPTURE_ARCHITECTURE.md` - LUT stage placement (GLES for preview / video,
  IO/encode lane for stills, RAW skip).
* `FAILURE_MATRIX.md` - corrupt cube / unsupported size / GLES upload failure
  / patch-variance-too-high rows land here when shipped.
* `STORAGE_STRATEGY.md` - the sibling `.cube.txt` / `.lutref.txt` sidecar
  contract for non-identity captures.
