#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.8** — uiautomator dump; assert interactive nodes have content-desc (USB).
#>
param(
    [string]$Serial = "",
    [switch]$HostOnly
)

$ErrorActionPreference = "Stop"
if ($HostOnly) {
    Write-Host "A11Y DUMP GATE: SKIP (HostOnly — requires device + preview foreground)"
    exit 0
}
if (Test-Path (Join-Path $PSScriptRoot "pns_resolve_adb.ps1")) {
    . (Join-Path $PSScriptRoot "pns_resolve_adb.ps1") -PrependToPath -Quiet
}
$adb = @()
if ($Serial) { $adb = @("-s", $Serial) }
$pkg = "dev.pointandshoot"
$act = "dev.pointandshoot/.MainActivity"
& adb @adb shell am force-stop $pkg 2>$null | Out-Null
Start-Sleep -Milliseconds 600
& adb @adb shell am start -W -n $act --es pns_screen preview 2>&1 | Out-Null
Start-Sleep -Seconds 2
$xmlPath = "/sdcard/pns_a11y_dump.xml"
& adb @adb shell uiautomator dump $xmlPath 2>$null | Out-Null
$xml = & adb @adb exec-out cat $xmlPath 2>$null
if (-not $xml) {
    Write-Host "A11Y DUMP GATE: FAIL (no dump)"
    exit 1
}
$hasPns = $xml -match 'package="dev\.pointandshoot"'
if (-not $hasPns) {
    Write-Host "A11Y DUMP GATE: FAIL (preview not foreground; dump did not contain dev.pointandshoot)"
    & adb @adb shell am force-stop dev.pointandshoot 2>$null | Out-Null
    exit 1
}
$missing = [regex]::Matches($xml, 'clickable="true"') | ForEach-Object {
    $start = [Math]::Max(0, $_.Index - 600)
    $chunk = $xml.Substring($start, [Math]::Min(1200, $xml.Length - $start))
    # Compose emits several intermediate `android.view.View` wrappers that are clickable but not
    # individually focusable as accessibility targets. Gate only the actual focusable button nodes.
    $isFocusableBtn =
        ($chunk -match 'focusable="true"') -and
            ($chunk -match 'class="android\.widget\.(Button|ImageButton)"')
    $hasDesc = $chunk -match 'content-desc="[^"]{1,}"'
    if ($isFocusableBtn -and (-not $hasDesc)) { $chunk } else { $null }
} | Where-Object { $_ }
$count = ($missing | Measure-Object).Count
$ok = $count -eq 0
Write-Host "A11Y DUMP GATE: $(if ($ok) { 'PASS' } else { "FAIL missingDesc=$count" })"
if (-not $ok) {
    $sample = $missing | Select-Object -First 1
    if ($sample) {
        Write-Host "A11Y DUMP GATE: sampleMissingChunk="
        Write-Host $sample
    }
}
& adb @adb shell am force-stop dev.pointandshoot 2>$null | Out-Null
if (-not $ok) { exit 1 }
exit 0
