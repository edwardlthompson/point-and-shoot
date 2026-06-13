# ReferenceApp reference DNGs (CPH2655 / OP13)

Lens-matched ProShot captures for Sprint **13.3g-4** parity gates (`dng_referenceapp_parity_gate.py`).

| File | ReferenceApp slot | Camera id |
|------|----------------|-----------|
| `referenceapp_uw_cam3.dng` | 15 mm (UW) | 3 |
| `referenceapp_wide_cam2.dng` | 23 mm (wide) | 2 |
| `referenceapp_tele_cam4.dng` | 73 mm (tele) | 4 |

Refresh on USB:

```powershell
.\scripts\pns_referenceapp_reference_sync.ps1 -FromForensicsDir hfr-runs\referenceapp_live_forensics_* -FixtureProfile Cph2655 -Serial 8bf09993
```

Source manifest: `fixture_sync_manifest.json` (updated each sync).

**Note:** On LegacySku, ReferenceApp UW and wide DNGs may share the same `ColorMatrix2[0,0]` (~1.4337); CI uses `--skip-wide-cal-leak` for these fixtures. The leak check applies to **P&S** `useWideLeafCalibrationForAuxDng` patches, not ReferenceApp reference files.
