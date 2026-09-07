$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Dist = Join-Path $Root "dist"
$ReleaseRoot = Join-Path $Root "build\release"
$Release = Join-Path $ReleaseRoot "Rune-Forge-v1.0.2"
$Zip = Join-Path $Root "build\Rune-Forge-v1.0.2-Windows.zip"

& "$Root\packaging\build.ps1"

if (-not (Test-Path "$Root\vendor\Alora-Launcher.jar")) {
    throw "vendor\Alora-Launcher.jar is required for a local portable release package."
}

Remove-Item -Recurse -Force $Release -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path `
    "$Release\vendor",
    "$Release\dist\scripts",
    "$Release\assets" |
    Out-Null

# Release launchers live at the ZIP root.
$Cmd = Get-Content "$Root\launcher\Start-RuneForge.cmd" -Raw
$Cmd = $Cmd.Replace('cd /d "%~dp0.."', 'cd /d "%~dp0"')
$Cmd = $Cmd.Replace('%CD%\launcher\Start-RuneForge.ps1', '%CD%\Start-RuneForge.ps1')
Set-Content -Path "$Release\Start-RuneForge.cmd" -Value $Cmd -Encoding ascii

$Ps1 = Get-Content "$Root\launcher\Start-RuneForge.ps1" -Raw
$Ps1 = $Ps1.Replace(
    '$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)',
    '$Root = Split-Path -Parent $MyInvocation.MyCommand.Path'
)
Set-Content -Path "$Release\Start-RuneForge.ps1" -Value $Ps1 -Encoding UTF8

Copy-Item "$Root\vendor\Alora-Launcher.jar" "$Release\vendor\"
Copy-Item "$Dist\RuneForge-Loader.jar" "$Release\dist\"
Copy-Item "$Dist\scripts\*.jar" "$Release\dist\scripts\"
Copy-Item "$Root\README.md","$Root\LICENSE","$Root\THIRD_PARTY_NOTICES.md" "$Release\"

if (Test-Path "$Root\assets") {
    Copy-Item "$Root\assets\*" "$Release\assets\" -ErrorAction SilentlyContinue
}

@"
RUNE FORGE v1.0.2 - WINDOWS RELEASE

1. Extract the entire ZIP to a normal folder.
2. Run Start-RuneForge.cmd.
3. First launch downloads a private Temurin Java 21 runtime if needed.
4. The Rune Forge window opens after the Alora/RuneLite client initializes.
5. Load and start scripts from the Rune Forge window.

DIAGNOSTICS

Startup:
    RuneForge-Dump.log

Loader:
    alora-data\rune-forge.log

If startup fails, include both logs when reporting the problem.

This release includes the Combat and Example scripts.
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

Write-Host ""
Write-Host "Release created:"
Write-Host "  $Zip"
