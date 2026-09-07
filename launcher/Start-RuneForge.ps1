$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Runtime = Join-Path $Root "runtime"
$JavaExe = Join-Path $Runtime "bin\java.exe"
$Home = Join-Path $Root "alora-data"
$ScriptsDir = Join-Path $Home "scripts"
$LoaderSource = Join-Path $Root "dist\RuneForge-Loader.jar"
$LoaderJar = Join-Path $Home "RuneForge-Loader.jar"
$AloraLauncher = Join-Path $Root "vendor\Alora-Launcher.jar"
$RuntimeZip = Join-Path $Root "temurin21.zip"
$RuntimeTemp = Join-Path $Root "runtime-temp"
$JavaUrl = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse"

function Log([string]$Message) {
    $stamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff"
    Write-Output "[$stamp] $Message"
}

function Fail([string]$Message, [int]$Code = 1) {
    Log "FATAL: $Message"
    exit $Code
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
    Log "Rune Forge v1.0.1 startup"
    New-Item -ItemType Directory -Force -Path $Home | Out-Null
    New-Item -ItemType Directory -Force -Path $ScriptsDir | Out-Null

    if (-not (Test-Path $LoaderSource)) {
        Fail "Missing $LoaderSource. Run packaging\build.ps1 first." 10
    }

    if (-not (Test-Path $AloraLauncher)) {
        Fail "Missing vendor\Alora-Launcher.jar. See vendor\README.md." 11
    }

    Copy-Item -Force $LoaderSource $LoaderJar
    Log "Loader installed: $LoaderJar"
    Log "Loader data directory: $Home"

    $BuiltScripts = Join-Path $Root "dist\scripts"
    if (-not (Test-Path $BuiltScripts)) {
        Fail "Missing script output directory: $BuiltScripts. Run packaging\build.ps1." 12
    }

    Get-ChildItem $BuiltScripts -Filter "*.jar" -File |
        ForEach-Object {
            Copy-Item -Force $_.FullName (Join-Path $ScriptsDir $_.Name)
            Log "Installed script: $($_.Name)"
        }

    if (-not (Test-Path $JavaExe)) {
        Log "Downloading Temurin Java 21 JRE."

        Add-Type -AssemblyName System.Net.Http
        $handler = New-Object System.Net.Http.HttpClientHandler
        $handler.AllowAutoRedirect = $true
        $client = New-Object System.Net.Http.HttpClient($handler)
        $client.Timeout = [TimeSpan]::FromMinutes(10)

        try {
            $response = $client.GetAsync($JavaUrl).GetAwaiter().GetResult()
            $response.EnsureSuccessStatusCode() | Out-Null
            [IO.File]::WriteAllBytes(
                $RuntimeZip,
                $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult())
        }
        finally {
            if ($client) { $client.Dispose() }
            if ($handler) { $handler.Dispose() }
        }

        if (Test-Path $RuntimeTemp) {
            Remove-Item -Recurse -Force $RuntimeTemp
        }

        New-Item -ItemType Directory -Force -Path $RuntimeTemp | Out-Null
        Expand-Archive -LiteralPath $RuntimeZip -DestinationPath $RuntimeTemp -Force

        $foundJava = Get-ChildItem $RuntimeTemp -Filter "java.exe" -File -Recurse |
            Where-Object { $_.FullName -match '\\bin\\java\.exe$' } |
            Select-Object -First 1

        if (-not $foundJava) {
            Fail "Could not locate java.exe after extraction." 20
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
        Fail "Java runtime test failed." 21
    }

    $env:JAVA_TOOL_OPTIONS = "-javaagent:`"$LoaderJar`" -Druneforge.home=`"$Home`""
    Log "JAVA_TOOL_OPTIONS configured."
    Log "Starting third-party launcher: $AloraLauncher"
    $launcherArgument = '-jar "' + $AloraLauncher + '"'
    $result = Run-NativeProcess $JavaExe $launcherArgument "ALORA"

    if ($result.ExitCode -ne 0) {
        Fail "Alora launcher returned $($result.ExitCode)." $result.ExitCode
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
