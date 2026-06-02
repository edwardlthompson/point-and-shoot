## Build + sideload without Android Studio (Windows)

These instructions assume you already have:
- **ADB** working (`adb devices` shows your legacy device)
- **Android SDK** installed (default path is `C:\Users\<you>\AppData\Local\Android\Sdk`)
- **Android Studio is NOT required** (we use its embedded JDK only if you don't have a system JDK)

### 0) One-time setup

#### A) Ensure `local.properties` points at your SDK
This repo does not commit `local.properties`. Create it at the repo root:

```
sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
```

#### B) JDK
If `java` is not on your PATH, you can use Android Studio's embedded JDK by setting:

```
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
```

PowerShell:

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

### 1) Build debug APK
From repo root:

PowerShell:

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
```

APK output:
- `app\build\outputs\apk\debug\app-debug.apk`

### 2) Sideload to device

```
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 3) Launch + monitor logs (recommended)

Launch:

```
adb shell am start -n dev.pointandshoot/.MainActivity
```

Find PID:

```
adb shell pidof dev.pointandshoot
```

Stream logs for just the app process:

```
adb logcat -v time --pid=<PID> *:V
```

### 3a) Automated §4 preview validation (optional)

Install a fresh **`app-debug.apk`**, grant **`CAMERA`**, run three scripted **`MainActivity`** launches (highlight dial **H**, **`pns_preview_raw_count`**, bracket **`3`**), and save logcat + grep summary under **`hfr-runs/`**:

```
.\scripts\pns_adb_preview_validate.ps1
.\scripts\pns_adb_preview_validate.ps1 -Serial <adb_serial> -SkipInstall -OutDir .\hfr-runs\my_adb_run

# Pull indexed stills (DCIM/Point & Shoot) to the host for desktop RAW / AVIF / JXL checks (see STORAGE_STRATEGY.md)
.\scripts\pns_pull_dcim_captures.ps1
.\scripts\pns_pull_dcim_captures.ps1 -Serial <adb_serial> -OutDir .\pulls\pns_dcim
```

Artifacts include **`logcat_*.txt`**, companion **`logcat_*_app_pid.txt`** (PID-filtered lines when the script used the tail fallback), and **`summary_grep.txt`**.

### 4) Toolchain gate (run after Kotlin or PowerShell changes)

```
.\scripts\pns_verify_toolchain.ps1                # full (assembleDebug + UTF-8 + dep audit)
.\scripts\pns_verify_toolchain.ps1 -SkipGradle    # docs / scripts only
```

Exit code 0 = pass. The same gate runs in CI on Ubuntu via
`.github/workflows/toolchain-verify.yml`.

### 5) Release / signed builds (optional)

Local debug-key release (for sanity-checking release-mode behavior; not for distribution):

```
.\scripts\pns_hfr_autorun.ps1 -AssembleReleaseOnly
# APK at: app\build\outputs\apk\release\app-release.apk (signed with the debug key)
```

Local **real** signed release:

1. Generate (or copy in) a keystore at the repo root, e.g. `release.keystore`.
2. Copy `keystore.properties.example` -> `keystore.properties` (gitignored) and fill in the
   `storeFile` / `storePassword` / `keyAlias` / `keyPassword` values.
3. Build:

   ```
   .\gradlew.bat :app:assembleRelease
   ```

   Gradle will log `[pns] release signing source = keystore.properties` to confirm the
   real signing key was used (instead of the debug-key fallback).

CI signed builds: `.github/workflows/build-signed.yml` (manual `workflow_dispatch` or
push to a `v*` tag). Requires the four secrets documented in that workflow file
(`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
`ANDROID_KEY_PASSWORD`). It runs the toolchain gate, builds, verifies the signature
with `apksigner verify --verbose`, and uploads the APK as a workflow artifact.

