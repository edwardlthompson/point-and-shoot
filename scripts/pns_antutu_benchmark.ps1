# Full-auto on-device AnTuTu benchmark — one run per session, append sample to antutu_samples.json.
param(
    [string]$Serial = "",
    [int]$RunTimeoutSec = 1800,
    [switch]$DryRun,
    [switch]$NoAppend,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $PSScriptRoot "pns_antutu_common.ps1")

if ($Help) {
    Write-Host @"
pns_antutu_benchmark.ps1 - USB AnTuTu full benchmark (single run)

  -Serial         ADB serial (default: scripts/pns_adb_device.env)
  -RunTimeoutSec  Max wait for benchmark result (default 900)
  -DryRun         Prep + launch only
  -NoAppend       Do not write antutu_samples.json

Requires AnTuTu Benchmark installed on device. Do not run in parallel with camera capture gates.
"@
    exit 0
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$pnsPkg = "dev.pointandshoot"

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $Serial = Resolve-PnsAntutuSerial "" $PSScriptRoot
    if ($Serial) { Write-Host "[antutu_benchmark] serial -> $Serial" }
}

$Adb = New-PnsAntutuAdbInvoker $Serial
$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $repoRoot "hfr-runs\antutu_benchmark_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
Write-Host "[antutu_benchmark] artifacts -> $outDir"

Invoke-PnsAntutuAdb $Adb @("devices", "-l")
$identity = Get-PnsAntutuDeviceIdentity $Adb
Write-Host "[antutu_benchmark] device $($identity.model) slug=$($identity.deviceSlug)"

$antutuPkg = Find-PnsAntutuPackage $Adb
if (-not $antutuPkg) {
    throw "AnTuTu Benchmark not installed. Install from Play Store (com.antutu.ABenchMark or com.antutu.benchmark.full)."
}
Write-Host "[antutu_benchmark] package=$antutuPkg version=$(Get-PnsAntutuAppVersion $Adb $antutuPkg)"

$batteryStart = Get-PnsAntutuBatteryPct $Adb
Enable-PnsAntutuDeviceInteractive $Adb
Invoke-PnsAntutuAdb $Adb @("shell", "am", "force-stop", $pnsPkg) -IgnoreErrors | Out-Null
Invoke-PnsAntutuAdb $Adb @("shell", "am", "force-stop", $antutuPkg) -IgnoreErrors | Out-Null
Start-Sleep -Seconds 1

Start-PnsAntutuApp $Adb $antutuPkg
for ($i = 1; $i -le 8; $i++) {
    $pass = Invoke-PnsAntutuUiPass $Adb $outDir ("boot_{0:D2}" -f $i)
    if (Test-PnsAntutuHomeReady $pass.xml) { break }
    if (-not $pass.tapped -and -not (Test-PnsAntutuAiPluginDialog $pass.xml)) { break }
}

if ($DryRun) {
    Write-Host "[antutu_benchmark] DryRun complete"
    Disable-PnsAntutuDeviceInteractive $Adb
    exit 0
}

$startMode = Start-PnsAntutuBenchmarkFromHome $Adb $outDir
Write-Host "[antutu_benchmark] start mode=$startMode"

$result = Wait-PnsAntutuBenchmarkResult $Adb $outDir $RunTimeoutSec
$screencap = Join-Path $outDir "result.png"
try {
    $remoteCap = "/sdcard/pns_antutu_cap.png"
    Invoke-PnsAntutuAdb $Adb @("shell", "screencap", "-p", $remoteCap) -IgnoreErrors | Out-Null
    Invoke-PnsAntutuAdb $Adb @("pull", $remoteCap, $screencap) -IgnoreErrors | Out-Null
} catch {
    Write-Warning "[antutu_benchmark] screencap failed: $_"
}

$batteryEnd = Get-PnsAntutuBatteryPct $Adb

if (-not $result.ok -or -not $result.scores) {
    Write-Host "[antutu_benchmark] FAIL: could not parse AnTuTu score within ${RunTimeoutSec}s"
    Disable-PnsAntutuDeviceInteractive $Adb
    Invoke-PnsAntutuAdb $Adb @("shell", "am", "force-stop", $antutuPkg) -IgnoreErrors | Out-Null
    Invoke-PnsAntutuAdb $Adb @("shell", "am", "force-stop", $pnsPkg) -IgnoreErrors | Out-Null
    exit 1
}

$scores = $result.scores
Write-Host "[antutu_benchmark] total=$($scores.total) cpu=$($scores.cpu) gpu=$($scores.gpu) mem=$($scores.mem) ux=$($scores.ux)"

$marketingName = Get-PnsAntutuMarketingName $repoRoot $identity.model
$sample = [ordered]@{
    sampleId = [Guid]::NewGuid().ToString("N")
    model = $identity.model
    marketingName = $marketingName
    deviceSlug = $identity.deviceSlug
    source = "maintainer_usb"
    trustTier = "maintainer"
    submittedUtc = [DateTime]::UtcNow.ToString("o")
    buildDisplay = $identity.buildDisplay
    fingerprintSha256Prefix = $identity.fingerprintSha256Prefix
    antutuPackage = $antutuPkg
    antutuAppVersion = Get-PnsAntutuAppVersion $Adb $antutuPkg
    total = $scores.total
    cpu = $scores.cpu
    gpu = $scores.gpu
    mem = $scores.mem
    ux = $scores.ux
    batteryPctStart = $batteryStart
    batteryPctEnd = $batteryEnd
    artifactDir = "hfr-runs/antutu_benchmark_$utc"
}

($sample | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath (Join-Path $outDir "sample.json") -Encoding utf8

if (-not $NoAppend) {
    $samplesPath = Get-PnsAntutuSamplesPath $repoRoot
    $historyPath = Join-Path $repoRoot "docs\leaderboard\data\history\antutu_samples.jsonl"
    Add-PnsAntutuSample $samplesPath $historyPath $sample
    Write-Host "[antutu_benchmark] appended sample -> $samplesPath"
}

Disable-PnsAntutuDeviceInteractive $Adb
Invoke-PnsAntutuAdb $Adb @("shell", "am", "force-stop", $antutuPkg) -IgnoreErrors | Out-Null
Invoke-PnsAntutuAdb $Adb @("shell", "am", "force-stop", $pnsPkg) -IgnoreErrors | Out-Null
Write-Host "[antutu_benchmark] PASS total=$($scores.total)"
exit 0
