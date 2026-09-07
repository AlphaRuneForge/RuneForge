$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Runtime = Join-Path $Root "runtime"
$JavaExe = Join-Path $Runtime "bin\java.exe"
$RuneForgeHome = Join-Path $Root "runeforge-data"
$ScriptsDir = Join-Path $RuneForgeHome "scripts"
$LoaderSource = Join-Path $Root "dist\RuneForge-Loader.jar"
$LoaderJar = Join-Path $RuneForgeHome "RuneForge-Loader.jar"
$ClientLauncher = Join-Path $Root "client\Client-Launcher.jar"
$RuntimeZip = Join-Path $Root "temurin21.zip"
$RuntimeTemp = Join-Path $Root "runtime-temp"
$JavaMetadataUrl = "https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jre&os=windows&vendor=eclipse"

function Log([string]$Message) {
    $stamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff"
    Write-Output "[$stamp] $Message"
}

function Fail([string]$Message, [int]$Code = 1) {
    Log "FATAL: $Message"
    exit $Code
}

function Download-Bytes([string]$Url) {
    Add-Type -AssemblyName System.Net.Http
    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.AllowAutoRedirect = $true
    $http = New-Object System.Net.Http.HttpClient($handler)
    $http.Timeout = [TimeSpan]::FromMinutes(10)

    try {
        $response = $http.GetAsync($Url).GetAwaiter().GetResult()
        $response.EnsureSuccessStatusCode() | Out-Null
        return $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
    }
    finally {
        if ($http) { $http.Dispose() }
        if ($handler) { $handler.Dispose() }
    }
}

function Run-NativeProcess([string]$FileName, [string]$Arguments, [string]$Label) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $FileName
    $psi.Arguments = $Arguments
    $psi.WorkingDirectory = $Root
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true

    if ($env:JAVA_TOOL_OPTIONS) {
        $psi.EnvironmentVariables["JAVA_TOOL_OPTIONS"] = $env:JAVA_TOOL_OPTIONS
    }

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $psi

    Log "$Label command: $FileName $Arguments" | Out-Host

    if (-not $process.Start()) {
        throw "Failed to start $Label"
    }

    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit()

    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()

    if ($stdout) {
        $stdout -split "`r?`n" | Where-Object { $_ } |
            ForEach-Object { Write-Host "[$Label STDOUT] $_" }
    }

    if ($stderr) {
        $stderr -split "`r?`n" | Where-Object { $_ } |
            ForEach-Object { Write-Host "[$Label STDERR] $_" }
    }

    [pscustomobject]@{
        ExitCode = [int]$process.ExitCode
        StdOut = $stdout
        StdErr = $stderr
    }
}

try {
    Log "Rune Forge v1.0.6 startup"
    New-Item -ItemType Directory -Force -Path $RuneForgeHome,$ScriptsDir | Out-Null

    if (-not (Test-Path $LoaderSource)) {
        Fail "Missing dist\RuneForge-Loader.jar." 10
    }

    if (-not (Test-Path $ClientLauncher)) {
        Fail "Missing client\Client-Launcher.jar. Obtain a compatible launcher separately and place it at that exact path." 11
    }

    Copy-Item -Force $LoaderSource $LoaderJar

    $BuiltScripts = Join-Path $Root "dist\scripts"
    if (-not (Test-Path $BuiltScripts)) {
        Fail "Missing dist\scripts." 12
    }

    # Keep RuneForge-managed scripts synchronized with dist\scripts.
    # Patch ZIP extraction does not delete old files, so version-stamped jars
    # from older revisions can remain in BOTH dist\scripts and the runtime
    # scripts directory. Never treat those stale jars as current scripts.
    $VersionedManagedPattern = '^RuneForge-(Combat|Example)-v\d+(?:\.\d+)+\.jar$'

    foreach ($Dir in @($BuiltScripts, $ScriptsDir)) {
        Get-ChildItem $Dir -Filter "RuneForge-*.jar" -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match $VersionedManagedPattern } |
            ForEach-Object {
                Remove-Item -Force $_.FullName
                Log "Removed stale versioned script: $($_.Name)"
            }
    }

    # Only canonical, unversioned jars in dist\scripts are deployable.
    $BuiltScriptFiles = @(Get-ChildItem $BuiltScripts -Filter "RuneForge-*.jar" -File |
        Where-Object { $_.Name -notmatch '-v\d+(?:\.\d+)+\.jar$' })

    foreach ($BuiltScript in $BuiltScriptFiles) {
        $Destination = Join-Path $ScriptsDir $BuiltScript.Name
        Copy-Item -Force $BuiltScript.FullName $Destination
        Log "Installed script: $($BuiltScript.Name)"
    }

    if (-not (Test-Path $JavaExe)) {
        Log "Resolving Temurin Java 21 JRE metadata."
        $metadataBytes = Download-Bytes $JavaMetadataUrl
        $metadataText = [Text.Encoding]::UTF8.GetString($metadataBytes)
        $assets = $metadataText | ConvertFrom-Json

        if (-not $assets -or -not $assets[0].binary.package.link -or -not $assets[0].binary.package.checksum) {
            Fail "Adoptium metadata did not include a package URL and SHA-256 checksum." 20
        }

        $JavaUrl = [string]$assets[0].binary.package.link
        $ExpectedSha256 = ([string]$assets[0].binary.package.checksum).ToLowerInvariant()
        Log "Downloading Temurin Java 21 JRE."
        [IO.File]::WriteAllBytes($RuntimeZip, (Download-Bytes $JavaUrl))

        $ActualSha256 = (Get-FileHash $RuntimeZip -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($ActualSha256 -ne $ExpectedSha256) {
            Remove-Item -Force $RuntimeZip -ErrorAction SilentlyContinue
            Fail "Temurin runtime checksum verification failed." 21
        }
        Log "Temurin SHA-256 verified: $ActualSha256"

        if (Test-Path $RuntimeTemp) {
            Remove-Item -Recurse -Force $RuntimeTemp
        }

        New-Item -ItemType Directory -Force -Path $RuntimeTemp | Out-Null
        Expand-Archive -LiteralPath $RuntimeZip -DestinationPath $RuntimeTemp -Force

        $foundJava = Get-ChildItem $RuntimeTemp -Filter "java.exe" -File -Recurse |
            Where-Object { $_.FullName -match '\\bin\\java\.exe$' } |
            Select-Object -First 1

        if (-not $foundJava) {
            Fail "Could not locate java.exe after extraction." 22
        }

        $runtimeRoot = Split-Path -Parent (Split-Path -Parent $foundJava.FullName)

        if (Test-Path $Runtime) {
            Remove-Item -Recurse -Force $Runtime
        }

        Move-Item $runtimeRoot $Runtime
        Remove-Item -Recurse -Force $RuntimeTemp
        Remove-Item -Force $RuntimeZip
    }

    $javaResult = Run-NativeProcess $JavaExe "-version" "JAVA"
    if ($javaResult.ExitCode -ne 0) {
        Fail "Java runtime test failed." 23
    }

    $env:JAVA_TOOL_OPTIONS = "-javaagent:`"$LoaderJar`" -Druneforge.home=`"$RuneForgeHome`""
    Log "Starting user-supplied client launcher: $ClientLauncher"

    $launcherArgument = '-jar "' + $ClientLauncher + '"'
    $result = Run-NativeProcess $JavaExe $launcherArgument "CLIENT"

    if ($result.ExitCode -ne 0) {
        Fail "Client launcher returned $($result.ExitCode)." $result.ExitCode
    }

    exit 0
}
catch {
    Log "UNHANDLED EXCEPTION"
    Log "Type: $($_.Exception.GetType().FullName)"
    Log "Message: $($_.Exception.Message)"
    Write-Output ($_ | Format-List * -Force | Out-String)
    exit 99
}
