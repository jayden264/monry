<#
 One-shot release: commit everything, push main, tag the next version, push the tag.
 GitHub Actions then builds and signs the APK and publishes it; the app offers it on next launch.

   .\scripts\release.ps1 "what changed"          # next patch version (v1.0.2 -> v1.0.3)
   .\scripts\release.ps1 "what changed" -Bump minor
   .\scripts\release.ps1 "what changed" -NoTag   # commit + push only, no release
#>
param(
    [Parameter(Mandatory = $true)][string]$Message,
    [ValidateSet("patch", "minor", "major")][string]$Bump = "patch",
    [switch]$NoTag
)
# git writes progress to stderr; Windows PowerShell 5.1 would treat that as an error under "Stop".
$ErrorActionPreference = "Continue"
function Invoke-Git {
    & git @args 2>&1 | ForEach-Object { "$_" } | Write-Host
    if ($LASTEXITCODE -ne 0) { throw "git $($args -join ' ') failed ($LASTEXITCODE)" }
}
Set-Location (Split-Path $PSScriptRoot -Parent)

Invoke-Git add -A
if (git status --porcelain) { Invoke-Git commit -m $Message } else { Write-Host "Nothing to commit." }
Invoke-Git push origin main

if ($NoTag) { return }

$last = (git tag --list "v*" --sort=-v:refname | Select-Object -First 1)
if (-not $last) { $last = "v1.0.0" }
$p = $last.TrimStart("v").Split(".") | ForEach-Object { [int]$_ }
switch ($Bump) {
    "major" { $p = @(($p[0] + 1), 0, 0) }
    "minor" { $p = @($p[0], ($p[1] + 1), 0) }
    default { $p = @($p[0], $p[1], ($p[2] + 1)) }
}
$tag = "v$($p -join '.')"
Invoke-Git tag $tag
Invoke-Git push origin $tag
Write-Host "Released $tag - build status: gh run list -L 1"
