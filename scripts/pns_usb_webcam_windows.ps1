#Requires -Version 5.1
<#
.SYNOPSIS
  Enable Lineage/AOSP USB Webcam so Windows Camera / Zoom / Teams see the phone.

.DESCRIPTION
  Camera, Zoom, and Teams only open a UVC device (inbox usbvideo.sys,
  "USB Video Device" / "Android Webcam"). They cannot open P and S HTTP MJPEG.

  This script flips the phone USB function the same way Settings does:
    svc usb setFunctions uvc
    svc usb setScreenUnlockedFunctions uvc
  If UsbService does not bind uvc.0, it taps Connected devices -> USB -> Webcam.

  Point and Shoot is force-stopped so DeviceAsWebcam can own the camera.

.PARAMETER Serial
  adb serial. Omit to use scripts/pns_adb_device.env.

.PARAMETER SkipEnable
  Only report whether Windows already has a live UVC camera.

.PARAMETER LaunchCamera
  Start the inbox Windows Camera app after UVC is OK.
#>
param(
    [string]$Serial = "",
    [switch]$SkipEnable,
    [switch]$LaunchCamera
)

$ErrorActionPreference = "Stop"

$resolveAdb = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
$adbSerial = Join-Path $PSScriptRoot "pns_adb_serial.ps1"
if (Test-Path -LiteralPath $resolveAdb) { . $resolveAdb -PrependToPath -Quiet }
if (Test-Path -LiteralPath $adbSerial) { . $adbSerial }

Set-StrictMode -Version Latest

$s = $Serial
if (-not $s -and (Get-Command Resolve-PnsAdbSerial -ErrorAction SilentlyContinue)) {
    $s = Resolve-PnsAdbSerial
}

function Get-PnsAdbArgs {
    if ($s) { @("-s", $s) } else { @() }
}

function Invoke-PnsAdb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$AdbArgs)
    & adb @(Get-PnsAdbArgs) @AdbArgs
}

function Wait-PnsAdb {
    param([int]$Seconds = 20)
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $out = & adb devices 2>$null
        if ($s) {
            if ($out -match "$([regex]::Escape($s))\s+device") { return $true }
        } elseif ($out -match "\sdevice(\s|$)") {
            return $true
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    return $false
}

function Get-PnsWindowsUvcCameras {
    Get-PnpDevice -ErrorAction SilentlyContinue | Where-Object {
        $_.Status -eq "OK" -and
        $_.Service -eq "usbvideo" -and
        (
            $_.FriendlyName -match "Android Webcam|USB Video Device" -or
            ($_.Class -eq "Camera" -and $_.FriendlyName -match "Webcam|UVC")
        )
    }
}

function Test-PnsPhoneUvc {
    $fn = ""
    $links = ""
    try { $fn = [string](Invoke-PnsAdb shell "svc usb getFunctions") } catch { }
    try { $links = [string](Invoke-PnsAdb shell "ls -l /config/usb_gadget/g1/configs/b.1/") } catch { }
    return (($fn -match "uvc") -or ($links -match "uvc\.0"))
}

function Get-PnsUiDumpXml {
    $remote = "/data/local/tmp/pns_uvc_uidump.xml"
    Invoke-PnsAdb shell "uiautomator dump $remote" | Out-Null
    $local = Join-Path $env:TEMP "pns_uvc_uidump.xml"
    Invoke-PnsAdb pull $remote $local | Out-Null
    if (-not (Test-Path -LiteralPath $local)) { return "" }
    return Get-Content -LiteralPath $local -Raw
}

function Get-PnsNodeTap {
    param([string]$Xml, [string]$Text)
    $node = [regex]::Match($Xml, "<node[^>]*text=`"$([regex]::Escape($Text))`"[^>]*>")
    if (-not $node.Success) { return $null }
    $b = [regex]::Match($node.Value, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if (-not $b.Success) { return $null }
    $x = [int](([int]$b.Groups[1].Value + [int]$b.Groups[3].Value) / 2)
    $y = [int](([int]$b.Groups[2].Value + [int]$b.Groups[4].Value) / 2)
    return @{ X = $x; Y = $y }
}

function Enable-PnsPhoneUvcViaSettings {
    Invoke-PnsAdb shell "cmd statusbar collapse" | Out-Null
    Invoke-PnsAdb shell "am start -n com.android.settings/.Settings`$ConnectedDeviceDashboardActivity --activity-clear-top" | Out-Null
    Start-Sleep -Seconds 2
    $xml = Get-PnsUiDumpXml
    $usbTap = Get-PnsNodeTap -Xml $xml -Text "USB"
    if (-not $usbTap) {
        foreach ($label in @("Charging this device", "File Transfer", "Webcam", "No data transfer")) {
            $usbTap = Get-PnsNodeTap -Xml $xml -Text $label
            if ($usbTap) { break }
        }
    }
    if (-not $usbTap) {
        Write-Host "Could not find the USB row in Connected devices."
        return $false
    }
    Invoke-PnsAdb shell "input tap $($usbTap.X) $($usbTap.Y)" | Out-Null
    Start-Sleep -Seconds 2
    $xml2 = Get-PnsUiDumpXml
    $webcam = Get-PnsNodeTap -Xml $xml2 -Text "Webcam"
    if (-not $webcam) {
        Write-Host "USB page opened but the Webcam radio was missing."
        return $false
    }
    Invoke-PnsAdb shell "input tap $($webcam.X) $($webcam.Y)" | Out-Null
    Start-Sleep -Seconds 4
    return (Test-PnsPhoneUvc)
}

Write-Host "USB Webcam for Camera / Zoom / Teams - inbox driver usbvideo.sys"
Write-Host ""

$already = @(Get-PnsWindowsUvcCameras)
if ($already.Count -gt 0 -and $SkipEnable) {
    Write-Host "UVC already live on Windows:"
    $already | Select-Object -First 8 FriendlyName, Status, InstanceId | Format-Table -AutoSize
    exit 0
}

if ($SkipEnable) {
    Write-Host "No live USB Video Device / Android Webcam. Re-run without -SkipEnable."
    exit 1
}

Write-Host "Stopping P and S so DeviceAsWebcam can own the camera..."
Invoke-PnsAdb shell "am force-stop dev.pointandshoot" | Out-Null

if (Test-PnsPhoneUvc) {
    Write-Host "Phone USB function is already uvc."
} else {
    Write-Host "Requesting svc usb setFunctions uvc (Lineage / AOSP UsbService)..."
    Invoke-PnsAdb shell "svc usb setFunctions uvc" | Out-Null
    if (-not (Wait-PnsAdb -Seconds 25)) {
        Write-Host "ADB dropped during the USB switch and did not return."
        exit 1
    }
    Invoke-PnsAdb shell "svc usb setScreenUnlockedFunctions uvc" | Out-Null
    Start-Sleep -Seconds 2
}

if (-not (Test-PnsPhoneUvc)) {
    Write-Host "UsbService did not bind UVC. Tapping Settings -> USB -> Webcam..."
    if (-not (Enable-PnsPhoneUvcViaSettings)) {
        Write-Host "Could not enable USB Webcam automatically."
        Write-Host "On the phone: Settings -> Connected devices -> USB -> Webcam."
        exit 1
    }
}

$deadline = (Get-Date).AddSeconds(20)
$win = @()
do {
    $win = @(Get-PnsWindowsUvcCameras)
    if ($win.Count -gt 0) { break }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)

if ($win.Count -eq 0) {
    Write-Host "Phone UVC is on, but Windows has not enumerated Android Webcam yet."
    Write-Host "Unplug/replug USB-C, then open Camera and pick Android Webcam."
    exit 1
}

Write-Host "Windows sees a class-compliant webcam (usbvideo.sys):"
$win | Select-Object -First 8 FriendlyName, Status, InstanceId | Format-Table -AutoSize
Write-Host "Pick that device in Camera, Zoom, or Teams."
if ($LaunchCamera) {
    Start-Process "microsoft.windows.camera:"
}
exit 0
