# F-Droid metadata (Milestone T.10)

In-repo **fastlane-style** metadata for a future [F-Droid](https://f-droid.org/) / [fdroiddata](https://gitlab.com/fdroid/fdroiddata) merge request.

**User-facing distribution today:** GitHub Releases + Obtainium — see root [`README.md`](../README.md).

## Layout

| Path | Role |
|------|------|
| [`metadata.yml`](metadata.yml) | Repo + **Builds:** Gradle/NDK recipe; sync `versionCode` / `versionName` with `app/build.gradle.kts` on every release |
| [`en-US/title.txt`](en-US/title.txt) | Store title |
| [`en-US/short_description.txt`](en-US/short_description.txt) | ≤80 chars |
| [`en-US/full_description.txt`](en-US/full_description.txt) | Long description (HTML `<b>` allowed) |
| [`en-US/changelogs/<versionCode>.txt`](en-US/changelogs/) | Per-release notes (excerpt from `CHANGELOG.md`) |
| [`en-US/images/phoneScreenshots/`](en-US/images/phoneScreenshots/) | Phone PNGs |
| [`en-US/images/sevenInchScreenshots/`](en-US/images/sevenInchScreenshots/) | 7" tablet PNGs |

## Host gate

```powershell
.\scripts\pns_fdroid_metadata_validate.ps1
```

Wired in [`scripts/pns_prerelease_gate.ps1`](../scripts/pns_prerelease_gate.ps1) (expanded in Milestone **T.12**).

## Screenshot refresh (USB)

See [`en-US/images/README.md`](en-US/images/README.md). Prefer:

```powershell
.\scripts\pns_device_screencap.ps1 -OutPath metadata\en-US\images\phoneScreenshots\01_preview.png
```

## Human review

Store copy in `en-US/*.txt` is **agent-drafted** (fleet-first). Milestone **H.5** / Sprint **T.10** **[HUMAN]** row: creative polish before fdroiddata MR.

## Constraints (unchanged)

- Application ID **`dev.pointandshoot`**
- **Apache-2.0** — matches [`LICENSE`](../LICENSE)
- No proprietary blobs / no Play Services (FOSS audit in `pns_verify_toolchain.ps1`)
