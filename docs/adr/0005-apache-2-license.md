# ADR-0005 — Apache-2.0 project license (not MIT)

- **Status:** Accepted
- **Date:** 2026-06-12

## Context

Generic Cursor project templates recommend **MIT** for maximum permissiveness. Point & Shoot ships a native camera stack, vendor HAL integration, and patent-sensitive Camera2/NDK code. The repo has used **Apache-2.0** since inception (`LICENSE`, README badge, F-Droid metadata).

## Decision

1. Keep **Apache-2.0** as the project license in [`LICENSE`](../../LICENSE).
2. Document third-party deps in [`LICENSES.md`](../../LICENSES.md) with `pns_license_inventory.ps1` drift gate.
3. F-Droid / store metadata use `License: Apache-2.0`.
4. Do **not** relicense to MIT for template alignment.

## Consequences

- Apache-2.0 patent grant and NOTICE-style attribution expectations apply to contributors.
- Template onboarding docs map "MIT" template rows to this ADR.
- Bundled assets (fonts, LUT sidecars) keep separate SPDX files under `app/src/main/assets/`.

## References

- [`LICENSE`](../../LICENSE)
- [`LICENSES.md`](../../LICENSES.md)
- [`metadata/metadata.yml`](../../metadata/metadata.yml)
