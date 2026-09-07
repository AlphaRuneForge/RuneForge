# Rune Forge

**Current release: v1.0.5**

Rune Forge is a modular Java script loader with a portable Windows launcher.
The loader and bundled scripts are separated so scripts can be updated without
changing the loader.

![Rune Forge banner](assets/banner.png)

## What changed in 1.0.4

- One documented client-launcher location: `client/Client-Launcher.jar`
- Real Gradle compilation and automated self-tests
- GitHub Actions build/release audit
- No compile-time dependency on a private client JAR
- Runtime client compatibility is isolated behind a reflection bridge
- Combat actions use the client's normal menu-action method instead of
  coordinate-based synthetic mouse clicks
- Runtime-classloader detection uses the Java agent's loaded-class view
- Visible bootstrap timeout/failure logging
- Script trust confirmation using SHA-256 fingerprints
- Safer script lifecycle and Swing/client-thread boundaries
- Temurin downloads are SHA-256 verified before extraction

## Windows release setup

Rune Forge does not redistribute a game client or game-client launcher.

1. Extract `Rune-Forge-v1.0.5-Windows.zip`.
2. Obtain a compatible client launcher separately.
3. Save it exactly as:

```text
client/Client-Launcher.jar
```

4. Run:

```text
Start-RuneForge.cmd
```

The launcher downloads a private Temurin Java 21 JRE when required. The
downloaded archive is verified against the SHA-256 checksum returned by the
Adoptium API before Rune Forge extracts or executes it.

## Building from source

Requirements:

- JDK 11+ (JDK 21 recommended)
- JDK 11+ (JDK 21 recommended). The repository includes the Gradle wrapper; a global Gradle installation is not required

Gradle:

```powershell
.\gradlew.bat clean build
```

PowerShell fallback:

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\build.ps1
```

Neither build path requires Alora, RuneLite, Jagex, or another private client
JAR. Runtime compatibility is discovered dynamically when Rune Forge starts.

Build outputs:

```text
dist/RuneForge-Loader.jar
dist/scripts/RuneForge-Example.jar
dist/scripts/RuneForge-Combat.jar
```

## Building a Windows release

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\package-release.ps1
```

This creates:

```text
build/Rune-Forge-v1.0.5-Windows.zip
```

The packaging script runs a release audit and rejects unexpected JAR/ZIP files.

## Script trust model

A script JAR executes inside the same JVM as the client. Java does not provide a
reliable modern in-process sandbox for arbitrary plugin code. Rune Forge
therefore treats third-party scripts as trusted code.

The first time a script hash is loaded, Rune Forge shows its SHA-256 fingerprint
and requires explicit confirmation. If the JAR changes, the hash changes and
Rune Forge asks again.

## Writing scripts

See [docs/script-development.md](docs/script-development.md).

A script implements `RuneForgeScript` and declares its entry point:

```text
Rune-Forge-Script-Class: io.runeforge.scripts.example.ExampleScript
```

## Third-party software

Rune Forge is not affiliated with RuneLite, Jagex, Alora, Eclipse Adoptium, or
Gradle. Third-party client binaries are not included in the source or Windows
release archive.

## License

MIT. See [LICENSE](LICENSE).
