<#
.SYNOPSIS
  Pull latest ProShot DNGs, capture matching P&S M14/M23/M73 RAW (non-Highlight dial), side-by-side report.

.DESCRIPTION
  Assumes you already shot UW / wide / tele in ProShot (newest 3 non-P&S DCIM DNGs).
  Uses preview dial Auto by default — not Highlight (H), which changes metering/YUV.

.EXAMPLE
  .\scripts\pns_dng_proshot_pns_session.ps1 -Serial 8bf09993
  .\scripts\pns_dng_proshot_pns_session.ps1 -SkipPnsCapture -ProShotDir hfr-runs\proshot_reference_*
  .\scripts\pns_dng_proshot_pns_session.ps1 -PnsStillModes standard,zsl,hdr -PullMotionCamReference
#>
param(
    [string]$Serial = "",
    [int]$WaitSec = 55,
    [string]$PreviewDial = "A",
    [switch]$SkipBuild,
    [switch]$SkipInstall,
    [switch]$SkipProShotPull,
    [switch]$SkipPnsCapture,
    [string]$ProShotDir = "",
    [string]$Notes = "Daylight 13.8d — compare Standard / ZSL / HDR vs ProShot; tag report is structural only.",
    [switch]$HostOnly,
    [string]$PnsStillModes = "",
    [switch]$PullMotionCamReference,
    [string]$MotionCamPackage = "com.motioncam.pro"
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot

if ($HostOnly) {
    Write-Host "=== Host-only DNG session prep (no device) ===" -ForegroundColor Cyan
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "pns_fixture_dng_gates.ps1")
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host "Host-only: run full session without -HostOnly when USB + ProShot captures are ready." -ForegroundColor Green
    exit 0
}

function Read-PnsAdbSerialFromEnvFile([string]$ScriptRoot) {
    $envFile = Join-Path $ScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) { return $null }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $t = $line.Trim()
        if ($t.StartsWith("#") -or $t.Length -eq 0) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        if ($t.Substring(0, $eq).Trim() -eq "PNS_ADB_SERIAL") {
            return $t.Substring($eq + 1).Trim()
        }
    }
    return $null
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if ($fromEnv) { $Serial = $fromEnv }
}

$ts = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$sessionDir = Join-Path $projRoot "hfr-runs\dng_proshot_pns_session_$ts"
New-Item -ItemType Directory -Force -Path $sessionDir | Out-Null

Write-Host ""
Write-Host "=== ProShot + P&S DNG session (dial=$PreviewDial, not H) ===" -ForegroundColor Cyan
Write-Host "Session folder: $sessionDir"
Write-Host "Note: $Notes"
Write-Host ""

if (-not $SkipProShotPull) {
    $pullArgs = @(
        "-File", (Join-Path $PSScriptRoot "pns_proshot_dng_reference_pull.ps1"),
        "-DngCount", "3",
        "-PnsCompareDir", "__skip__"
    )
    if ($Serial) { $pullArgs += @("-Serial", $Serial) }
    & powershell -NoProfile -ExecutionPolicy Bypass @pullArgs
    if ($LASTEXITCODE -ne 0) { throw "pns_proshot_dng_reference_pull failed" }
    $proshotLatest = Get-ChildItem (Join-Path $projRoot "hfr-runs") -Directory -Filter "proshot_reference_*" |
        Sort-Object Name | Select-Object -Last 1
    if (-not $proshotLatest) { throw "No proshot_reference_* folder after pull" }
    $ProShotDir = $proshotLatest.FullName
    Write-Host "[session] ProShot -> $ProShotDir"
} elseif ([string]::IsNullOrWhiteSpace($ProShotDir)) {
    $proshotLatest = Get-ChildItem (Join-Path $projRoot "hfr-runs") -Directory -Filter "proshot_reference_*" |
        Sort-Object Name | Select-Object -Last 1
    if ($proshotLatest) { $ProShotDir = $proshotLatest.FullName }
}

if ([string]::IsNullOrWhiteSpace($ProShotDir) -or -not (Test-Path $ProShotDir)) {
    throw "ProShotDir missing; run ProShot captures first or omit -SkipProShotPull"
}

function Invoke-PnsCaptureForMode([string]$StillMode, [string]$ParentDir) {
    $modeDir = Join-Path $ParentDir "pns_$StillMode"
    Write-Host "[session] P&S capture stillMode=$StillMode -> $modeDir" -ForegroundColor Cyan
    $cap = @{
        PreviewDial = $PreviewDial
        NoFast = $true
        WaitSec = $WaitSec
        OutDir = $modeDir
        ExtraAmArgs = @(
            "--es", "pns_preview_still_mode", $StillMode,
            "--ei", "pns_preview_raw_count", "1"
        )
    }
    if ($Serial) { $cap["Serial"] = $Serial }
    if ($SkipBuild) { $cap["SkipBuild"] = $true }
    if ($SkipInstall) { $cap["SkipInstall"] = $true }
    & (Join-Path $PSScriptRoot "pns_aux_dng_capture_analyze.ps1") @cap
    return @{ mode = $StillMode; dir = $modeDir; exitCode = $LASTEXITCODE }
}

function Try-PullMotionCamDngs([string]$DestDir, [int]$Count = 3) {
    New-Item -ItemType Directory -Force -Path $DestDir | Out-Null
    $resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
    if (Test-Path $resolve) { . $resolve -PrependToPath -Quiet }
    function AdbShell([string]$cmd) {
        if ($Serial) { return (& adb -s $Serial shell $cmd 2>&1 | Out-String) }
        return (& adb shell $cmd 2>&1 | Out-String)
    }
    $pkgLine = (AdbShell "pm path $MotionCamPackage 2>/dev/null").Trim()
    if (-not $pkgLine) {
        return @{ ok = $false; reason = "package_not_installed"; dir = $DestDir }
    }
    $findOut = AdbShell "find /sdcard/DCIM -name '*.dng' 2>/dev/null"
    $paths = @($findOut -split "`n" | ForEach-Object { $_.Trim() } | Where-Object {
            $_ -match "\.dng$" -and $_ -notmatch "Point.and.Shoot" -and $_ -notmatch "Point & Shoot" -and
                $_ -notmatch "proshot" -and $_ -notmatch "ProShot"
        })
    $withMtime = @()
    foreach ($p in $paths) {
        if (-not $p) { continue }
        $statOut = (AdbShell "stat -c '%Y %s' '$p' 2>/dev/null").Trim()
        $parts = $statOut -split "\s+"
        if ($parts.Count -ge 2) {
            $withMtime += [pscustomobject]@{ Path = $p; Mtime = [int]$parts[0] }
        }
    }
    $picked = $withMtime | Sort-Object Mtime | Select-Object -Last $Count
    if ($picked.Count -eq 0) {
        return @{ ok = $false; reason = "no_motioncam_dng_in_dcim"; dir = $DestDir }
    }
    $i = 0
    foreach ($item in $picked) {
        $i++
        $local = Join-Path $DestDir ("motioncam_{0:D2}.dng" -f $i)
        if ($Serial) {
            & adb -s $Serial pull $item.Path $local 2>&1 | Out-Null
        } else {
            & adb pull $item.Path $local 2>&1 | Out-Null
        }
    }
    return @{ ok = $true; reason = "pulled"; dir = $DestDir; count = $picked.Count }
}

$pnsDir = ""
$pnsModeCaptures = @()
if (-not $SkipPnsCapture) {
    $stillModes = @()
    if (-not [string]::IsNullOrWhiteSpace($PnsStillModes)) {
        $stillModes = @($PnsStillModes.Split(",") | ForEach-Object { $_.Trim().ToLower() } | Where-Object { $_ })
    }
    if ($stillModes.Count -gt 0) {
        $pnsRoot = Join-Path $sessionDir "pns_still_modes"
        New-Item -ItemType Directory -Force -Path $pnsRoot | Out-Null
        foreach ($sm in $stillModes) {
            $pnsModeCaptures += Invoke-PnsCaptureForMode $sm $pnsRoot
        }
        $pnsDir = $pnsRoot
        Write-Host "[session] P&S three-way -> $pnsDir"
    } else {
        $capArgs = @(
            "-File", (Join-Path $PSScriptRoot "pns_aux_dng_capture_analyze.ps1"),
            "-NoFast",
            "-WaitSec", "$WaitSec",
            "-PreviewDial", $PreviewDial
        )
        if ($Serial) { $capArgs += @("-Serial", $Serial) }
        if ($SkipBuild) { $capArgs += "-SkipBuild" }
        if ($SkipInstall) { $capArgs += "-SkipInstall" }
        & powershell -NoProfile -ExecutionPolicy Bypass @capArgs
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "[session] capture script exit=$LASTEXITCODE (missing DNG or error)"
        }
        $pnsLatest = Get-ChildItem (Join-Path $projRoot "hfr-runs") -Directory -Filter "aux_dng_capture_analyze_*" |
            Sort-Object Name | Select-Object -Last 1
        if (-not $pnsLatest) { throw "No aux_dng_capture_analyze_* after capture" }
        $pnsDir = $pnsLatest.FullName
        Write-Host "[session] P&S -> $pnsDir"
    }
} else {
    if (-not [string]::IsNullOrWhiteSpace($PnsStillModes)) {
        $pnsDir = Join-Path $sessionDir "pns_still_modes"
    } else {
        $pnsLatest = Get-ChildItem (Join-Path $projRoot "hfr-runs") -Directory -Filter "aux_dng_capture_analyze_*" |
            Sort-Object Name | Select-Object -Last 1
        if ($pnsLatest) { $pnsDir = $pnsLatest.FullName }
    }
}

$motionCamDir = ""
$motionCamPull = $null
if ($PullMotionCamReference) {
    $motionCamDir = Join-Path $sessionDir "motioncam_reference"
    $motionCamPull = Try-PullMotionCamDngs $motionCamDir 3
    Write-Host "[session] MotionCam pull: $($motionCamPull.reason) (count=$($motionCamPull.count))"
}

$comparePnsDir = $pnsDir
if ($pnsModeCaptures.Count -gt 0) {
    $stdCap = $pnsModeCaptures | Where-Object { $_.mode -eq "standard" } | Select-Object -First 1
    if ($stdCap) { $comparePnsDir = $stdCap.dir }
}
if ($comparePnsDir -and (Test-Path (Join-Path $comparePnsDir "M23_wide.dng"))) {
    $compareArgs = @(
        "-File", (Join-Path $PSScriptRoot "pns_dng_side_by_side_compare.ps1"),
        "-ProShotDir", $ProShotDir,
        "-PnsDir", $comparePnsDir,
        "-OutDir", (Join-Path $sessionDir "side_by_side")
    )
    & powershell -NoProfile -ExecutionPolicy Bypass @compareArgs
    if ($LASTEXITCODE -ne 0) { throw "pns_dng_side_by_side_compare failed" }
} else {
    Write-Warning "[session] skip side_by_side (no M23_wide.dng in compare dir)"
}

$diffReport = Join-Path $sessionDir "session_summary.md"
$pyTag = Join-Path $PSScriptRoot "dng_tag_report.py"
$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add("# ProShot vs P&S session $ts")
$lines.Add("")
$lines.Add("**Environment:** $Notes")
$lines.Add("**P&S preview dial:** $PreviewDial (not H / Highlight)")
$lines.Add("")
$lines.Add("| Artifact | Path |")
$lines.Add("|----------|------|")
$lines.Add("| ProShot pull | ``$ProShotDir`` |")
$lines.Add("| P&S captures | ``$pnsDir`` |")
$lines.Add("| Side-by-side | ``$(Join-Path $sessionDir 'side_by_side')`` |")
$lines.Add("")
if (Test-Path (Join-Path $sessionDir "side_by_side\side_by_side_report.md")) {
    $lines.Add("## Side-by-side report")
    $lines.Add("")
    Get-Content (Join-Path $sessionDir "side_by_side\side_by_side_report.md") | ForEach-Object { $lines.Add($_) }
}
if ((Test-Path $pyTag)) {
    $psFiles = @(
        (Join-Path $ProShotDir "proshot_01.dng"),
        (Join-Path $ProShotDir "proshot_02.dng"),
        (Join-Path $ProShotDir "proshot_03.dng")
    ) | Where-Object { Test-Path $_ }
    if ($psFiles.Count -gt 0) {
        $lines.Add("")
        $lines.Add("## ProShot tags (pull order)")
        $lines.Add("``````")
        & python $pyTag @($psFiles) | ForEach-Object { $lines.Add($_) }
        $lines.Add("``````")
    }
    if ($pnsModeCaptures.Count -gt 0) {
        foreach ($cap in $pnsModeCaptures) {
            $pnFiles = @(
                (Join-Path $cap.dir "M14_uw.dng"),
                (Join-Path $cap.dir "M23_wide.dng"),
                (Join-Path $cap.dir "M73_tele.dng")
            ) | Where-Object { Test-Path $_ }
            if ($pnFiles.Count -gt 0) {
                $lines.Add("")
                $lines.Add("## P&S tags stillMode=$($cap.mode) (M14/M23/M73)")
                $lines.Add("``````")
                & python $pyTag @($pnFiles) | ForEach-Object { $lines.Add($_) }
                $lines.Add("``````")
            }
        }
    } elseif ($pnsDir -and (Test-Path $pnsDir)) {
        $pnFiles = @(
            (Join-Path $pnsDir "M14_uw.dng"),
            (Join-Path $pnsDir "M23_wide.dng"),
            (Join-Path $pnsDir "M73_tele.dng")
        ) | Where-Object { Test-Path $_ }
        if ($pnFiles.Count -gt 0) {
            $lines.Add("")
            $lines.Add("## P&S tags (M14/M23/M73)")
            $lines.Add("``````")
            & python $pyTag @($pnFiles) | ForEach-Object { $lines.Add($_) }
            $lines.Add("``````")
        }
    }
    if ($motionCamPull -and $motionCamPull.ok -and $motionCamDir) {
        $mcFiles = Get-ChildItem $motionCamDir -Filter "*.dng" | Select-Object -ExpandProperty FullName
        if ($mcFiles.Count -gt 0) {
            $lines.Add("")
            $lines.Add("## MotionCam tags (newest DCIM pull)")
            $lines.Add("``````")
            & python $pyTag @($mcFiles) | ForEach-Object { $lines.Add($_) }
            $lines.Add("``````")
        }
    }
}
$lines | Out-File -Encoding utf8 $diffReport

$manifest = @{
    timestampUtc = $ts
    previewDial = $PreviewDial
    environmentNote = $Notes
    proShotDir = $ProShotDir
    pnsDir = $pnsDir
    pnsStillModes = $PnsStillModes
    pnsModeCaptures = $pnsModeCaptures
    motionCamDir = $motionCamDir
    motionCamPull = $motionCamPull
    sideBySideDir = Join-Path $sessionDir "side_by_side"
    summaryReport = $diffReport
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $sessionDir "session_manifest.json") -Encoding UTF8

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path $resolve) { . $resolve -PrependToPath -Quiet }
if ($Serial) { & adb -s $Serial shell am force-stop dev.pointandshoot 2>$null | Out-Null }
else { & adb shell am force-stop dev.pointandshoot 2>$null | Out-Null }

Write-Host ""
Write-Host "=== Session complete ===" -ForegroundColor Green
Write-Host "Summary: $diffReport"
Write-Host "Side-by-side DNGs: $(Join-Path $sessionDir 'side_by_side')"
