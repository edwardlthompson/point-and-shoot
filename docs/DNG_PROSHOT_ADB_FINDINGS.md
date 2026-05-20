# ProShot ADB findings (index)

Latest runs on **`8bf09993`** (CPH2655) after user ProShot captures (May 2026):

| Artifact | Path |
|----------|------|
| **New ProShot DNGs** (22:57 session) | [`hfr-runs/proshot_reference_20260518_025813/`](../hfr-runs/proshot_reference_20260518_025813/) — `20260517_225732/36/39.dng`, `diff_report.md` |
| Logcat + dumpsys (post-capture) | [`hfr-runs/proshot_adb_forensics_20260518_025806/`](../hfr-runs/proshot_adb_forensics_20260518_025806/) — `proshot_capture_logcat.txt`, `dumpsys_media_camera_grep.txt` |
| P&S baseline (FM reverted) | [`hfr-runs/aux_dng_capture_analyze_20260518_025101/`](../hfr-runs/aux_dng_capture_analyze_20260518_025101/) |

Earlier reference pull: `hfr-runs/proshot_reference_20260518_025412/`.

## Camera service: ProShot opens leaf devices (same ids as P&S focal slots)

From `dumpsys media.camera` **Camera service events** during the 22:57 ProShot session:

| Time (local) | Event | Maps to P&S slot |
|--------------|--------|------------------|
| 22:57:28 | **CONNECT device 3** (disconnect 2) | M14 UW (`cameraIdAfter=3`) |
| 22:57:33 | **CONNECT device 2** (disconnect 3) | M23 wide (`cameraIdAfter=2`) |
| 22:57:38 | **CONNECT device 4** (disconnect 2) | M73 tele (`cameraIdAfter=4`) |

ProShot does **not** stay on logical `0` for these three shots — it switches **leaf `CameraDevice` per lens**, matching P&S `focalSlotTap` routing.

Hal log on teardown: `logicalCameraId: 3, cameraId: 4` for tele — OEM may expose tele through logical parent **3** while API device id is **4**.

## Tag diff headline (unchanged)

- **ProShot and P&S DNGs both use `FM1[0,0]=0.4375` on all three lenses** — TIFF ForwardMatrix rewriting is not what separates good ProShot color.
- **ASN WB differs per file** in both apps.
- ProShot APK dex: `DngCreator`, `setPhysicalCameraId`, `LOGICAL_MULTI_CAMERA`, `LENS_SHADING` (`apk_strings_grep.txt` in latest reference folder).

## Next P&S work (not FM/ASN)

See [`DNG_PS_ALIGNMENT_SPIKE.md`](DNG_PS_ALIGNMENT_SPIKE.md): lens shading on still RAW, leaf-session `DngCreator` pairing without hybrid resolver, RAW pixel/metadata alignment — **not** post-`DngCreator` matrix patches.

Scripts: `scripts/pns_proshot_dng_reference_pull.ps1`, `scripts/pns_proshot_adb_forensics.ps1`.
