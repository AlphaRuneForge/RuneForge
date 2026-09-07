param(
    [Parameter(Mandatory = $true)]
    [string]$ReleaseZip
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Temp = Join-Path $Root "build\release-audit"

Remove-Item -Recurse -Force $Temp -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Temp | Out-Null
Expand-Archive -LiteralPath $ReleaseZip -DestinationPath $Temp -Force

$AllowedJars = @(
    "dist\RuneForge-Loader.jar",
    "dist\scripts\RuneForge-Combat.jar",
    "dist\scripts\RuneForge-Example.jar"
)

$Archives = Get-ChildItem $Temp -Recurse -File |
    Where-Object { $_.Extension -in ".jar",".zip" }

foreach ($File in $Archives) {
    $Relative = $File.FullName.Substring($Temp.Length + 1)
    if ($File.Extension -eq ".zip" -or $AllowedJars -notcontains $Relative) {
        throw "Unexpected binary/archive in release: $Relative"
    }
}

$ForbiddenNames = @("alora","runelite","jagex","client_runelite","clientlibs")
foreach ($File in Get-ChildItem $Temp -Recurse -File) {
    $Relative = $File.FullName.Substring($Temp.Length + 1).ToLowerInvariant()
    foreach ($Forbidden in $ForbiddenNames) {
        if ($Relative.Contains($Forbidden)) {
            throw "Forbidden third-party name found in release path: $Relative"
        }
    }
}

Write-Host "Release audit passed."
