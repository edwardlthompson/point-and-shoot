# DNG color revert checkpoint

**Tag:** `checkpoint/dng-decode-all-cameras-baseline`

**Meaning:** All back cameras produce **openable** DNGs (pipeline pairing + RAW_SENSOR routing restored). Ultrawide and tele may still show **wrong color / black levels** in desktop RAW converters because CPH2655 HAL copies wide **ForwardMatrix** / **AsShotNeutral** to aux sensors — color post-process in `Dng12Saver` was not wired yet at this tag.

**Restore this baseline:**

```powershell
git checkout checkpoint/dng-decode-all-cameras-baseline
```

**After this tag:** `Dng12Saver` applies `DngForwardMatrixFix` + `TiffDngColorMatrixPatch` for aux physical ids on CPH2655-class devices.

**Focal triage slots (reference wide, not 150 mm tele):** M14 ultrawide, M23 wide, **M73** native tele (not M150 digital crop).
