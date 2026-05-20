# DNG color revert checkpoint

**Tag:** `checkpoint/dng-decode-all-cameras-baseline`

**Meaning:** All back cameras produce **openable** DNGs (pipeline pairing + RAW_SENSOR routing restored). Ultrawide and tele may still show **wrong color** in desktop RAW converters on CPH2655-class devices.

**Restore this baseline:**

```powershell
git checkout checkpoint/dng-decode-all-cameras-baseline
```

**FM/ASN experiment (reverted May 2026):** Post-`DngCreator` **ForwardMatrix** / **AsShotNeutral** patching in **`Dng12Saver`** was tried and **reverted** — it broke wide and tele DNGs when applied to the wrong physical ids. **Do not reapply** without maintainer sign-off and USB proof. Color investigation uses **ProShot** reference DNGs on-device; see **`docs/DNG_REFERENCE_APPS.md`**.

**Focal triage slots (reference wide, not 150 mm tele):** M14 ultrawide, M23 wide, **M73** native tele (not M150 digital crop).
