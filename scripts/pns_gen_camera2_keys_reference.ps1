<#
.SYNOPSIS
  Regenerate docs/CAMERA2_KEYS_AND_APIS_REFERENCE.md from the Android SDK android.jar (Camera2 keys + core APIs).

.DESCRIPTION
  Resolves **sdk.dir** from **local.properties** at repo root, then **platforms/android-&lt;api&gt;/android.jar**.
  **API level** defaults to **`compileSdk`** parsed from **`app/build.gradle.kts`** (override with **`-ApiLevel`**).
  Parses **javap** output for:

  - `CameraCharacteristics$Key` static fields (characteristics / static metadata)
  - `CaptureRequest$Key` static fields
  - `CaptureResult$Key` static fields
  Appends **Face / eye tracking reference** (filtered Camera2 keys + merged narrative from **docs/camera2_reference_face_eye_appendix.md**).

  Run from repo root:
    .\scripts\pns_gen_camera2_keys_reference.ps1
    .\scripts\pns_gen_camera2_keys_reference.ps1 -ApiLevel 37
#>
param(
    [int]$ApiLevel = 0
)

$ErrorActionPreference = "Stop"
$projRoot = Split-Path -Parent $PSScriptRoot

function Read-CompileSdkFromAppGradle([string]$root) {
    $gf = Join-Path $root "app\build.gradle.kts"
    if (-not (Test-Path -LiteralPath $gf)) {
        throw "Missing $gf (expected app module)."
    }
    foreach ($line in Get-Content -LiteralPath $gf) {
        $t = $line.Trim()
        if ($t -match '^compileSdk\s*=\s*(\d+)\s*(//.*)?$') {
            return [int]$Matches[1]
        }
    }
    throw "Could not find numeric compileSdk assignment in $gf; set compileSdk in app/build.gradle.kts or pass -ApiLevel."
}

if ($ApiLevel -lt 1) {
    $ApiLevel = Read-CompileSdkFromAppGradle $projRoot
    Write-Host "[gen_camera2_ref] ApiLevel from app/build.gradle.kts (compileSdk) -> $ApiLevel"
}

$localProps = Join-Path $projRoot "local.properties"
if (-not (Test-Path -LiteralPath $localProps)) {
    throw "Missing $localProps (sdk.dir required)."
}
$sdkDir = $null
foreach ($line in Get-Content -LiteralPath $localProps) {
    if ($line -match '^\s*sdk\.dir\s*=\s*(.+)\s*$') {
        $sdkDir = $Matches[1].Trim()
        break
    }
}
if ([string]::IsNullOrWhiteSpace($sdkDir)) {
    throw "sdk.dir not found in local.properties"
}
$jar = Join-Path $sdkDir "platforms\android-$ApiLevel\android.jar"
if (-not (Test-Path -LiteralPath $jar)) {
    throw "Missing platform JAR: $jar (install Android SDK platform $ApiLevel)."
}

$outMd = Join-Path $projRoot "docs\CAMERA2_KEYS_AND_APIS_REFERENCE.md"
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outMd) | Out-Null

function Get-JavapLines([string]$ClassName) {
    $o = & javap -classpath $jar -public $ClassName 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "javap failed for $ClassName"
    }
    return @($o)
}

function Get-JavapLinesCollapsed([string]$ClassName) {
    $raw = Get-JavapLines $ClassName
    $buf = ""
    $out = New-Object System.Collections.Generic.List[string]
    foreach ($line in $raw) {
        $t = $line.Trim()
        if ($t.Length -eq 0) { continue }
        if ($t.StartsWith('Compiled from')) { continue }
        if ($t.StartsWith('public final class')) { continue }
        if ($t.StartsWith('public static final') -and $t.Contains('$Key')) {
            $buf = $t
        }
        elseif ($buf.Length -gt 0) {
            $buf = "$buf $t"
        }
        if ($buf.Length -gt 0 -and $t.EndsWith(';')) {
            [void]$out.Add($buf)
            $buf = ""
        }
    }
    return $out
}

function Get-StaticKeyFieldNames([string]$ClassName) {
    # Match through nested generics (e.g. Key<Range<Integer>[]>); field name is after the last `>` before `;`.
    $rx = [regex]'\$Key<.*>\s+(\w+);'
    $names = New-Object System.Collections.Generic.List[string]
    foreach ($line in (Get-JavapLinesCollapsed $ClassName)) {
        $m = $rx.Match([string]$line)
        if ($m.Success) {
            [void]$names.Add($m.Groups[1].Value)
        }
    }
    return $names | Sort-Object -Unique
}

function Append-JavapMethods([System.Text.StringBuilder]$sb, [string]$title, [string]$ClassName) {
    [void]$sb.AppendLine("### $title")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine('```')
    foreach ($line in Get-JavapLines $ClassName) {
        $s = [string]$line
        if ($s -notmatch '^\s*public\s') { continue }
        if ($s -match '^\s*public\s+static\s+final') { continue }
        [void]$sb.AppendLine($s.TrimEnd())
    }
    [void]$sb.AppendLine('```')
    [void]$sb.AppendLine()
}

$utc = [DateTime]::UtcNow.ToString("yyyy-MM-dd HH:mm:ss' UTC'")

$sb = [System.Text.StringBuilder]::new()
[void]$sb.AppendLine('# Camera2 - keys and core APIs (reference)')
[void]$sb.AppendLine()
[void]$sb.AppendLine("**Generated:** $utc  ")
$sdkRel = 'platforms/android-' + $ApiLevel + '/android.jar (resolved from local.properties sdk.dir)'
[void]$sb.AppendLine('**Source:** ' + $sdkRel + ' - API **' + $ApiLevel + '** (matches app compileSdk in app/build.gradle.kts).  ')
[void]$sb.AppendLine('**Regenerate:** ``.\scripts\pns_gen_camera2_keys_reference.ps1`` from repo root.')
[void]$sb.AppendLine()
[void]$sb.AppendLine('---')
[void]$sb.AppendLine()
[void]$sb.AppendLine('## How to use this file')
[void]$sb.AppendLine()
[void]$sb.AppendLine('- **Java / Kotlin field names** below (`CONTROL_AE_MODE`, `SENSOR_INFO_TIMESTAMP_SOURCE`, ...) are the `public static final` **Key** identifiers on `CameraCharacteristics`, `CaptureRequest`, and `CaptureResult`. At runtime, each **Key** canonical string is `key.name` (e.g. `android.control.aeMode`) - use that when matching **vendor** keys or probe markdown.')
[void]$sb.AppendLine('- **Per-device truth** (OEM vendor tags, logical/physical map, stream sizes) is still only in **`CameraCharacteristics`** / **`availableCaptureRequestKeys`** on hardware - keep using **`pns_ae_highlight_probe_adb.ps1`** / exported **`PROBE_EXPORT_LATEST.md`** for fleet-specific names beyond this API-level list.')
[void]$sb.AppendLine('- **Face / eye / metering:** see **`## Face / eye tracking reference (Point & Shoot)`** at the end (filtered key subset + maintained appendix from ``docs/camera2_reference_face_eye_appendix.md``).')
[void]$sb.AppendLine('- **Official docs:** [android.hardware.camera2](https://developer.android.com/reference/android/hardware/camera2/package-summary)')
[void]$sb.AppendLine()
[void]$sb.AppendLine('---')
[void]$sb.AppendLine()

$chars = Get-StaticKeyFieldNames "android.hardware.camera2.CameraCharacteristics"
$req = Get-StaticKeyFieldNames "android.hardware.camera2.CaptureRequest"
$res = Get-StaticKeyFieldNames "android.hardware.camera2.CaptureResult"

[void]$sb.AppendLine('## Summary counts')
[void]$sb.AppendLine()
[void]$sb.AppendLine('| Class | Key field count |')
[void]$sb.AppendLine('|-------|---------------|')
[void]$sb.AppendLine('| `CameraCharacteristics` | ' + $chars.Count + ' |')
[void]$sb.AppendLine('| `CaptureRequest` | ' + $req.Count + ' |')
[void]$sb.AppendLine('| `CaptureResult` | ' + $res.Count + ' |')
[void]$sb.AppendLine()
[void]$sb.AppendLine('---')
[void]$sb.AppendLine()

function Append-KeyTable([System.Text.StringBuilder]$sb, [string]$title, [string[]]$names) {
    [void]$sb.AppendLine("## $title")
    [void]$sb.AppendLine()
    foreach ($n in $names) {
        [void]$sb.AppendLine("- ``$n``")
    }
    [void]$sb.AppendLine()
}

function Append-KeyTableSubsection([System.Text.StringBuilder]$sb, [string]$title, [string[]]$names) {
    [void]$sb.AppendLine("### $title")
    [void]$sb.AppendLine()
    if ($null -eq $names -or $names.Count -eq 0) {
        [void]$sb.AppendLine('_No keys matched this filter on this API level._')
    }
    else {
        foreach ($n in $names) {
            [void]$sb.AppendLine("- ``$n``")
        }
    }
    [void]$sb.AppendLine()
}

Append-KeyTable $sb "CameraCharacteristics.Keys (static metadata)" $chars
Append-KeyTable $sb 'CaptureRequest.Keys (requests & session parameters)' $req
Append-KeyTable $sb 'CaptureResult.Keys (capture results & partials)' $res

[void]$sb.AppendLine('---')
[void]$sb.AppendLine()
[void]$sb.AppendLine('## Core Camera2 & capture pipeline APIs (`javap` public surface)')
[void]$sb.AppendLine()
[void]$sb.AppendLine('Public methods only - for signatures and parameters see Android Studio or the linked reference docs.')
[void]$sb.AppendLine()

$apiClasses = @(
    @{ title = "CameraManager"; class = "android.hardware.camera2.CameraManager" },
    @{ title = "CameraDevice"; class = "android.hardware.camera2.CameraDevice" },
    @{ title = "CameraCaptureSession"; class = "android.hardware.camera2.CameraCaptureSession" },
    @{ title = "CameraCaptureSession.CaptureCallback"; class = "android.hardware.camera2.CameraCaptureSession`$CaptureCallback" },
    @{ title = "CameraDevice.StateCallback"; class = "android.hardware.camera2.CameraDevice`$StateCallback" },
    @{ title = "CameraManager.AvailabilityCallback"; class = "android.hardware.camera2.CameraManager`$AvailabilityCallback" },
    @{ title = "CaptureRequest.Builder"; class = "android.hardware.camera2.CaptureRequest`$Builder" },
    @{ title = "TotalCaptureResult"; class = "android.hardware.camera2.TotalCaptureResult" },
    @{ title = "params.SessionConfiguration"; class = "android.hardware.camera2.params.SessionConfiguration" },
    @{ title = "params.OutputConfiguration"; class = "android.hardware.camera2.params.OutputConfiguration" },
    @{ title = "params.StreamConfigurationMap"; class = "android.hardware.camera2.params.StreamConfigurationMap" },
    @{ title = "params.MeteringRectangle"; class = "android.hardware.camera2.params.MeteringRectangle" },
    @{ title = "params.Face"; class = "android.hardware.camera2.params.Face" },
    @{ title = "params.InputConfiguration"; class = "android.hardware.camera2.params.InputConfiguration" },
    @{ title = "CameraExtensionCharacteristics"; class = "android.hardware.camera2.CameraExtensionCharacteristics" }
)

foreach ($entry in $apiClasses) {
    try {
        Append-JavapMethods $sb $entry.title $entry.class
    }
    catch {
        [void]$sb.AppendLine("### $($entry.title)")
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("_(`javap` skipped: $($entry.class) not present on this API level or name mismatch.)_")
        [void]$sb.AppendLine()
    }
}

[void]$sb.AppendLine('## Related types (see Android reference)')
[void]$sb.AppendLine()
[void]$sb.AppendLine('- `android.hardware.camera2.CameraAccessException`')
[void]$sb.AppendLine('- `android.hardware.camera2.CameraCharacteristics` (methods: `get`, `getKeys`, `getAvailableCaptureRequestKeys`, ...)')
[void]$sb.AppendLine('- `android.hardware.camera2.CameraMetadata` (AE/AF/AWB mode **int** constants used with `Key` values)')
[void]$sb.AppendLine('- `android.hardware.camera2.DngCreator`')
[void]$sb.AppendLine('- `android.hardware.camera2.extensions.*` (extensions API)')
[void]$sb.AppendLine('- `android.hardware.camera2.params.Face` / `android.hardware.camera2.params.*` (face bounds, optional eye points, OIS, high-speed video, recommended stream configuration, ...)')
[void]$sb.AppendLine('- `android.media.ImageReader` / `android.media.Image` / `android.graphics.ImageFormat` (YUV/RAW preview and analysis)')
[void]$sb.AppendLine('- `android.view.Surface` / `android.graphics.SurfaceTexture` (outputs)')
[void]$sb.AppendLine()
[void]$sb.AppendLine('## Point & Shoot in-repo usage (quick index)')
[void]$sb.AppendLine()
[void]$sb.AppendLine('- Probe export & vendor key safety: `CameraCapabilitiesProbe.kt`, `VendorKeyGuard.kt`, `AeHighlightProbe.kt`')
[void]$sb.AppendLine('- Face / eye / vendor face tags: `VendorFaceEyeKeyNames.kt`, `FaceMeterProbeScreen.kt`, `scripts/pns_face_meter_probe.ps1`, `docs/face-eye-tracking-toolkit.md`')
[void]$sb.AppendLine('- Preview / metering: `PreviewEngineScreen.kt`, `HighlightMeter.kt`, `HighlightMeterSupport.kt`')
[void]$sb.AppendLine('- ADB automation: `scripts/pns_ae_highlight_probe_adb.ps1`, `AGENTS.md`')
[void]$sb.AppendLine()

# Face / eye / subject-helper key subset + maintained appendix (see docs/camera2_reference_face_eye_appendix.md).
$faceKeyRegex = '(FACE|AUTOFRAMING|CONTROL_(AE_REGIONS|AF_REGIONS|AWB_REGIONS|MAX_REGIONS_AE|MAX_REGIONS_AF|MAX_REGIONS_AWB|AF_TRIGGER|AE_PRECAPTURE_TRIGGER|AE_LOCK|AF_STATE|AE_STATE)|STATISTICS_(FACES|FACE_DETECT|INFO_AVAILABLE_FACE|INFO_MAX_FACE)|LENS_FOCUS_DISTANCE|LENS_FOCUS_RANGE|LENS_STATE|SYNC_MAX_LATENCY)'
$charsFace = @($chars | Where-Object { $_ -match $faceKeyRegex } | Sort-Object -Unique)
$reqFace = @($req | Where-Object { $_ -match $faceKeyRegex } | Sort-Object -Unique)
$resFace = @($res | Where-Object { $_ -match $faceKeyRegex } | Sort-Object -Unique)

[void]$sb.AppendLine('---')
[void]$sb.AppendLine()
[void]$sb.AppendLine('## Face / eye tracking reference (Point & Shoot)')
[void]$sb.AppendLine()
[void]$sb.AppendLine('**Filtered key lists** (this API level): names matching ``FACE``, ``AUTOFRAMING``, region / max-region controls, AF/AE triggers and states, ``STATISTICS_*FACE*``, ``LENS_FOCUS_*``, ``LENS_STATE``, ``SYNC_MAX_LATENCY``. For the complete key tables, see the sections above.')
[void]$sb.AppendLine()
Append-KeyTableSubsection $sb 'CameraCharacteristics (subset)' $charsFace
Append-KeyTableSubsection $sb 'CaptureRequest (subset)' $reqFace
Append-KeyTableSubsection $sb 'CaptureResult (subset)' $resFace

$appendixMd = Join-Path $projRoot 'docs\camera2_reference_face_eye_appendix.md'
if (Test-Path -LiteralPath $appendixMd) {
    [void]$sb.AppendLine((Get-Content -LiteralPath $appendixMd -Raw -Encoding utf8))
}
else {
    [void]$sb.AppendLine("_Maintained appendix not found: ``$appendixMd``_")
    [void]$sb.AppendLine()
}

[System.IO.File]::WriteAllText($outMd, $sb.ToString(), [System.Text.UTF8Encoding]::new($false))
Write-Host "Wrote $outMd ($((Get-Item $outMd).Length) bytes)"
