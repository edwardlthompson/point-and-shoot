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

## Schema

- Matrix **`schemaVersion`:** **2** (M18) — accepts v1 artifacts; v2 adds optional `product.focalRow`, `product.formatQuality`.
- Catalog **`catalogVersion`:** bumped with each taxonomy expansion (`CameraCapabilityCatalog.CATALOG_VERSION`).

## References

- `docs/FLEET_DEVICE_CAPABILITY_MATRIX.md`
- `docs/FLEET_PARITY_SWEEP.md`
- `docs/PNS_TECHNICAL_SETTINGS.md`
