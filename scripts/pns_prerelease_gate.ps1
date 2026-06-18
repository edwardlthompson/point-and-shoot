# Point & Shoot — pre-release gate orchestrator (Milestone T.12).
#
# Host lane (default):
#   1. pns_verify_toolchain.ps1 -RunTests  (changelog, license, SBOM, template links, perf budget, detekt, lint, unit tests, kover)
#   2. pns_fixture_dng_gates.ps1
#   3. pns_fdroid_metadata_validate.ps1
#   4. pns_repro_build_verify.ps1
#   5. Security CI config (+ optional local gitleaks when on PATH)
#
# USB lane (-IncludeUsb, serial mutex — run capture then chrome sequentially):
#   6. pns_capture_pipeline_verify.ps1 -Fast
#   7. pns_chrome_ux_gate.ps1 -SkipGradle -SkipHost
#   8. pns_eye_af_pixel_gate.ps1 (optional visual; skip with -SkipEyeAfPixelGate when no face — CRI-032 human)
#
# Usage:
#   .\scripts\pns_prerelease_gate.ps1
#   .\scripts\pns_prerelease_gate.ps1 -SkipGradle          # host docs/fixture/fdroid/repro only (no Gradle)
#   .\scripts\pns_prerelease_gate.ps1 -IncludeUsb -Serial <adb-serial>
#   .\scripts\pns_prerelease_gate.ps1 -IncludeUsb -SkipEyeAfPixelGate

param(
    [switch]$IncludeUsb,
    [string]$Serial = "",
    [switch]$SkipGradle,
    [switch]$SkipEyeAfPixelGate
)

$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Push-Location $ProjectRoot
try {
    $failed = $false
    $started = Get-Date

    function Invoke-Step {
        param(
            [string]$Name,
            [scriptblock]$Block
        )
        $stepStart = Get-Date
        Write-Host ""
        Write-Host "== prerelease: $Name =="
        & $Block
        $elapsed = [int]((Get-Date) - $stepStart).TotalSeconds
        if ($LASTEXITCODE -ne 0) {
            Write-Host "FAIL: $Name (exit $LASTEXITCODE, ${elapsed}s)"
            $script:failed = $true
        } else {
            Write-Host "OK: $Name (${elapsed}s)"
        }
    }

    function Test-SecurityCiLane {
        $gitleaksToml = Join-Path $ProjectRoot ".gitleaks.toml"
        $codeqlYml = Join-Path $ProjectRoot ".github\workflows\codeql-analysis.yml"
        $securityYml = Join-Path $ProjectRoot ".github\workflows\security-scan.yml"
        $preCommit = Join-Path $ProjectRoot ".pre-commit-config.yaml"
        $issues = @()

        foreach ($path in @($gitleaksToml, $codeqlYml, $securityYml)) {
            if (-not (Test-Path -LiteralPath $path)) {
                $issues += "missing $path"
            }
        }

        if ($issues.Count -gt 0) {
            Write-Host "FAIL: security CI config incomplete: $($issues -join '; ')"
            return 1
        }

        Write-Host "OK: CodeQL + gitleaks workflows present"

        if (Test-Path -LiteralPath $preCommit) {
            Write-Host "OK: pre-commit config present (local gitleaks hook)"
        } else {
            Write-Host "WARN: .pre-commit-config.yaml missing — rely on GitHub security-scan.yml"
        }

        $gitleaksCmd = Get-Command gitleaks -ErrorAction SilentlyContinue
        if ($gitleaksCmd) {
            Write-Host "Running local gitleaks detect (no-git) ..."
            & gitleaks detect --source $ProjectRoot --config $gitleaksToml --no-git --redact
            if ($LASTEXITCODE -ne 0) {
                return $LASTEXITCODE
            }
            Write-Host "OK: local gitleaks detect"
        } else {
            Write-Host "WARN: gitleaks not on PATH — skipped local scan (CI: security-scan.yml)"
        }

        return 0
    }

    # --- 1. Toolchain (changelog, license, SBOM, template links, perf budget, optional Gradle tests) ---
    if ($SkipGradle) {
        Invoke-Step "verify_toolchain_host" {
            & (Join-Path $PSScriptRoot "pns_verify_toolchain.ps1") -ProjectRoot $ProjectRoot -SkipGradle
        }
    } else {
        Invoke-Step "verify_toolchain_runtests" {
            & (Join-Path $PSScriptRoot "pns_verify_toolchain.ps1") -ProjectRoot $ProjectRoot -RunTests
        }
    }

    # --- 2–4. Pre-release host gates not fully duplicated elsewhere ---
    Invoke-Step "fixture_dng_gates" {
        & (Join-Path $PSScriptRoot "pns_fixture_dng_gates.ps1")
    }

    Invoke-Step "fdroid_metadata_validate" {
        & (Join-Path $PSScriptRoot "pns_fdroid_metadata_validate.ps1") -ProjectRoot $ProjectRoot
    }

    Invoke-Step "repro_build_verify" {
        & (Join-Path $PSScriptRoot "pns_repro_build_verify.ps1") -ProjectRoot $ProjectRoot
    }

    # --- 5. Security CI (config + optional local gitleaks) ---
    Invoke-Step "security_ci_status" {
        $code = Test-SecurityCiLane
        if ($code -ne 0) {
            $global:LASTEXITCODE = $code
        } else {
            $global:LASTEXITCODE = 0
        }
    }

    # --- USB lane (never parallel capture + chrome on one serial) ---
    if ($IncludeUsb) {
        Invoke-Step "capture_pipeline_verify" {
            if ([string]::IsNullOrWhiteSpace($Serial)) {
                & (Join-Path $PSScriptRoot "pns_capture_pipeline_verify.ps1") -Fast
            } else {
                & (Join-Path $PSScriptRoot "pns_capture_pipeline_verify.ps1") -Fast -Serial $Serial
            }
        }

        Invoke-Step "chrome_ux_gate" {
            if ([string]::IsNullOrWhiteSpace($Serial)) {
                & (Join-Path $PSScriptRoot "pns_chrome_ux_gate.ps1") -SkipGradle -SkipHost
            } else {
                & (Join-Path $PSScriptRoot "pns_chrome_ux_gate.ps1") -SkipGradle -SkipHost -Serial $Serial
            }
        }

        Invoke-Step "eye_af_pixel_gate" {
            if ($SkipEyeAfPixelGate) {
                Write-Host "SKIP: eye_af_pixel_gate (-SkipEyeAfPixelGate; CRI-032 human face-in-frame)"
                $global:LASTEXITCODE = 0
            } elseif ([string]::IsNullOrWhiteSpace($Serial)) {
                & (Join-Path $PSScriptRoot "pns_eye_af_pixel_gate.ps1")
            } else {
                & (Join-Path $PSScriptRoot "pns_eye_af_pixel_gate.ps1") -Serial $Serial
            }
        }
    } else {
        Write-Host ""
        Write-Host "SKIP: USB gates (pass -IncludeUsb for capture/chrome/visual gates on one serial)"
    }

    $totalSec = [int]((Get-Date) - $started).TotalSeconds
    Write-Host ""
    if ($failed) {
        Write-Host "PRERELEASE GATE: FAIL (${totalSec}s total)"
        exit 1
    }
    Write-Host "PRERELEASE GATE: PASS (host lane$(if ($IncludeUsb) { ' + USB subset' } else { '' }), ${totalSec}s total)"
    exit 0
} finally {
    Pop-Location
}
