param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [switch]$SkipInstall,
    [switch]$SkipAssemble,
    [switch]$AllowNoAv1
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $OutDir) {
    $OutDir = Join-Path $repoRoot "hfr-runs\av1_parity_$(Get-Date -Format yyyyMMdd_HHmmss)"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$args = @{
    RunAv1Record = $true
    RecordSec = 6
    WaitSec = 70
    OutDir = $OutDir
}
if ($Serial) { $args.Serial = $Serial }
if ($SkipInstall) { $args.SkipInstall = $true }
if ($SkipAssemble) { $args.SkipAssemble = $true }
if ($AllowNoAv1) { $args.AllowAv1RecordSkip = $true }

& (Join-Path $PSScriptRoot "pns_video_format_test.ps1") @args
$exit = $LASTEXITCODE
$supportsAv1 = $null
$ffprobeAv1Ok = $null
$summaryPath = Join-Path $OutDir "summary.json"
if (Test-Path -LiteralPath $summaryPath) {
    try {
        $summary = Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json
        $supportsAv1 = [bool]$summary.supportsAv1
        if ($null -ne $summary.ffprobeAv1Ok) { $ffprobeAv1Ok = [bool]$summary.ffprobeAv1Ok }
    } catch { }
}
if (-not $AllowNoAv1 -and $supportsAv1 -eq $false) {
    $exit = 1
}

$result = [ordered]@{
    schema = "pns.av1_parity_verify.v1"
    pass = ($exit -eq 0)
    supportsAv1 = $supportsAv1
    ffprobeAv1Ok = $ffprobeAv1Ok
    delegatedScript = "pns_video_format_test.ps1"
    outDir = $OutDir
    timestampUtc = [DateTime]::UtcNow.ToString("o")
}
$result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $OutDir "gate.json") -Encoding utf8

if ($exit -ne 0) { exit $exit }
exit 0
