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
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

git add -A
if (git status --porcelain) { git commit -m $Message } else { Write-Host "Nothing to commit." }
git push origin main

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
git tag $tag
git push origin $tag
Write-Host "Released $tag - build status: gh run list -L 1"
