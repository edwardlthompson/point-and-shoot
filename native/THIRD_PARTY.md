# THIRD_PARTY.md - native dependencies

Tracks the upstream FOSS sources that `native/CMakeLists.txt` will consume via
`FetchContent_Declare` once Phase 1 wires real encoder bodies. Phase 0 ships
JNI stubs only; this file is intentionally pre-populated so license review
can land before any source archives are fetched.

> **Status:** Encode path uses CMake `FetchContent` (no vendor trees in git). Upstream
> tarballs/git pins are listed below and duplicated in `native/CMakeLists.txt`.

## License compatibility summary

The host project ships under Apache-2.0 (see `LICENSES.md`). Every dependency
listed below has been hand-checked against
[Apache-2.0 compatibility](https://www.apache.org/legal/resolved.html) and
the project's `pns_verify_toolchain.ps1` FOSS audit (which only rejects
Play Services / Firebase / ML Kit / Play Billing / Ads).

| Library  | Direct dep of | Upstream | License | SPDX | Apache-2.0 compat | Pinned ref | SHA-256 (archive where used) |
|----------|---------------|----------|---------|------|---------------------|------------|---------|
| libavif  | `pns_native`  | https://github.com/AOMediaCodec/libavif | BSD-2-Clause | BSD-2-Clause | OK | tag `v1.2.1` tarball | `9c859c7c12ccb0f407511bfe303e6a7247f5f6738f54852662c6df8048daddf4` |
| SVT-AV1  | libavif (LOCAL encoder; replaces libaom on Windows hosts without Perl) | https://gitlab.com/AOMediaCodec/SVT-AV1 | BSD-3-Clause | BSD-3-Clause | OK | Git tag `v3.0.1` (via libavif `LocalSvt.cmake`) | *(git clone; not SHA-pinned in CMake)* |
| libjxl   | `pns_native`  | https://github.com/libjxl/libjxl | BSD-3-Clause | BSD-3-Clause | OK | tag `v0.11.1` tarball | `1492dfef8dd6c3036446ac3b340005d92ab92f7d48ee3271b5dac1d36945d3d9` |
| highway  | libjxl `third_party` (vendored from tarball into extracted libjxl) | https://github.com/google/highway | Apache-2.0 | Apache-2.0 | OK (same license) | commit `457c891775a7397bdb0376bb1031e6e027af1c48` tarball | `5124b0501c98d9930dbb065bfa1a5bbbd59ce0f12facb7e1e33aaef01a5f1f1a` |
| brotli   | libjxl `third_party` | https://github.com/google/brotli | MIT | MIT | OK | commit `36533a866ed1ca4b75cf049f4521e4ec5fe24727` tarball | `9dbeae5b67739ad00f8e355a20f9af5507ed578abace0a4939a54c6c2b597005` |
| skcms    | libjxl `third_party` | https://skia.googlesource.com/skcms | BSD-3-Clause | BSD-3-Clause | OK | commit `42030a771244ba67f86b1c1c76a6493f873c5f91` git | *(git fetch; not tarball SHA)* |

## Sourcing rules

Every entry in the table must satisfy ALL of the following before Phase 1 lands:

1. **Pinned upstream tag** (not a moving branch like `main`). The tag is
   captured in `CMakeLists.txt` `FetchContent_Declare(URL ...)` and copied
   into the table above for human review.
2. **Pinned SHA-256** of the downloaded tarball. CMake fails the build if
   the digest mismatches, so a supply-chain compromise (registry hijack,
   stale mirror) is detected at fetch time, not at install time.
3. **License sidecar** captured under `app/src/main/assets/legal/native/<name>/`:
   - `LICENSE.txt`: verbatim copy of the upstream LICENSE file.
   - `SOURCE.txt`: upstream URL + pinned tag + SHA-256.
   - `NOTICE.txt`: any third-party attribution required by the license
     (Apache-2.0 NOTICE files; BSD attribution lines).
4. **Cross-reference in `LICENSES.md`** "Native dependencies (planned -
   Phase 1)" section, mirroring the bundled-LUT table that already exists.

## Fallback if a dependency cannot be sourced cleanly

If an upstream changes license or vanishes, the capture engine ALREADY has a
defensible fallback per `FAILURE_MATRIX.md`:

- `NativeEncoders.isAvailable = false` (the .so didn't load, or build was
  disabled with `pns.nativeEncoders=false`).
- `EncoderRoute.decide(profile, nativeAvailable = false)` substitutes a JPEG
  for the AVIF / JXL output. RAW / DNG always survives - it doesn't go
  through the NDK.

That fallback path is fully tested in `EncoderRouteTest` and
`NativeEncodersFallbackTest` so this isn't an aspirational claim.

## How to update this file

1. Update `native/CMakeLists.txt` `FetchContent_Declare` block(s) with the new
   pinned tag + SHA-256.
2. Update the matching row in the table above.
3. Drop the upstream LICENSE / NOTICE files into
   `app/src/main/assets/legal/native/<name>/`.
4. Run `scripts/pns_license_inventory.ps1` - it walks the legal asset
   tree and asserts every entry here has the matching sidecars on disk.
5. Update `CHANGELOG.md` Unreleased "Added" section.
