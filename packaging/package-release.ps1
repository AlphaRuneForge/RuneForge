$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Dist = Join-Path $Root "dist"
$Release = Join-Path $Root "build\release\Rune-Forge"

& "$Root\packaging\build.ps1"

if (-not (Test-Path "$Root\vendor\Alora-Launcher.jar")) {
    throw "vendor\Alora-Launcher.jar is required for a local portable release package."
}

Remove-Item -Recurse -Force $Release -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$Release\launcher","$Release\vendor","$Release\dist\scripts" |
    Out-Null

Copy-Item "$Root\launcher\*" "$Release\launcher\"
Copy-Item "$Root\vendor\Alora-Launcher.jar" "$Release\vendor\"
Copy-Item "$Dist\RuneForge-Loader.jar" "$Release\dist\"
Copy-Item "$Dist\scripts\*.jar" "$Release\dist\scripts\"
Copy-Item "$Root\README.md","$Root\LICENSE" "$Release\"

$zip = Join-Path $Root "build\Rune-Forge-v1.0.0-Windows.zip"
Remove-Item -Force $zip -ErrorAction SilentlyContinue
Compress-Archive -Path "$Release\*" -DestinationPath $zip

Write-Host "Created $zip"
