param(
    [string]$Serial = "",
    [int]$TimeoutMin = 35,
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $PSScriptRoot "pns_antutu_common.ps1")

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $Serial = Resolve-PnsAntutuSerial "" $PSScriptRoot
}
$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($OutDir)) {
    $OutDir = Join-Path $repoRoot "hfr-runs\antutu_poll_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$Adb = New-PnsAntutuAdbInvoker $Serial
Enable-PnsAntutuDeviceInteractive $Adb
$deadline = (Get-Date).AddMinutes($TimeoutMin)
$n = 0
while ((Get-Date) -lt $deadline) {
    $n++
    $path = Join-Path $OutDir ("poll_{0:D3}.xml" -f $n)
    $xml = Get-PnsAntutuUiXml $Adb $path
    if ($xml -match 'mainTestPercent|mainTestRootView' -or (Test-PnsAntutuBenchmarkInProgress $xml)) {
        $pctM = [regex]::Match($xml, 'mainTestPercent[^>]*text="(\d+)"')
        $pct = if ($pctM.Success) { $pctM.Groups[1].Value } else { "?" }
        Write-Host "[poll $n] running pct=$pct"
    } elseif (Test-PnsAntutuAiPluginDialog $xml) {
        Invoke-PnsAntutuDismissAiPlugin $Adb $xml | Out-Null
        Write-Host "[poll $n] dismissed AI dialog"
    } else {
        $scores = Get-PnsAntutuScoresFromXml $xml
        if ($scores) {
            ($scores | ConvertTo-Json) | Set-Content (Join-Path $OutDir "scores.json") -Encoding utf8
            Write-Host "[poll $n] DONE total=$($scores.total) cpu=$($scores.cpu) gpu=$($scores.gpu) mem=$($scores.mem) ux=$($scores.ux)"
            Write-Host "artifacts -> $OutDir"
            exit 0
        }
        Write-Host "[poll $n] waiting (no score yet)"
    }
    Start-Sleep -Seconds 20
}
Write-Host "TIMEOUT after ${TimeoutMin}m"
exit 1
