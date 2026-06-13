# Visual regression policy (Milestone T.9)

When to use **manual screencap**, **USB pixel gates**, or **deferred Paparazzi** for Point & Shoot UI proof. Preview **engine chrome is locked** — see [preview-chrome-layout-style-guide.md](preview-chrome-layout-style-guide.md) and `.cursor/rules/preview-chrome-ui-lock.mdc`.

## Decision matrix

| Surface | Primary proof | CI | Notes |
|---------|---------------|-----|-------|
| **Preview chrome** (finder, 7×3 grid, tray, readout) | [`scripts/pns_device_screencap.ps1`](../scripts/pns_device_screencap.ps1) + human review | No | Layout locked; behavioral fixes only |
| **Preview chrome UX** (focal tap, dial) | [`scripts/pns_chrome_ux_gate.ps1`](../scripts/pns_chrome_ux_gate.ps1) | No (USB) | Log + optional screencap artifacts under `hfr-runs/` |
| **Settings / About** (rail sheets) | Paparazzi (deferred) + screencap today | Host compile only | `@Ignore` scaffold in `*PaparazziTest.kt` until explicit unlock |
| **Face / Eye-AF overlay alignment** | [`scripts/pns_eye_af_pixel_gate.ps1`](../scripts/pns_eye_af_pixel_gate.ps1) | No (USB) | Requires face in frame; optional in `pns_prerelease_gate.ps1 -IncludeUsb` |
| **Store / F-Droid assets** | `pns_device_screencap.ps1` → `metadata/en-US/images/` | No | Milestone **T.10** |

## Manual screencap (default for visible UI)

```powershell
.\scripts\pns_sideload_and_launch.ps1 -LaunchScreen preview
.\scripts\pns_device_screencap.ps1 -OutPath .\hfr-runs\ui_proof_<feature>.png
adb shell am force-stop dev.pointandshoot
```

**When:** Any consumer-visible chrome or settings change (even behavioral — capture before/after for PR notes).

**Do not:** Commit large PNGs to git unless maintainer requests (F-Droid `metadata/` is the exception in T.10).

## USB pixel gates (numeric / overlay)

| Script | Proves |
|--------|--------|
| `pns_eye_af_pixel_gate.ps1` | Eye-AF overlay box aligns with expected region (PIL diff) |
| `pns_preview_jpeg_framing_gate.ps1` | Preview JPEG framing / letterbox |
| `pns_settings_rail_screencap.ps1` | Settings rail layout capture |

Run during **release prep** on **CPH2583** (or documented SKU). Not wired in GitHub Actions — device + camera session required.

## Paparazzi (deferred)

**Status:** Plugin + `@Ignore` tests compile; **no golden snapshots** checked in.

**Allowed scope when enabled (maintainer unlock):**

- `AboutRailSheetContent` / `AboutScreen` shell
- `ChromeSettingsSearchField` (Settings entry)

**Forbidden without explicit chrome unlock request:**

- `PreviewEngineScreen` and locked 7×3 / tray / finder geometry
- Paparazzi refactors that change layout constants for testability

**Enable workflow (future):**

1. Maintainer removes `@Ignore` on `AboutScreenPaparazziTest` / `ChromeSettingsSearchPaparazziTest`.
2. `./gradlew :app:recordPaparazziDebug` on a pinned JDK/host.
3. Commit snapshots under `app/src/test/snapshots/`.
4. CI runs `verifyPaparazziDebug` (not enabled in T.9).

## Pre-release orchestration

[`scripts/pns_prerelease_gate.ps1`](../scripts/pns_prerelease_gate.ps1):

- **Host lane (default):** doc + perf gates (expanded in Milestone **T.12**).
- **`-IncludeUsb`:** adds optional USB visual gates including **`pns_eye_af_pixel_gate.ps1`** (not CI).

## References

- [CONTRIBUTING.md](../CONTRIBUTING.md) — contributor checklist
- [KNOWLEDGE_BASE.md](../KNOWLEDGE_BASE.md) §3 — preview chrome index
- [BUILD_PLAN.md](../BUILD_PLAN.md) Milestone T Sprint T.9
