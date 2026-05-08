# THIRD_PARTY.md - native dependencies

Tracks the upstream FOSS sources that `native/CMakeLists.txt` will consume via
`FetchContent_Declare` once Phase 1 wires real encoder bodies. Phase 0 ships
JNI stubs only; this file is intentionally pre-populated so license review
can land before any source archives are fetched.

> **Status:** Phase 0 / scaffolding. No upstream sources are committed to
> this repository. Pinned tags + SHA-256 digests will be filled in by the
> Phase 1 PR that turns on `pns.nativeEncoders=true`.

## License compatibility summary

The host project ships under Apache-2.0 (see `LICENSES.md`). Every dependency
listed below has been hand-checked against
[Apache-2.0 compatibility](https://www.apache.org/legal/resolved.html) and
the project's `pns_verify_toolchain.ps1` FOSS audit (which only rejects
Play Services / Firebase / ML Kit / Play Billing / Ads).

| Library  | Direct dep of | Upstream | License | SPDX | Apache-2.0 compat | Phase 0 status | Pinned tag | SHA-256 |
|----------|---------------|----------|---------|------|---------------------|----------------|------------|---------|
| libavif  | `pns_native`  | https://github.com/AOMediaCodec/libavif | BSD-2-Clause | BSD-2-Clause | OK | not fetched | TBD | TBD |
| aom      | libavif (transitively) | https://aomedia.googlesource.com/aom | BSD-2 + Alliance for Open Media patent license | BSD-2-Clause AND ALLIANCE-FOR-OPEN-MEDIA-PATENT-LICENSE-1.0 | OK | not fetched | TBD | TBD |
| libjxl   | `pns_native`  | https://github.com/libjxl/libjxl | BSD-3-Clause | BSD-3-Clause | OK | not fetched | TBD | TBD |
| highway  | libjxl (transitively) | https://github.com/google/highway | Apache-2.0 | Apache-2.0 | OK (same license) | not fetched | TBD | TBD |
| brotli   | libjxl (transitively) | https://github.com/google/brotli | MIT | MIT | OK | not fetched | TBD | TBD |

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
