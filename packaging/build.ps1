$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

$Gradle = Get-Command gradle -ErrorAction SilentlyContinue
if ($Gradle) {
    & gradle -p $Root clean build
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed."
    }
    exit 0
}

$javac = Get-Command javac -ErrorAction SilentlyContinue
$jar = Get-Command jar -ErrorAction SilentlyContinue
if (-not $javac -or -not $jar) {
    throw "Gradle or a JDK with javac and jar is required."
}

$Build = Join-Path $Root "build\manual"
$Dist = Join-Path $Root "dist"
Remove-Item -Recurse -Force $Build,$Dist -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path `
    "$Build\api","$Build\loader","$Build\bootstrap",
    "$Build\example","$Build\combat","$Build\tests",
    "$Dist\scripts" | Out-Null

function Sources([string]$Path) {
    @(Get-ChildItem $Path -Recurse -Filter "*.java" | ForEach-Object FullName)
}

& javac -proc:none --release 11 -d "$Build\api" `
    (Sources "$Root\script-api\src\main\java")
if ($LASTEXITCODE -ne 0) { throw "API compilation failed." }

& javac -proc:none --release 11 -cp "$Build\api" -d "$Build\loader" `
    (Sources "$Root\loader\src\main\java")
if ($LASTEXITCODE -ne 0) { throw "Loader compilation failed." }

$BootstrapCp = "$Build\api;$Build\loader"
& javac -proc:none --release 8 -cp $BootstrapCp -d "$Build\bootstrap" `
    (Sources "$Root\bootstrap\src\main\java")
if ($LASTEXITCODE -ne 0) { throw "Bootstrap compilation failed." }

& javac -proc:none --release 11 -cp "$Build\api" -d "$Build\example" `
    (Sources "$Root\scripts\example\src\main\java")
if ($LASTEXITCODE -ne 0) { throw "Example compilation failed." }

& javac -proc:none --release 11 -cp "$Build\api" -d "$Build\combat" `
    (Sources "$Root\scripts\combat\src\main\java")
if ($LASTEXITCODE -ne 0) { throw "Combat compilation failed." }

& javac -proc:none --release 11 -cp "$Build\api" -d "$Build\tests" `
    (Sources "$Root\tests\src\main\java")
if ($LASTEXITCODE -ne 0) { throw "Test compilation failed." }

& java -cp "$Build\api;$Build\tests" io.runeforge.tests.RuneForgeSelfTest
if ($LASTEXITCODE -ne 0) { throw "Self-tests failed." }

Copy-Item -Recurse "$Build\api\*" "$Build\loader"
Copy-Item -Recurse "$Build\bootstrap\*" "$Build\loader"

$LoaderManifest = "$Build\loader-manifest.mf"
@"
Manifest-Version: 1.0
Premain-Class: io.runeforge.loader.RuneForgeBootstrap

"@ | Set-Content -Encoding ascii $LoaderManifest

& jar cfm "$Dist\RuneForge-Loader.jar" $LoaderManifest -C "$Build\loader" .
if ($LASTEXITCODE -ne 0) { throw "Loader packaging failed." }

$ExampleManifest = "$Build\example-manifest.mf"
@"
Manifest-Version: 1.0
Rune-Forge-Script-Class: io.runeforge.scripts.example.ExampleScript

"@ | Set-Content -Encoding ascii $ExampleManifest

& jar cfm "$Dist\scripts\RuneForge-Example.jar" $ExampleManifest -C "$Build\example" .
if ($LASTEXITCODE -ne 0) { throw "Example packaging failed." }

$CombatManifest = "$Build\combat-manifest.mf"
@"
Manifest-Version: 1.0
Rune-Forge-Script-Class: io.runeforge.scripts.combat.CombatScript

"@ | Set-Content -Encoding ascii $CombatManifest

& jar cfm "$Dist\scripts\RuneForge-Combat.jar" $CombatManifest -C "$Build\combat" .
if ($LASTEXITCODE -ne 0) { throw "Combat packaging failed." }

Write-Host "Rune Forge v1.0.5 build and self-tests passed."
