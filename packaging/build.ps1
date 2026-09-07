param(
    [string]$ClientJar = $env:ALORA_CLIENT_JAR
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Build = Join-Path $Root "build"
$Dist = Join-Path $Root "dist"

if (-not $ClientJar) {
    $candidate = Join-Path $env:USERPROFILE "alora\client_runelite.jar"
    if (Test-Path $candidate) {
        $ClientJar = $candidate
    }
}

if (-not $ClientJar -or -not (Test-Path $ClientJar)) {
    throw "Alora RuneLite client JAR not found. Set ALORA_CLIENT_JAR or pass -ClientJar."
}

$javac = Get-Command javac -ErrorAction SilentlyContinue
$jar = Get-Command jar -ErrorAction SilentlyContinue

if (-not $javac -or -not $jar) {
    throw "A JDK with javac and jar is required to build Rune Forge."
}

Remove-Item -Recurse -Force $Build,$Dist -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path `
    "$Build\api","$Build\bootstrap","$Build\loader","$Build\example","$Build\combat","$Dist\scripts" |
    Out-Null

$apiSources = Get-ChildItem "$Root\script-api\src\main\java" -Recurse -Filter "*.java" |
    ForEach-Object FullName

& javac -proc:none -source 11 -target 11 -cp $ClientJar -d "$Build\api" $apiSources
if ($LASTEXITCODE -ne 0) { throw "API compilation failed." }

$bootstrapSource = "$Root\loader\src\main\java\io\runeforge\loader\RuneForgeBootstrap.java"
& javac -proc:none -source 8 -target 8 -d "$Build\bootstrap" $bootstrapSource
if ($LASTEXITCODE -ne 0) { throw "Bootstrap compilation failed." }

$loaderSource = "$Root\loader\src\main\java\io\runeforge\loader\RuneForgeLoader.java"
$loaderCp = "$ClientJar;$Build\api"
& javac -proc:none -source 11 -target 11 -cp $loaderCp -d "$Build\loader" $loaderSource
if ($LASTEXITCODE -ne 0) { throw "Loader compilation failed." }

Copy-Item -Recurse "$Build\api\*" "$Build\loader"
Copy-Item -Recurse "$Build\bootstrap\*" "$Build\loader"

$loaderManifest = Join-Path $Build "loader-manifest.mf"
@"
Manifest-Version: 1.0
Premain-Class: io.runeforge.loader.RuneForgeBootstrap

"@ | Set-Content -Encoding ascii $loaderManifest

& jar cfm "$Dist\RuneForge-Loader.jar" $loaderManifest -C "$Build\loader" .
if ($LASTEXITCODE -ne 0) { throw "Loader packaging failed." }

function Build-Script(
    [string]$Name,
    [string]$SourceRoot,
    [string]$Manifest,
    [string]$OutputJar,
    [string]$ClassesDir
) {
    $sources = Get-ChildItem $SourceRoot -Recurse -Filter "*.java" | ForEach-Object FullName
    $cp = "$ClientJar;$Dist\RuneForge-Loader.jar"

    & javac -proc:none -source 11 -target 11 -cp $cp -d $ClassesDir $sources
    if ($LASTEXITCODE -ne 0) { throw "$Name compilation failed." }

    & jar cfm $OutputJar $Manifest -C $ClassesDir .
    if ($LASTEXITCODE -ne 0) { throw "$Name packaging failed." }
}

Build-Script `
    "Example script" `
    "$Root\scripts\example\src\main\java" `
    "$Root\scripts\example\src\main\resources\META-INF\MANIFEST.MF" `
    "$Dist\scripts\RuneForge-Example.jar" `
    "$Build\example"

Build-Script `
    "Combat script" `
    "$Root\scripts\combat\src\main\java" `
    "$Root\scripts\combat\src\main\resources\META-INF\MANIFEST.MF" `
    "$Dist\scripts\RuneForge-Combat.jar" `
    "$Build\combat"

Write-Host ""
Write-Host "Rune Forge v1.0.1 build complete:"
Write-Host "  $Dist\RuneForge-Loader.jar"
Write-Host "  $Dist\scripts\RuneForge-Example.jar"
Write-Host "  $Dist\scripts\RuneForge-Combat.jar"
