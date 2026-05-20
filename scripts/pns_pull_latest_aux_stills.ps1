<#
.SYNOPSIS
  Pull the latest N DNG and JPEG files from DCIM/Point & Shoot for aux color comparison.

.DESCRIPTION
  Wide (M23) DNG is the color reference; M14 UW and M73 native tele are compared.
  Writes manifest.json under hfr-runs/pull_aux_stills_<utc>/.

.EXAMPLE
  .\scripts\pns_pull_latest_aux_stills.ps1 -Serial 8bf09993
  .\scripts\pns_pull_latest_aux_stills.ps1 -DngCount 3 -JpegCount 3
#>
param(
    [string]$Serial = "",
    [int]$DngCount = 3,
    [int]$JpegCount = 3
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) {
    . $resolve -PrependToPath -Quiet
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

function Invoke-Adb([string[]]$CmdArgs) {
    if ($Serial) { & adb -s $Serial @CmdArgs } else { & adb @CmdArgs }
    if ($LASTEXITCODE -ne 0) { throw "adb $($CmdArgs -join ' ') failed exit=$LASTEXITCODE" }
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if ($fromEnv) { $Serial = $fromEnv }
}

$stamp = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\pull_aux_stills_$stamp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
Write-Host "[pull_aux_stills] -> $outDir"

$remoteDcim = "/sdcard/DCIM/Point & Shoot"

function Get-LatestRemoteFiles([string]$Pattern, [int]$Count) {
    if ($Serial) {
        $listR = & adb -s $Serial shell "find '$remoteDcim' -name '$Pattern' 2>/dev/null"
    } else {
        $listR = & adb shell "find '$remoteDcim' -name '$Pattern' 2>/dev/null"
    }
    $paths = @($listR -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -match "\." })
    $withMtime = @()
    foreach ($p in $paths) {
        if (-not $p) { continue }
        if ($Serial) {
            $statR = & adb -s $Serial shell "stat -c %Y '$p' 2>/dev/null"
        } else {
            $statR = & adb shell "stat -c %Y '$p' 2>/dev/null"
        }
        $mtime = [int]([string]$statR).Trim()
        $withMtime += [pscustomobject]@{ Path = $p; Mtime = $mtime }
    }
    $withMtime | Sort-Object Mtime | Select-Object -Last $Count
}

$dngs = Get-LatestRemoteFiles "*.dng" $DngCount
$jpegs = Get-LatestRemoteFiles "*.jpg" $JpegCount
if ($jpegs.Count -lt $JpegCount) {
    $jpegs += Get-LatestRemoteFiles "*.jpeg" ($JpegCount - $jpegs.Count)
}

$pulled = @()
$i = 0
foreach ($f in $dngs) {
    $i++
    $local = Join-Path $outDir ("dng_{0:D2}.dng" -f $i)
    Write-Host "[pull] $($f.Path) -> $local"
    Invoke-Adb @("pull", $f.Path, $local)
    $pulled += [pscustomobject]@{ kind = "dng"; remote = $f.Path; local = $local; mtime = $f.Mtime }
}
$i = 0
foreach ($f in $jpegs) {
    $i++
    $local = Join-Path $outDir ("jpeg_{0:D2}.jpg" -f $i)
    Write-Host "[pull] $($f.Path) -> $local"
    Invoke-Adb @("pull", $f.Path, $local)
    $pulled += [pscustomobject]@{ kind = "jpeg"; remote = $f.Path; local = $local; mtime = $f.Mtime }
}

$manifest = @{
    timestampUtc = $stamp
    referenceNote = "Compare DNG color to wide (M23); native tele triage uses M73 not M150"
    files = $pulled
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $outDir "manifest.json") -Encoding UTF8

if ($dngs.Count -ge 3) {
    $dngPaths = 1..[Math]::Min(3, $dngs.Count) | ForEach-Object { Join-Path $outDir ("dng_{0:D2}.dng" -f $_) }
    if ($dngPaths.Count -eq 3) {
        $py = Join-Path $PSScriptRoot "structural_verify.py"
        if (Test-Path $py) {
            Write-Host "[pull_aux_stills] structural_verify (order: dng_01=oldest..dng_03=newest of last 3; label manually if needed)"
            python $py $dngPaths[0] $dngPaths[1] $dngPaths[2]
        }
    }
}

Invoke-Adb @("shell", "am", "force-stop", "dev.pointandshoot") | Out-Null
Write-Host "[pull_aux_stills] done; app force-stopped"
