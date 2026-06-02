<#
.SYNOPSIS
  Compare two fleet_device_matrix.json files (Milestone 16.3).

.DESCRIPTION
  Emits Markdown per camera: HFR@1080, RAW pick, fleet role, face gate, sessionOk columns, encoder stub.

.EXAMPLE
  .\scripts\pns_fleet_matrix_diff.ps1 -PathA .\hfr-runs\fleet_matrix_a\fleet_device_matrix.json -PathB .\hfr-runs\fleet_matrix_b\fleet_device_matrix.json -OutMarkdown .\hfr-runs\fleet_matrix_diff.md
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$PathA,
    [Parameter(Mandatory = $true)][string]$PathB,
    [string]$OutMarkdown = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Read-Matrix([string]$path) {
    if (-not (Test-Path -LiteralPath $path)) { throw "File not found: $path" }
    return (Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json)
}

function Gate-Cell($gate) {
    if ($null -eq $gate) { return "?" }
    return "adv=$($gate.advertised) sess=$($gate.sessionOk) app=$($gate.appEnabled)"
}

function Cam-Row($cam) {
    $id = "$($cam.cameraId)"
    $hfr = if ($null -ne $cam.hfrMaxFpsAt1080) { "$($cam.hfrMaxFpsAt1080)" } else { "-" }
    $raw = if ($null -ne $cam.rawPickEffective) { "$($cam.rawPickEffective)" } else { "-" }
    $role = "-"
    if ($cam.PSObject.Properties.Name -contains 'fleetPolicy' -and $null -ne $cam.fleetPolicy) {
        if ($cam.fleetPolicy.PSObject.Properties.Name -contains 'role') {
            $role = "$($cam.fleetPolicy.role)"
        }
    }
    $fg = $null
    if ($cam.PSObject.Properties.Name -contains 'featureGates') { $fg = $cam.featureGates }
    $rawGate = Gate-Cell $(if ($null -ne $fg -and $fg.PSObject.Properties.Name -contains 'raw') { $fg.raw } else { $null })
    $hfrGate = Gate-Cell $(if ($null -ne $fg -and $fg.PSObject.Properties.Name -contains 'hfr') { $fg.hfr } else { $null })
    $faceGate = Gate-Cell $(if ($null -ne $fg -and $fg.PSObject.Properties.Name -contains 'face') { $fg.face } else { $null })
    return "| $id | $role | $hfr | $raw | $rawGate | $hfrGate | $faceGate |"
}

function Build-Table($label, $root) {
    $lines = @()
    $lines += "## $label"
    $lines += ""
    $dev = $root.device
    $meta = $root.scanMeta
    $lines += "- **Device:** $($dev.manufacturer) $($dev.model)"
    $lines += "- **Scan tier:** $($meta.scanTier) ms=$($meta.scanDurationMs)"
    $policy = $null
    if ($null -ne $root.product -and $null -ne $root.product.fleetProfiles) {
        $policy = $root.product.fleetProfiles.policyId
    }
    $lines += "- **Fleet policyId:** $(if ($policy) { $policy } else { '(generic)' })"
    $lines += ""
    $lines += "| cameraId | role | hfr1080 | rawPick | RAW gate | HFR gate | face gate |"
    $lines += "|----------|------|---------|---------|----------|----------|-----------|"
    foreach ($cam in @($root.cameras)) {
        $lines += (Cam-Row $cam)
    }
    $enc = $root.encoder
    if ($null -ne $enc -and "$enc" -ne "") {
        $lines += ""
        $lines += "### Encoder"
        $lines += "- source: $($enc.source) file=$($enc.sourceFile)"
        if ($enc.surfaceEncoding) {
            $mimeBits = @()
            $enc.surfaceEncoding.PSObject.Properties | ForEach-Object {
                $mimeBits += "$($_.Name)=$($_.Value)"
            }
            $lines += "- surfaceEncoding: $($mimeBits -join ', ')"
        }
        if ($enc.bestByCameraFps) {
            foreach ($row in @($enc.bestByCameraFps)) {
                $lines += "- cam $($row.cameraId) @$($row.targetFps)fps measured=$($row.measuredFps) ok=$($row.ok)"
            }
        }
    }
    $lines += ""
    return ($lines -join "`n")
}

$a = Read-Matrix $PathA
$b = Read-Matrix $PathB

$out = @()
$out += "# fleet_device_matrix diff"
$out += ""
$out += "- **A:** ``$PathA``"
$out += "- **B:** ``$PathB``"
$out += ""
$out += (Build-Table "A" $a)
$out += (Build-Table "B" $b)
$text = $out -join "`n"

if ($OutMarkdown -ne "") {
    $dir = Split-Path -Parent $OutMarkdown
    if ($dir -ne "" -and -not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Path $dir | Out-Null
    }
    Set-Content -LiteralPath $OutMarkdown -Value $text -Encoding UTF8
    Write-Host "Wrote $OutMarkdown"
}
else {
    Write-Output $text
}
