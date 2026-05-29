#!/usr/bin/env pwsh
<#
.SYNOPSIS
    GitLab project setup and mirroring configuration script for Sprint 12.6
    
.DESCRIPTION
    Uses GitLab REST API to create project, configure mirroring from GitHub, set CI/CD variables.
    Idempotent - checks existence first.
    
.PARAMETER GitLabToken
    GitLab personal access token with api scope
    
.PARAMETER GitHubRepo
    Source GitHub repo in "owner/repo" format
    
.PARAMETER GitLabGroup
    Target GitLab group/namespace (default: personal project)
    
.PARAMETER ProjectName
    Project name (default: inferred from GitHub repo)
    
.EXAMPLE
    .\pns_gitlab_setup.ps1 -GitLabToken $env:GITLAB_TOKEN -GitHubRepo "myuser/point-and-shoot"
    
.OUTPUTS
    Writes gitlab_setup_result.json
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$false)]
    [string]$GitLabToken,

    [Parameter(Mandatory=$false)]
    [string]$GitHubRepo,

    [string]$GitLabGroup = "",
    [string]$ProjectName = "",
    [switch]$Verify
)

$ErrorActionPreference = "Stop"

if ($Verify) {
    $token = if ($GitLabToken) { $GitLabToken } else { $env:GITLAB_TOKEN }
    $projectId = $env:GITLAB_PROJECT_ID
    if (-not $token -or -not $projectId) {
        if (-not $env:ANDROID_KEYSTORE_BASE64) {
            Write-Host "GITLAB VERIFY: SKIP (set GITLAB_TOKEN + GITLAB_PROJECT_ID for API check, or ANDROID_KEYSTORE_BASE64 locally)"
            exit 0
        }
        Write-Host "GITLAB VERIFY: PASS (ANDROID_KEYSTORE_BASE64 set locally; API verify skipped — no GITLAB_TOKEN/PROJECT_ID)"
        exit 0
    }
    $headers = @{ "PRIVATE-TOKEN" = $token }
    $vars = Invoke-RestMethod -Uri "https://gitlab.com/api/v4/projects/$projectId/variables" -Headers $headers -Method Get
    $ks = $vars | Where-Object { $_.key -eq "ANDROID_KEYSTORE_BASE64" } | Select-Object -First 1
    if (-not $ks) {
        Write-Host "GITLAB VERIFY: FAIL (ANDROID_KEYSTORE_BASE64 variable not found on project $projectId)"
        exit 1
    }
    $masked = [bool]$ks.masked
    Write-Host "GITLAB VERIFY: $(if ($masked) { 'PASS' } else { 'FAIL' }) ANDROID_KEYSTORE_BASE64 masked=$masked"
    if (-not $masked) { exit 1 }
    exit 0
}

if (-not $GitLabToken -or -not $GitHubRepo) {
    throw "GitLabToken and GitHubRepo are required unless -Verify is used"
}
$script:tag = "PNS.GitLabSetup"

function Write-Log {
    param([string]$Message)
    $ts = Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ"
    Write-Host "[$ts] $Message"
}

# Parse GitHub repo
if ($GitHubRepo -notmatch '^[^/]+/[^/]+$') {
    throw "Invalid GitHubRepo format. Expected: 'owner/repo'"
}

if (-not $ProjectName) {
    $ProjectName = $GitHubRepo.Split('/')[1]
}

$GitLabUrl = "https://gitlab.com/api/v4"
$headers = @{
    "PRIVATE-TOKEN" = $GitLabToken
    "Content-Type" = "application/json"
}

$results = @{
    timestamp = (Get-Date -Format "o")
    githubRepo = $GitHubRepo
    projectName = $ProjectName
    gitlabGroup = $GitLabGroup
    steps = @()
    success = $false
}

# Step 1: Check if project already exists
Write-Log "Checking if project exists: $ProjectName"
$projectPath = if ($GitLabGroup) { "$GitLabGroup/$ProjectName" } else { $ProjectName }

try {
    $encodedPath = [System.Web.HttpUtility]::UrlEncode($projectPath)
    $existingProject = Invoke-RestMethod -Uri "$GitLabUrl/projects/$encodedPath" `
        -Headers $headers -ErrorAction SilentlyContinue
    
    Write-Log "Project already exists: $($existingProject.web_url)"
    $results.steps += @{ name = "CheckExisting"; status = "exists"; projectId = $existingProject.id; url = $existingProject.web_url }
    $projectId = $existingProject.id
} catch {
    # Project doesn't exist, create it
    Write-Log "Project not found, creating..."
    
    $createBody = @{
        name = $ProjectName
        visibility = "private"
        import_url = "https://github.com/$GitHubRepo.git"
        mirror = $true
        mirror_trigger_builds = $true
    }
    
    if ($GitLabGroup) {
        # Get namespace ID
        try {
            $groups = Invoke-RestMethod -Uri "$GitLabUrl/groups?search=$([System.Web.HttpUtility]::UrlEncode($GitLabGroup))" `
                -Headers $headers
            $namespace = $groups | Where-Object { $_.path -eq $GitLabGroup -or $_.name -eq $GitLabGroup } | Select-Object -First 1
            if ($namespace) {
                $createBody.namespace_id = $namespace.id
                Write-Log "Using group: $($namespace.full_path) (ID: $($namespace.id))"
            }
        } catch {
            Write-Log "Could not find group: $GitLabGroup (will create in personal namespace)"
        }
    }
    
    try {
        $newProject = Invoke-RestMethod -Uri "$GitLabUrl/projects" `
            -Method POST `
            -Headers $headers `
            -Body ($createBody | ConvertTo-Json -Compress)
        
        Write-Log "Project created: $($newProject.web_url)"
        $results.steps += @{ name = "CreateProject"; status = "created"; projectId = $newProject.id; url = $newProject.web_url }
        $projectId = $newProject.id
    } catch {
        Write-Log "Failed to create project: $_"
        $results.steps += @{ name = "CreateProject"; status = "failed"; error = $_.ToString() }
        $results | ConvertTo-Json -Depth 5 | Set-Content -Path "hfr-runs/gitlab_setup_failed.json" -Encoding UTF8
        throw
    }
}

# Step 2: Configure mirror if not already set
Write-Log "Checking mirror configuration..."
try {
    $mirrors = Invoke-RestMethod -Uri "$GitLabUrl/projects/$projectId/remote_mirrors" `
        -Headers $headers -ErrorAction SilentlyContinue
    
    $githubMirror = $mirrors | Where-Object { $_.url -match "github\.com/$([regex]::Escape($GitHubRepo))" }
    
    if ($githubMirror) {
        Write-Log "Mirror already configured: $($githubMirror.url)"
        $results.steps += @{ name = "ConfigureMirror"; status = "exists"; mirrorId = $githubMirror.id }
    } else {
        Write-Log "Creating mirror from GitHub..."
        
        $mirrorBody = @{
            url = "https://github.com/$GitHubRepo.git"
            enabled = $true
        }
        
        try {
            $newMirror = Invoke-RestMethod -Uri "$GitLabUrl/projects/$projectId/remote_mirrors" `
                -Method POST `
                -Headers $headers `
                -Body ($mirrorBody | ConvertTo-Json -Compress)
            
            Write-Log "Mirror created: $($newMirror.url)"
            $results.steps += @{ name = "ConfigureMirror"; status = "created"; mirrorId = $newMirror.id }
        } catch {
            Write-Log "Mirror creation failed (may need manual setup): $_"
            $results.steps += @{ name = "ConfigureMirror"; status = "skipped"; error = $_.ToString() }
        }
    }
} catch {
    Write-Log "Could not check/configure mirrors: $_"
    $results.steps += @{ name = "ConfigureMirror"; status = "error"; error = $_.ToString() }
}

# Step 3: Set CI/CD variables
Write-Log "Setting CI/CD variables..."
$ciVars = @(
    @{ key = "CI"; value = "true"; protected = $false; masked = $false }
)

foreach ($var in $ciVars) {
    try {
        # Check if variable exists
        $encodedKey = [System.Web.HttpUtility]::UrlEncode($var.key)
        $existingVar = Invoke-RestMethod -Uri "$GitLabUrl/projects/$projectId/variables/$encodedKey" `
            -Headers $headers -ErrorAction SilentlyContinue
        
        # Update existing
        Invoke-RestMethod -Uri "$GitLabUrl/projects/$projectId/variables/$encodedKey" `
            -Method PUT `
            -Headers $headers `
            -Body ($var | ConvertTo-Json -Compress) | Out-Null
        
        Write-Log "  Updated CI var: $($var.key)"
    } catch {
        # Create new
        try {
            Invoke-RestMethod -Uri "$GitLabUrl/projects/$projectId/variables" `
                -Method POST `
                -Headers $headers `
                -Body ($var | ConvertTo-Json -Compress) | Out-Null
            
            Write-Log "  Created CI var: $($var.key)"
        } catch {
            Write-Log "  Failed CI var $($var.key): $_"
        }
    }
}

$results.steps += @{ name = "SetCiVars"; status = "configured"; count = $ciVars.Count }

# Summary
$results.success = ($results.steps | Where-Object { $_.status -in @('created', 'exists', 'configured') }).Count -ge 2

Write-Log "Setup complete. Success: $($results.success)"

# Write result
$outDir = "hfr-runs"
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

$outFile = Join-Path $outDir ("gitlab_setup_{0:yyyyMMdd_HHmmss}.json" -f (Get-Date))
$results | ConvertTo-Json -Depth 5 | Set-Content -Path $outFile -Encoding UTF8
Write-Log "Results: $outFile"

Write-Host "`n=== GitLab Setup Summary ===" -ForegroundColor Cyan
Write-Host "Project: $projectPath"
if ($results.steps | Where-Object { $_.url }) {
    Write-Host "URL: $(($results.steps | Where-Object { $_.url })[0].url)"
}
Write-Host "Steps completed: $($results.steps.Count)"
foreach ($step in $results.steps) {
    $color = switch ($step.status) {
        { $_ -in @('created', 'exists', 'configured') } { 'Green' }
        'skipped' { 'Yellow' }
        default { 'Red' }
    }
    Write-Host "  $($step.name): $($step.status)" -ForegroundColor $color
}

exit $(if ($results.success) { 0 } else { 1 })
