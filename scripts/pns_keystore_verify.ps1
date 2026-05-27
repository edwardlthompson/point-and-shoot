#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.B** — verify release keystore alias + SHA-256 vs scripts/pns_keystore_expected.json
#>
param(
    [string]$KeystorePath = "",
    [string]$StorePass = "",
    [string]$KeyPass = "",
    [string]$ExpectedJson = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if (-not $ExpectedJson) { $ExpectedJson = Join-Path $PSScriptRoot "pns_keystore_expected.json" }

if (-not $KeystorePath) {
    $KeystorePath = Join-Path $root "release.keystore"
}
if (-not (Test-Path $ExpectedJson)) {
    Write-Host "KEYSTORE VERIFY: SKIP (no $ExpectedJson — create from keytool -list -v)"
    exit 0
}
if (-not (Test-Path $KeystorePath)) {
    Write-Host "KEYSTORE VERIFY: SKIP (no keystore at $KeystorePath)"
    exit 0
}

$expected = Get-Content $ExpectedJson -Raw | ConvertFrom-Json
$alias = $expected.alias
if (-not $alias) { throw "pns_keystore_expected.json missing alias" }

$keytool = Get-Command keytool -ErrorAction SilentlyContinue
if (-not $keytool) { throw "keytool not on PATH" }

$passArg = if ($StorePass) { $StorePass } else { "android" }
& keytool -list -v -keystore $KeystorePath -alias $alias -storepass $passArg 2>&1 | Out-String | Set-Variable -Name listing

$sha = $null
if ($listing -match 'SHA256:\s*([0-9A-F:]+)') { $sha = $Matches[1] }
if (-not $sha) { throw "Could not parse SHA256 from keytool output" }

$ok = ($sha -eq $expected.sha256)
Write-Host "KEYSTORE VERIFY: $(if ($ok) { 'PASS' } else { 'FAIL' }) alias=$alias sha=$sha"
if (-not $ok) { exit 1 }
exit 0
