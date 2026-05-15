<#
.SYNOPSIS
  Compare two **deep_caps_*.json** files (Deep caps probe output) for fleet review.

.DESCRIPTION
  Reads JSON produced by **`DeepCapsProbeScreen`** / `runDeepCapsProbe` (`device` + `cameras[]`
  with `streamConfigurationMap.highSpeedVideo`, `pipelineAccess`, `lensInfo`, etc.).

  Emits a short **Markdown** table: per camera **max high-speed FPS**, **HDR DR** summary,
  **`maxNumOutputRaw`**, **`rawCapabilityAdvertised`**.

.PARAMETER PathA
  First JSON file (e.g. **hfr-runs/deep_caps_*.json**).

.PARAMETER PathB
  Second JSON file.

.PARAMETER OutMarkdown
  Optional path to write Markdown (UTF-8). When omitted, prints to stdout.

.EXAMPLE
  .\scripts\pns_deep_caps_diff.ps1 -PathA .\hfr-runs\deep_caps_a.json -PathB .\hfr-runs\deep_caps_b.json -OutMarkdown .\hfr-runs\fleet_deep_caps_diff.md
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$PathA,
  [Parameter(Mandatory = $true)][string]$PathB,
  [string]$OutMarkdown = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Read-DeepCapsJson([string]$path) {
  if (-not (Test-Path -LiteralPath $path)) { throw "File not found: $path" }
  $raw = Get-Content -LiteralPath $path -Raw -Encoding UTF8
  return ($raw | ConvertFrom-Json)
}

function Max-HighSpeedFps($cam) {
  $max = 0
  $hs = $cam.streamConfigurationMap.highSpeedVideo
  if ($null -eq $hs) { return 0 }
  foreach ($entry in $hs) {
    $ranges = $entry.fpsRanges
    if ($null -eq $ranges) { continue }
    foreach ($r in $ranges) {
      $u = [int]$r.upper
      if ($u -gt $max) { $max = $u }
    }
  }
  return $max
}

function Dr-Summary($cam) {
  $hdr = $cam.pipelineAccess.hdrDynamicRange
  if ($null -eq $hdr) { return "(no hdrDynamicRange)" }
  if ($hdr.supported -eq $false) { return "supported=false" }
  $n = 0
  if ($null -ne $hdr.profiles) { $n = @($hdr.profiles).Count }
  $rec = $hdr.recommendedTenBitProfile
  return "profiles=$n recTenBit=$rec"
}

function Raw-Summary($cam) {
  $rp = $cam.pipelineAccess.requestPipeline
  $maxRaw = "?"
  if ($null -ne $rp -and $null -ne $rp.maxNumOutputRaw) { $maxRaw = "$($rp.maxNumOutputRaw)" }
  $rawHint = $cam.pipelineAccess.rawVsHdrHints
  $adv = "?"
  if ($null -ne $rawHint -and $null -ne $rawHint.rawCapabilityAdvertised) { $adv = "$($rawHint.rawCapabilityAdvertised)" }
  return "maxNumOutputRaw=$maxRaw rawAdvertised=$adv"
}

function Device-Line($root) {
  $d = $root.device
  if ($null -eq $d) { return "(no device block)" }
  return "$($d.manufacturer) $($d.model) API=$($d.sdkInt) ($($d.release))"
}

function Build-MdTable($label, $root) {
  $lines = @()
  $lines += "## $label"
  $lines += ""
  $lines += "- **Device:** $(Device-Line $root)"
  $lines += "- **Generated:** $($root.generatedAt)"
  $lines += ""
  $lines += "| cameraId | maxHfrFps | DR | RAW / pipeline |"
  $lines += "|----------|-----------|----|------------------|"
  foreach ($cam in @($root.cameras)) {
    $id = "$($cam.cameraId)"
    $mh = Max-HighSpeedFps $cam
    $dr = Dr-Summary $cam
    $rw = Raw-Summary $cam
    $lines += "| $id | $mh | $dr | $rw |"
  }
  $lines += ""
  return ($lines -join "`n")
}

$a = Read-DeepCapsJson $PathA
$b = Read-DeepCapsJson $PathB

$out = @()
$out += "# deep_caps diff"
$out += ""
$out += "- **A:** ``$PathA``"
$out += "- **B:** ``$PathB``"
$out += ""
$out += (Build-MdTable "A" $a)
$out += (Build-MdTable "B" $b)
$text = $out -join "`n"

if ($OutMarkdown -ne "") {
  $dir = Split-Path -Parent $OutMarkdown
  if ($dir -ne "" -and -not (Test-Path -LiteralPath $dir)) {
    New-Item -ItemType Directory -Path $dir | Out-Null
  }
  Set-Content -LiteralPath $OutMarkdown -Value $text -Encoding UTF8
  Write-Host "Wrote $OutMarkdown"
} else {
  Write-Output $text
}
