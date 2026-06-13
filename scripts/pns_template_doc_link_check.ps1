# Point & Shoot — validate relative markdown links in KNOWLEDGE_BASE.md (and optional DECISION_LOG.md).
# Milestone T Sprint T.1 gate. Wired into pns_verify_toolchain.ps1 when manifest files exist.
#
# Usage:
#   .\scripts\pns_template_doc_link_check.ps1
#   .\scripts\pns_template_doc_link_check.ps1 -ProjectRoot C:\path\to\repo

param(
  [string]$ProjectRoot = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
  $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
  $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

$targets = @(
  (Join-Path $ProjectRoot "KNOWLEDGE_BASE.md")
)

$decisionLog = Join-Path $ProjectRoot "DECISION_LOG.md"
if (Test-Path -LiteralPath $decisionLog) {
  $targets += $decisionLog
}

$linkPattern = '\[[^\]]*\]\(([^)]+)\)'
$failures = New-Object System.Collections.Generic.List[string]
$checked = 0

function Test-MarkdownLink {
  param(
    [string]$SourceFile,
    [string]$RawTarget
  )

  $target = $RawTarget.Trim()
  if ([string]::IsNullOrWhiteSpace($target)) {
    return
  }
  if ($target -match '^(https?|mailto):') {
    return
  }
  if ($target.StartsWith("#")) {
    return
  }

  $pathPart = ($target -split '#', 2)[0]
  if ([string]::IsNullOrWhiteSpace($pathPart)) {
    return
  }

  $pathPart = $pathPart -replace '\\', '/'
  if ($pathPart.StartsWith("/")) {
    $resolved = Join-Path $ProjectRoot ($pathPart.TrimStart('/'))
  } else {
    $sourceDir = Split-Path -Parent $SourceFile
    $resolved = Join-Path $sourceDir ($pathPart -replace '/', [IO.Path]::DirectorySeparatorChar)
  }

  try {
    $resolved = (Resolve-Path -LiteralPath $resolved -ErrorAction Stop).Path
  } catch {
    $failures.Add("FAIL: $($SourceFile.Replace($ProjectRoot, '.')) -> missing target '$RawTarget' (resolved '$pathPart')")
    return
  }

  if (-not (Test-Path -LiteralPath $resolved)) {
    $failures.Add("FAIL: $($SourceFile.Replace($ProjectRoot, '.')) -> missing target '$RawTarget'")
  }
}

foreach ($file in $targets) {
  if (-not (Test-Path -LiteralPath $file)) {
    if ($file -like "*KNOWLEDGE_BASE.md") {
      Write-Error "Missing required KNOWLEDGE_BASE.md at $file"
    }
    continue
  }

  $content = [System.IO.File]::ReadAllText($file)
  foreach ($match in [regex]::Matches($content, $linkPattern)) {
    $checked++
    Test-MarkdownLink -SourceFile $file -RawTarget $match.Groups[1].Value
  }
}

if ($failures.Count -gt 0) {
  foreach ($line in $failures) {
    Write-Host $line
  }
  Write-Host "TEMPLATE DOC LINK CHECK: FAIL ($($failures.Count) broken links, $checked checked)"
  exit 1
}

Write-Host "TEMPLATE DOC LINK CHECK: PASS ($checked relative links in $($targets.Count) file(s))"
exit 0
