param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Dist = Join-Path $Root "dist"
$ReleaseRoot = Join-Path $Root "build\release"
$Release = Join-Path $ReleaseRoot "Rune-Forge-v1.0.4"
$Zip = Join-Path $Root "build\Rune-Forge-v1.0.4-Windows.zip"

if (-not $SkipBuild) {
    & "$Root\packaging\build.ps1"
}

if (-not (Test-Path "$Dist\RuneForge-Loader.jar")) {
    throw "Build output is missing. Run packaging\build.ps1."
}

Remove-Item -Recurse -Force $Release -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path `
    "$Release\client",
    "$Release\dist\scripts",
    "$Release\assets" | Out-Null

$Cmd = Get-Content "$Root\launcher\Start-RuneForge.cmd" -Raw
$Cmd = $Cmd.Replace('cd /d "%~dp0.."', 'cd /d "%~dp0"')
$Cmd = $Cmd.Replace('%CD%\launcher\Start-RuneForge.ps1', '%CD%\Start-RuneForge.ps1')
Set-Content "$Release\Start-RuneForge.cmd" $Cmd -Encoding ascii

$Ps1 = Get-Content "$Root\launcher\Start-RuneForge.ps1" -Raw
$Ps1 = $Ps1.Replace(
    '$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)',
    '$Root = Split-Path -Parent $MyInvocation.MyCommand.Path'
)
Set-Content "$Release\Start-RuneForge.ps1" $Ps1 -Encoding UTF8

Copy-Item "$Dist\RuneForge-Loader.jar" "$Release\dist\"
Copy-Item "$Dist\scripts\*.jar" "$Release\dist\scripts\"
Copy-Item "$Root\README.md","$Root\LICENSE","$Root\THIRD_PARTY_NOTICES.md","$Root\CHANGELOG.md" "$Release\"

if (Test-Path "$Root\assets") {
    Copy-Item "$Root\assets\*" "$Release\assets\" -ErrorAction SilentlyContinue
}

@"
RUNE FORGE v1.0.4 - WINDOWS RELEASE

Rune Forge does NOT include Alora, RuneLite, Jagex, or other game-client binaries.

SETUP
1. Extract the ZIP.
2. Obtain a compatible client launcher separately.
3. Save it exactly as:
       client\Client-Launcher.jar
4. Run Start-RuneForge.cmd.

The first run downloads Temurin Java 21 directly from Adoptium and verifies
the downloaded archive against the SHA-256 checksum published in Adoptium's
metadata before extraction.

LOGS
- RuneForge-Dump.log
- runeforge-data\runeforge-bootstrap.log
- runeforge-data\rune-forge.log
"@ | Set-Content "$Release\README-FIRST.txt" -Encoding UTF8

$HashLines = Get-ChildItem $Release -File -Recurse |
    Sort-Object FullName |
    ForEach-Object {
        $Hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $Relative = $_.FullName.Substring($Release.Length + 1).Replace('\','/')
        "$Hash  $Relative"
    }
$HashLines | Set-Content "$Release\SHA256SUMS.txt" -Encoding ascii

Remove-Item -Force $Zip -ErrorAction SilentlyContinue
Compress-Archive -Path "$Release\*" -DestinationPath $Zip

& "$Root\packaging\audit-release.ps1" -ReleaseZip $Zip

Write-Host "Release created: $Zip"
