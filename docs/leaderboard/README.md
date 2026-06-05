# Fleet Camera Parity Leaderboard (GitHub Pages)

Public purchasing guide: **https://edwardlthompson.github.io/point-and-shoot/leaderboard/**

Methodology: [`docs/CAMERA2_OEM_DISPARITY.md`](../CAMERA2_OEM_DISPARITY.md)

## Publish maintainer data

After USB Full parity sweep:

```powershell
.\scripts\pns_leaderboard_export_catalog.ps1
.\scripts\pns_leaderboard_site_publish.ps1 -SkipGsmarenaScrape
.\scripts\pns_leaderboard_pages_push.ps1
```

Or one-shot push (publish + catalog + commit):

```powershell
.\scripts\pns_leaderboard_pages_push.ps1
```

## GitHub Pages setup (one-time)

1. Repo **Settings → Pages → Build and deployment**
2. Source: **GitHub Actions** (workflow `leaderboard-pages.yml` deploys `/docs`)
3. Site root: `docs/index.html` redirects to `leaderboard/`

## Community submissions

1. Enable **Contribute to public leaderboard** in app connectivity settings
2. Engineering Hub → check **Leaderboard readiness** → **Run Parity Sweep** (Full) → **Contribute**
3. Ingest: [`leaderboard-ingest/`](../../leaderboard-ingest/) — deploy to Render; add cert SHA-256 to `config/signing_pins.json`; set `BuildConfig.LEADERBOARD_INGEST_URL`

Validation: `python scripts/leaderboard_submission_validate.py`

## Artifacts

| Path | Role |
|------|------|
| `data/site.json` | Manifest + OEM rankings |
| `data/product_groups.json` | Stock vs custom vs GSMArena advertised (separate line items) |
| `data/oem_accountability.json` | OEM restriction + resolution betrayal aggregates |
| `data/devices/{slug}.json` | Per-device public profile |
| `data/gsmarena_device_specs.json` | Advertised spec scrape (untested) |
| `data/leaderboard.csv` | Flat export |
| `data/feed.xml` | RSS |
| `data/catalog_taxonomy.json` | Feature display names |
| `data/glossary.json` | Tooltip definitions |

## GSMArena refresh

```powershell
python scripts/gsmarena_sensor_scrape.py
python scripts/gsmarena_device_specs_scrape.py
.\scripts\pns_leaderboard_site_publish.ps1
```

On HTTP 429, use `-SkipGsmarenaScrape` and committed cache JSON.

Weekly CI: `leaderboard-pages.yml` (non-blocking scrapes).
