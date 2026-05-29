<#
.SYNOPSIS
  Sprint 15.14 — exiftool on pulled P&S DNGs (focal length + capture time; optional GPS).

.PARAMETER DngDir
  Folder with M14_uw.dng / M23_wide.dng / M73_tele.dng (e.g. aux_dng_capture_analyze_*).
  Default: newest hfr-runs/aux_dng_capture_analyze_*.

.PARAMETER RequireGps
  Hard-fail when GPS tags missing (needs geotag pref + location on device at capture).

.EXAMPLE
  .\scripts\pns_dng_exif_verify.ps1 -DngDir hfr-runs\aux_dng_capture_analyze_20260528_015423
#>
param(
    [string]$DngDir = "",
    [switch]$RequireGps
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($DngDir)) {
    $latest = Get-ChildItem (Join-Path $projRoot "hfr-runs") -Filter "aux_dng_capture_analyze_*" -Directory |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $latest) { throw "No aux_dng_capture_analyze_* under hfr-runs" }
    $DngDir = $latest.FullName
} elseif (-not [System.IO.Path]::IsPathRooted($DngDir)) {
    $DngDir = Join-Path $projRoot $DngDir
}

$wide = Join-Path $DngDir "M23_wide.dng"
if (-not (Test-Path -LiteralPath $wide)) {
    throw "Missing M23_wide.dng in $DngDir"
}

$exiftool = Get-Command exiftool -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
if (-not $exiftool) {
    Write-Host "SKIP: exiftool not on PATH (install ExifTool for 15.14 gate)" -ForegroundColor Yellow
    exit 2
}

$tags = @(
    "-FocalLength",
    "-FocalLengthIn35mmFormat",
    "-DateTimeOriginal",
    "-CreateDate",
    "-GPSLatitude",
    "-GPSLongitude"
)
$out = & $exiftool @tags -json $wide 2>&1 | Out-String
Write-Host $out
$meta = $out | ConvertFrom-Json | Select-Object -First 1

$focalOk = $null -ne $meta.FocalLength -and "$($meta.FocalLength)".Length -gt 0
$timeOk =
    ($null -ne $meta.DateTimeOriginal -and "$($meta.DateTimeOriginal)".Length -gt 0) -or
    ($null -ne $meta.CreateDate -and "$($meta.CreateDate)".Length -gt 0)
$gpsOk =
    $null -ne $meta.GPSLatitude -and $null -ne $meta.GPSLongitude

$report = @{
    schema = "dng_exif_verify.v1"
    dngDir = $DngDir
    widePath = $wide
    focalLength = $meta.FocalLength
    focalLength35mm = $meta.FocalLengthIn35mmFormat
    dateTimeOriginal = $meta.DateTimeOriginal
    gpsLatitude = $meta.GPSLatitude
    gpsLongitude = $meta.GPSLongitude
    checks = @{
        focal = $focalOk
        captureTime = $timeOk
        gps = $gpsOk
    }
}
$reportPath = Join-Path $DngDir "dng_exif_verify.json"
$report | ConvertTo-Json -Depth 4 | Set-Content $reportPath -Encoding UTF8

if (-not $focalOk -or -not $timeOk) {
    Write-Host "FAIL: wide DNG missing focal and/or capture time" -ForegroundColor Red
    Write-Host "Report: $reportPath"
    exit 1
}
if ($RequireGps -and -not $gpsOk) {
    Write-Host "FAIL: GPS tags missing (enable geotag + grant location before capture)" -ForegroundColor Red
    exit 1
}
if (-not $gpsOk) {
    Write-Host "WARN: GPS not present (optional unless -RequireGps)" -ForegroundColor Yellow
}

Write-Host "DNG EXIF VERIFY: PASS (focal + capture time on wide)" -ForegroundColor Green
Write-Host "Report: $reportPath"
exit 0
