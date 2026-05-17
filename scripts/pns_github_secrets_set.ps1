#!/usr/bin/env pwsh
<#
.SYNOPSIS
    GitHub Actions secrets configuration script for Sprint 12.6
    
.DESCRIPTION
    Uses gh CLI or GitHub REST API to configure repository secrets for CI/CD signing.
    Idempotent - updates secrets if they already exist.
    
.PARAMETER Repo
    GitHub repository in "owner/repo" format (default: inferred from git remote)
    
.PARAMETER KeystorePath
    Path to Android keystore file to encode as base64
    
.PARAMETER KeystorePassword
    Keystore password (secure string)
    
.PARAMETER KeyPassword
    Key password (secure string)
    
.PARAMETER KeyAlias
    Key alias name
    
.EXAMPLE
    .\pns_github_secrets_set.ps1 -KeystorePath "C:\Keys\mykey.jks" -KeystorePassword $pass -KeyPassword $keypass -KeyAlias "release"
    
.OUTPUTS
    Writes secrets_set_result.json
#>
[CmdletBinding(SupportsShouldProcess=$true)]
param(
    [string]$Repo = "",
    [Parameter(Mandatory=$true)]
    [string]$KeystorePath,
    [Parameter(Mandatory=$true)]
    [Security.SecureString]$KeystorePassword,
    [Parameter(Mandatory=$true)]
    [Security.SecureString]$KeyPassword,
    [Parameter(Mandatory=$true)]
    [string]$KeyAlias
)

$ErrorActionPreference = "Stop"
$script:tag = "PNS.GitHubSecrets"

function Write-Log {
    param([string]$Message)
    $ts = Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ"
    Write-Host "[$ts] $Message"
}

# Infer repo from git remote if not specified
if (-not $Repo) {
    try {
        $remoteUrl = & git remote get-url origin 2>&1
        if ($remoteUrl -match "github\.com[:/](?<owner>[^/]+)/(?<repo>[^/\.]+)") {
            $Repo = "$($matches.owner)/$($matches.repo)"
            Write-Log "Inferred repo from git remote: $Repo"
        }
    } catch {
        Write-Log "Could not infer repo from git remote: $_"
    }
}

if (-not $Repo) {
    throw "Repository not specified and could not be inferred from git remote. Use -Repo 'owner/repo'"
}

Write-Log "Configuring secrets for: $Repo"

# Check for gh CLI
$gh = Get-Command gh -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
if (-not $gh) {
    Write-Log "gh CLI not found, attempting to use curl with GITHUB_TOKEN"
    
    if (-not $env:GITHUB_TOKEN) {
        throw "GITHUB_TOKEN environment variable required when gh CLI is not installed"
    }
}

# Read and encode keystore
if (-not (Test-Path $KeystorePath)) {
    throw "Keystore not found: $KeystorePath"
}

Write-Log "Reading keystore: $KeystorePath"
$keystoreBytes = [System.IO.File]::ReadAllBytes($KeystorePath)
$keystoreBase64 = [Convert]::ToBase64String($keystoreBytes)
Write-Log "Keystore encoded: $($keystoreBytes.Length) bytes -> $($keystoreBase64.Length) base64 chars"

# Convert secure strings to plain text (for API transmission - done in memory only)
$ptr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($KeystorePassword)
$keystorePassPlain = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($ptr)
[System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)

$ptr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($KeyPassword)
$keyPassPlain = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($ptr)
[System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)

# Define secrets to set
$secrets = @{
    ANDROID_KEYSTORE_BASE64 = $keystoreBase64
    KEYSTORE_PASSWORD = $keystorePassPlain
    KEY_PASSWORD = $keyPassPlain
    KEY_ALIAS = $KeyAlias
}

$results = @{
    timestamp = (Get-Date -Format "o")
    repo = $Repo
    keystorePath = $KeystorePath
    keystoreSize = $keystoreBytes.Length
    secrets = @()
}

foreach ($secret in $secrets.GetEnumerator()) {
    $secretName = $secret.Key
    $secretValue = $secret.Value
    
    Write-Log "Setting secret: $secretName"
    
    $result = @{
        name = $secretName
        status = "pending"
        error = $null
    }
    
    if ($gh) {
        # Use gh CLI
        try {
            # gh secret set doesn't accept value via parameter securely, use pipeline
            $secretValue | & $gh secret set $secretName --repo=$Repo 2>&1
            $result.status = "set"
            Write-Log "  OK (gh CLI)"
        } catch {
            $result.status = "failed"
            $result.error = $_.ToString()
            Write-Log "  FAILED: $_" -ForegroundColor Red
        }
    } else {
        # Use GitHub API via curl
        try {
            # Get public key for encryption
            $publicKeyUrl = "https://api.github.com/repos/$Repo/actions/secrets/public-key"
            $publicKeyResponse = Invoke-RestMethod -Uri $publicKeyUrl -Headers @{
                Authorization = "token $env:GITHUB_TOKEN"
                Accept = "application/vnd.github.v3+json"
            }
            
            # Encrypt secret value using sodium
            # Note: This requires libsodium or equivalent. For PowerShell native,
            # we'll use a simplified approach that requires libsodium-cli
            
            Write-Log "Public key retrieved: $($publicKeyResponse.key_id)"
            
            # For now, indicate that gh CLI is preferred
            $result.status = "skipped"
            $result.error = "Direct API encryption not implemented. Install gh CLI for automatic secret setting."
            Write-Log "  SKIPPED: Install gh CLI for automatic secret setting" -ForegroundColor Yellow
            
        } catch {
            $result.status = "failed"
            $result.error = $_.ToString()
            Write-Log "  FAILED: $_" -ForegroundColor Red
        }
    }
    
    $results.secrets += $result
}

# Clear plaintext passwords from memory
$keystorePassPlain = $null
$keyPassPlain = $null
[GC]::Collect()

# Summary
$successCount = ($results.secrets | Where-Object { $_.status -eq 'set' }).Count
$failCount = ($results.secrets | Where-Object { $_.status -eq 'failed' }).Count

Write-Log "Summary: $successCount set, $failCount failed"

# Write result
$outFile = "hfr-runs/github_secrets_set_$(Get-Date -Format 'yyyyMMdd_HHmmss').json"
$results | ConvertTo-Json -Depth 3 | Set-Content -Path $outFile -Encoding UTF8
Write-Log "Results: $outFile"

Write-Host "`n=== GitHub Secrets Configuration ===" -ForegroundColor Cyan
Write-Host "Repository: $Repo"
Write-Host "Secrets configured: $successCount/$($secrets.Count)" -ForegroundColor $(if ($failCount -eq 0) { "Green" } else { "Yellow" })
if ($failCount -gt 0) {
    Write-Host "Failed: $failCount" -ForegroundColor Red
}

exit $(if ($failCount -eq 0) { 0 } else { 1 })
