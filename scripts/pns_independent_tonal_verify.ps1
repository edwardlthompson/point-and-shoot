param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $OutDir) {
    $OutDir = Join-Path $repoRoot "hfr-runs\independent_tonal_verify_$(Get-Date -Format yyyyMMdd_HHmmss)"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$args = @{ OutDir = (Join-Path $OutDir "adb_preview_validate") }
if ($Serial) { $args.Serial = $Serial }
if ($SkipInstall) { $args.SkipInstall = $true }

& (Join-Path $PSScriptRoot "pns_adb_preview_validate.ps1") @args
$exit = $LASTEXITCODE

$independentOk = $false
$hooksPath = Join-Path $args.OutDir "m10_build_plan_host_hooks.json"
if (Test-Path -LiteralPath $hooksPath) {
    try {
        $hooks = Get-Content -LiteralPath $hooksPath -Raw | ConvertFrom-Json
        $independentOk = ($hooks.jpegOnlyIndependentOk -eq $true)
    } catch { }
}
if (-not $independentOk) {
    $log = Join-Path $args.OutDir "logcat_sprint10_jpeg_only_x1.txt"
    if (Test-Path -LiteralPath $log) {
        $text = Get-Content -LiteralPath $log -Raw
        $independentOk = $text -match "captureIndependentTonalStill 1/1 ok=true"
    }
}

$pass = ($exit -eq 0) -and $independentOk
$report = [ordered]@{
    schema = "pns.independent_tonal_verify.v1"
    pass = $pass
    delegatedScriptPass = ($exit -eq 0)
    independentTonalOk = $independentOk
    artifactDir = $args.OutDir
    outDir = $OutDir
    timestampUtc = [DateTime]::UtcNow.ToString("o")
}
$report | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $OutDir "gate.json") -Encoding utf8
if (-not $pass) { exit 1 }
exit 0
