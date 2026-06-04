<#
.SYNOPSIS
  Pull latest ReferenceCam DNGs from device and diff tags vs Point and Shoot baseline captures.

.EXAMPLE
  .\scripts\pns_referenceapp_dng_reference_pull.ps1 -Serial <serial>
  .\scripts\pns_referenceapp_dng_reference_pull.ps1 -DngCount 3 -PnsCompareDir hfr-runs\aux_dng_capture_analyze_20260518_020540
#>
param(
    [string]$Serial = "",
    [int]$DngCount = 3,
    [string]$PnsCompareDir = "",
    [string]$ReferenceAppPackage = "com.riseupgames.referenceapp2"
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) { . $resolve -PrependToPath -Quiet }

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
$outDir = Join-Path $projRoot "hfr-runs\referenceapp_reference_$ts"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
Write-Host "[referenceapp_ref] -> $outDir"

function AdbShell([string]$cmd) {
    if ($Serial) { return (& adb -s $Serial shell $cmd 2>&1 | Out-String) }
    return (& adb shell $cmd 2>&1 | Out-String)
}

# All DNGs under DCIM; exclude Point and Shoot folder
$findOut = AdbShell "find /sdcard/DCIM -name '*.dng' 2>/dev/null"
$paths = @($findOut -split "`n" | ForEach-Object { $_.Trim() } | Where-Object {
        $_ -match "\.dng$" -and $_ -notmatch "Point.and.Shoot" -and $_ -notmatch "Point & Shoot"
    })

if ($paths.Count -eq 0) {
    Write-Host "[referenceapp_ref] no non-P&S DNGs under DCIM; trying /sdcard"
    $findOut = AdbShell "find /sdcard -maxdepth 5 -name '*.dng' 2>/dev/null"
    $paths = @($findOut -split "`n" | ForEach-Object { $_.Trim() } | Where-Object {
            $_ -match "\.dng$" -and $_ -notmatch "Point.and.Shoot" -and $_ -notmatch "Point & Shoot"
        })
}

$withMtime = @()
foreach ($p in $paths) {
    if (-not $p) { continue }
    $statOut = (AdbShell "stat -c '%Y %s' '$p' 2>/dev/null").Trim()
    $parts = $statOut -split "\s+"
    if ($parts.Count -ge 2) {
        $withMtime += [pscustomobject]@{ Path = $p; Mtime = [int]$parts[0]; Size = [int]$parts[1] }
    }
}
$picked = $withMtime | Sort-Object Mtime | Select-Object -Last $DngCount
if ($picked.Count -lt $DngCount) {
    Write-Warning "[referenceapp_ref] only found $($picked.Count) candidate DNG(s); expected $DngCount"
}

$pulled = @()
$i = 0
foreach ($f in $picked) {
    $i++
    $local = Join-Path $outDir ("referenceapp_{0:D2}.dng" -f $i)
    Write-Host "[referenceapp_ref] pull $($f.Path)"
    if ($Serial) {
        & adb -s $Serial pull $f.Path $local
    } else {
        & adb pull $f.Path $local
    }
    if (Test-Path $local) {
        $pulled += [pscustomobject]@{
            index = $i; remote = $f.Path; local = $local; mtime = $f.Mtime; size = $f.Size
        }
    }
}

# Latest P&S compare dir (pass "__skip__" to omit)
if ([string]::IsNullOrWhiteSpace($PnsCompareDir)) {
    $pnsDirs = Get-ChildItem (Join-Path $projRoot "hfr-runs") -Directory -Filter "aux_dng_capture_analyze_*" |
        Sort-Object Name | Select-Object -Last 1
    if ($pnsDirs) { $PnsCompareDir = $pnsDirs.FullName }
} elseif ($PnsCompareDir -eq "__skip__") {
    $PnsCompareDir = ""
}

$report = [System.Collections.Generic.List[string]]::new()
$report.Add("# ReferenceCam vs Point and Shoot DNG tag diff")
$report.Add("")
$report.Add("Timestamp UTC: $ts")
$report.Add("ReferenceCam package: $ReferenceAppPackage")
$report.Add("P&S compare dir: $PnsCompareDir")
$report.Add("")

$pyReport = Join-Path $PSScriptRoot "dng_tag_report.py"
$pyStructural = Join-Path $PSScriptRoot "structural_verify.py"

$report.Add("## ReferenceCam files (newest $DngCount non-P&S DCIM DNGs)")
foreach ($p in $pulled) {
    $report.Add("- ``$($p.local)`` remote=$($p.remote) mtime=$($p.mtime)")
}
$report.Add("")
if ($pulled.Count -ge 1 -and (Test-Path $pyReport)) {
    $report.Add("### ReferenceCam tag summary")
    $report.Add("``````")
    $tagOut = & python $pyReport @($pulled.local)
    $tagOut | ForEach-Object { $report.Add($_) }
    $report.Add("``````")
}

if ($PnsCompareDir -and (Test-Path $PnsCompareDir)) {
    $report.Add("")
    $report.Add("## Point and Shoot (same device, focal slots M14/M23/M73)")
    $pnsFiles = @(
        (Join-Path $PnsCompareDir "M14_uw.dng"),
        (Join-Path $PnsCompareDir "M23_wide.dng"),
        (Join-Path $PnsCompareDir "M73_tele.dng")
    )
    foreach ($pf in $pnsFiles) {
        if (Test-Path $pf) { $report.Add("- ``$pf``") }
    }
    if ((Test-Path $pyReport) -and ($pnsFiles | Where-Object { Test-Path $_ }).Count -gt 0) {
        $report.Add("")
        $report.Add("### P&S tag summary")
        $report.Add("``````")
        $existing = $pnsFiles | Where-Object { Test-Path $_ }
        $tagOut2 = & python $pyReport @($existing)
        $tagOut2 | ForEach-Object { $report.Add($_) }
        $report.Add("``````")
    }
    $cam2 = Join-Path $PnsCompareDir "M23_wide.dng"
    $cam3 = Join-Path $PnsCompareDir "M14_uw.dng"
    $cam4 = Join-Path $PnsCompareDir "M73_tele.dng"
    if ((Test-Path $pyStructural) -and (Test-Path $cam2) -and (Test-Path $cam3) -and (Test-Path $cam4)) {
        $report.Add("")
        $report.Add("### P&S structural_verify (HAL id order cam2/3/4)")
        $report.Add("``````")
        $sv = & python $pyStructural $cam2 $cam3 $cam4 2>&1
        $sv | ForEach-Object { $report.Add($_) }
        $report.Add("``````")
    }
}

if ($pulled.Count -eq 3 -and (Test-Path $pyStructural)) {
    $report.Add("")
    $report.Add("### ReferenceCam structural_verify (assumed order: oldest..newest of pull = uw/wide/tele if you shot in that order)")
    $report.Add("Reorder files manually if needed.")
    $report.Add("``````")
    $sv2 = & python $pyStructural $pulled[0].local $pulled[1].local $pulled[2].local 2>&1
    $sv2 | ForEach-Object { $report.Add($_) }
    $report.Add("``````")
}

$reportPath = Join-Path $outDir "diff_report.md"
$report | Out-File -Encoding utf8 $reportPath
Write-Host "[referenceapp_ref] report -> $reportPath"

# Pull ReferenceCam APK for static analysis
$apkDir = Join-Path $outDir "apk"
New-Item -ItemType Directory -Force -Path $apkDir | Out-Null
$pathLines = (AdbShell "pm path $ReferenceAppPackage").Trim() -split "`n"
foreach ($line in $pathLines) {
    if ($line -match "^package:(.+)") {
        $remoteApk = $Matches[1].Trim()
        $base = Split-Path -Leaf $remoteApk
        $localApk = Join-Path $apkDir $base
        Write-Host "[referenceapp_ref] pull APK $remoteApk"
        if ($Serial) { & adb -s $Serial pull $remoteApk $localApk } else { & adb pull $remoteApk $localApk }
    }
}

$apkStrings = Join-Path $outDir "apk_strings_grep.txt"
$pyApkGrep = Join-Path $PSScriptRoot "apk_strings_grep.py"
if ((Test-Path $apkDir) -and (Test-Path $pyApkGrep)) {
    $baseApk = Get-ChildItem $apkDir -Filter "base.apk" | Select-Object -First 1
    if ($baseApk) {
        & python $pyApkGrep $baseApk.FullName 2>&1 | Set-Content $apkStrings -Encoding UTF8
        Write-Host "[referenceapp_ref] apk string hits -> $apkStrings"
    }
}

if ($Serial) { & adb -s $Serial shell am force-stop dev.pointandshoot 2>$null | Out-Null }
else { & adb shell am force-stop dev.pointandshoot 2>$null | Out-Null }

$manifest = @{
    timestampUtc = $ts
    referenceappPackage = $ReferenceAppPackage
    pulled = $pulled
    pnsCompareDir = $PnsCompareDir
    reportPath = $reportPath
}
$manifest | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $outDir "manifest.json") -Encoding UTF8
Write-Host "[referenceapp_ref] done"
