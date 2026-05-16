<#.SYNOPSIS
  Host-side smoke: launch preview with random-ish `am start` extras (Milestone 3.2 gate aid).
  Requires adb + debuggable app; does not assert logcat (run with logcat in parallel if needed).
#>
param(
  [int] $Iterations = 50,
  [string] $PackageActivity = "dev.pointandshoot/.MainActivity"
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\pns_resolve_adb.ps1" -PrependToPath -Quiet | Out-Null

1..$Iterations | ForEach-Object {
  $n = Get-Random -Minimum 0 -Maximum 61
  $cam = Get-Random -Minimum 0 -Maximum 9
  $lut = @("None", "PnsCinematic", "bogus_lut_name", "") | Get-Random
  adb shell am force-stop dev.pointandshoot | Out-Null
  adb shell am start -n $PackageActivity --es pns_screen preview `
    --ei pns_preview_self_timer_sec $n `
    --es pns_preview_camera_id "$cam" `
    --es pns_preview_stills_lut_name "$lut" `
    | Out-Null
  Start-Sleep -Milliseconds 80
}
Write-Host "pns_intent_fuzz: completed $Iterations launches."
