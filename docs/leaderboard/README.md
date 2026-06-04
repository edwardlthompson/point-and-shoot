# Fleet Camera Parity Leaderboard (GitHub Pages)

Public purchasing guide: **https://edwardlthompson.github.io/point-and-shoot/leaderboard/**

## Publish maintainer data

After USB Full parity sweep:

```powershell
.\scripts\pns_leaderboard_export_catalog.ps1
.\scripts\pns_leaderboard_site_publish.ps1
git add docs/leaderboard/
git commit -m "leaderboard: refresh device bundle"
git push
```

## GitHub Pages setup (one-time)

1. Repo **Settings → Pages → Build and deployment**
2. Source: **GitHub Actions** (workflow `leaderboard-pages.yml` deploys `/docs`)
3. Site root: `docs/index.html` redirects to `leaderboard/`

## Community submissions

1. Enable **Contribute to public leaderboard** in app connectivity settings
2. Engineering Hub → **Run Parity Sweep** (Full) → **Contribute to public leaderboard**
3. Ingest service: [`leaderboard-ingest/`](../../leaderboard-ingest/) (deploy to Render; set `BuildConfig.LEADERBOARD_INGEST_URL`)

Validation: `python scripts/leaderboard_submission_validate.py`

## Artifacts

| Path | Role |
|------|------|
| `data/site.json` | Manifest + OEM rankings |
| `data/devices/{slug}.json` | Per-device public profile |
| `data/catalog_taxonomy.json` | Feature display names |
| `data/glossary.json` | Tooltip definitions |
| `data/feed.json` | New-device feed |
| `submissions/approved/` | Validated community uploads |

## AnTuTu refresh

```powershell
python scripts/antutu_ranking_scrape.py
```

Monthly via `leaderboard-pages.yml` schedule / workflow_dispatch.
