# Camera capability taxonomy (Milestone 18)

**Purpose:** Canonical categories and naming for `CameraCapabilityCatalog` rows, fleet matrix `featureGates`, and **Fleet Parity Sweep** cells.

**Source of truth in code:** `dev.pointandshoot.fleet.CameraCapabilityCatalog` · matrix `capabilityCatalog[]` on `files/fleet_device_matrix.json`.

## Categories

| Category | Prefix examples | Parity sweep |
|----------|-----------------|--------------|
| Still capture | `still.*`, `raw.*` | DNG, AVIF, bracket, ZSL |
| Video | `video.*` | H.264/HEVC/AV1, HFR, DCG, RAW, dual |
| Lens | `lens.*`, `af.*` | Focal row, macro, OIS/EIS |
| Preview HUD | `hud.*`, `preview.*` | Zebra, histogram, QR |
| Face & AF | `face.*`, `af.*` | Face detect, eye-AF, rack focus |
| Audio | `audio.*` | Hi-Fi, external mic, spatial |
| Performance | `perf.*` | Thermal adaptive, capture latency |
| Fleet / product | `fleet.*`, `product.*` | Matrix, parity sweep, format picker |
| CameraX | `camerax.*` | Extension probe (informational) |
| Root | `root.*` | Vendor keys, governor (root-only) |
| Legacy | `legacy.*` | Camera1 probe |

## Catalog row fields

- **`id`** — stable dot-notation key (grep-friendly)
- **`appStatus`** — `Shipped` | `Partial` | `ProbeOnly` | `Planned` | `NotApplicable`
- **`surfacing`** — `format_picker`, `focal_row`, `mode_dial`, `engineering_hub`, …
- **`visibilityPolicy`** — `HideWhenUnavailable` (default) | `ShowDisabledEngineering` | `RootOnly` | `AlwaysShow`

## Gap classes (Fleet Parity Sweep)

| Class | Meaning |
|-------|---------|
| `OK` | Advertised capability proven on device |
| `GAP_ADVERTISED_NOT_PROVEN` | Matrix/catalog says yes; sweep failed |
| `GAP_DELIVERY_MISMATCH` | Capture ran but fps/resolution ≠ picked settings |
| `GAP_PROVEN_NOT_ADVERTISED` | Works but evaluation pessimistic |
| `GAP_PLANNED` | Catalog `Planned`; not yet in app |

### M21 extended gap classes + consumer impact

| Class | Blocks Full pass? | Consumer impact |
|-------|-------------------|-----------------|
| `GAP_PROBE_INVENTORY` | No | engineering_only |
| `GAP_ADVERTISED_NOT_SURFACED` / `GAP_SURFACED_NOT_ADVERTISED` | No | planning |
| `GAP_REGRESSION_SINCE_BASELINE` | Yes (with `-BaselineTag`) | ship_blocker |
| `GAP_CONFLICT_RISK` | No | informational |
| `GAP_UNAUTOMATED` | No | engineering_only |
| `GAP_FLAKE_SUSPECT` | No | informational |
| `GAP_HUMAN_ONLY` | No | informational (DNG color, ACR) |
| `GAP_FLEET_PLUGIN_CANDIDATE` | No | engineering_only |

**Quick tier:** matrix `scanTier=quick` treats `sessionOk` as unknown (null) — parity smoke uses HAL `advertised` without session-probe false negatives.

## Schema

- Matrix **`schemaVersion`:** **2** (M18) — accepts v1 artifacts; v2 adds optional `product.focalRow`, `product.formatQuality`.
- Catalog **`catalogVersion`:** bumped with each taxonomy expansion (`CameraCapabilityCatalog.CATALOG_VERSION`).

## References

- `docs/FLEET_DEVICE_CAPABILITY_MATRIX.md`
- `docs/FLEET_PARITY_SWEEP.md`
- `docs/PNS_TECHNICAL_SETTINGS.md`
