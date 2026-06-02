<#
.SYNOPSIS
  Monthly host-side ping for upstream Camera2/HAL source changes.
  No device required, no app telemetry, no personal data.

.DESCRIPTION
  Fetches selected upstream Android Camera2/HAL source files, hashes content,
  and reports what changed since baseline.
#>
param(
    [string]$OutDir = "",
    [string]$BaselinePath = "",
    [switch]$UpdateBaseline,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
pns_camera2_upstream_ping.ps1

Host-only monthly source ping for Camera2/HAL evolution.
Compares SHA-256 hashes against docs/CAMERA2_UPSTREAM_BASELINE.json.

Usage:
  .\scripts\pns_camera2_upstream_ping.ps1
  .\scripts\pns_camera2_upstream_ping.ps1 -UpdateBaseline
"@
    exit 0
}

$projRoot = Split-Path -Parent $PSScriptRoot
if (-not $OutDir) {
    $utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
    $OutDir = Join-Path $projRoot "hfr-runs\camera2_upstream_ping_$utc"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

if (-not $BaselinePath) {
    $BaselinePath = Join-Path $projRoot "docs\CAMERA2_UPSTREAM_BASELINE.json"
}

$sources = @(
    [ordered]@{
        id = "camera_metadata_definitions"
        url = "https://android.googlesource.com/platform/system/media/+/refs/heads/main/camera/docs/metadata_definitions.xml?format=TEXT"
    },
    [ordered]@{
        id = "camera_characteristics_java"
        url = "https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/hardware/camera2/CameraCharacteristics.java?format=TEXT"
    },
    [ordered]@{
        id = "capture_request_java"
        url = "https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/hardware/camera2/CaptureRequest.java?format=TEXT"
    },
    [ordered]@{
        id = "capture_result_java"
        url = "https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/hardware/camera2/CaptureResult.java?format=TEXT"
    }
)

function Get-Sha256([byte[]]$Bytes) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $hash = $sha.ComputeHash($Bytes)
        return ([BitConverter]::ToString($hash)).Replace("-", "").ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
}

function Read-Baseline([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        return [ordered]@{
            schema = "pns.camera2_upstream_baseline.v1"
            updatedAtUtc = $null
            entries = @()
        }
    }
    return Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
}

$baseline = Read-Baseline $BaselinePath
$baselineMap = @{}
foreach ($e in @($baseline.entries)) {
    $baselineMap["$($e.id)"] = "$($e.sha256)"
}

$results = @()
$changed = @()
$fetchErrors = @()

foreach ($src in $sources) {
    $id = "$($src.id)"
    $url = "$($src.url)"
    try {
        $resp = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 45
        $bytes = [System.Text.Encoding]::UTF8.GetBytes([string]$resp.Content)
        $sha = Get-Sha256 $bytes
        $prev = if ($baselineMap.ContainsKey($id)) { $baselineMap[$id] } else { $null }
        $isChanged = ($prev -ne $sha)
        $entry = [ordered]@{
            id = $id
            url = $url
            sha256 = $sha
            previousSha256 = $prev
            changed = $isChanged
            bytes = $bytes.Length
        }
        $results += $entry
        if ($isChanged) { $changed += $entry }
    } catch {
        $msg = $_.Exception.Message
        $fetchErrors += [ordered]@{ id = $id; url = $url; error = $msg }
        $results += [ordered]@{
            id = $id
            url = $url
            sha256 = $null
            previousSha256 = if ($baselineMap.ContainsKey($id)) { $baselineMap[$id] } else { $null }
            changed = $false
            error = $msg
        }
    }
}

$report = [ordered]@{
    schema = "pns.camera2_upstream_ping_report.v1"
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    sourceCount = $sources.Count
    changedCount = $changed.Count
    fetchErrorCount = $fetchErrors.Count
    entries = $results
    changedEntries = $changed
    fetchErrors = $fetchErrors
}

$jsonPath = Join-Path $OutDir "camera2_upstream_ping_report.json"
$report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $jsonPath -Encoding utf8

$md = @(
    "# Camera2 upstream monthly ping",
    "",
    "- **Sources checked:** $($sources.Count)",
    "- **Changed:** $($changed.Count)",
    "- **Fetch errors:** $($fetchErrors.Count)",
    ""
)
if ($changed.Count -gt 0) {
    $md += "## Changed sources"
    $md += @($changed | ForEach-Object { "- $($_.id): $($_.url)" })
    $md += ""
}
if ($fetchErrors.Count -gt 0) {
    $md += "## Fetch errors"
    $md += @($fetchErrors | ForEach-Object { "- $($_.id): $($_.error)" })
    $md += ""
}
$mdPath = Join-Path $OutDir "camera2_upstream_ping_report.md"
$md | Set-Content -LiteralPath $mdPath -Encoding utf8

if ($UpdateBaseline -or -not (Test-Path -LiteralPath $BaselinePath)) {
    $baseOut = [ordered]@{
        schema = "pns.camera2_upstream_baseline.v1"
        updatedAtUtc = [DateTime]::UtcNow.ToString("o")
        entries = @($results | Where-Object { $null -ne $_.sha256 } | ForEach-Object {
                [ordered]@{
                    id = $_.id
                    url = $_.url
                    sha256 = $_.sha256
                }
            })
    }
    $baseOut | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $BaselinePath -Encoding utf8
    Write-Host "[camera2_ping] baseline updated -> $BaselinePath"
}

Write-Host "[camera2_ping] changed=$($changed.Count) errors=$($fetchErrors.Count) report=$jsonPath"
exit 0
