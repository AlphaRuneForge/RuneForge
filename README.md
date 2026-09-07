# Rune Forge

**Current release: v1.0.3**

A modular Java script loader with a portable Windows launcher.

Rune Forge keeps the loader separate from individual scripts. Scripts are ordinary JAR files with their own UI and lifecycle, so adding or updating a script does not require rebuilding the loader.

![Rune Forge banner](assets/banner.png)

## Features

- Portable Windows launcher
- Private Temurin Java 21 runtime downloaded on first run
- Modular script JAR loading
- Load, start, stop, and unload lifecycle
- Per-script data directories
- Central diagnostic logging
- Example script
- Combat script with its own configuration window

## Repository layout

```text
launcher/       Windows startup scripts
loader/         Java agent bootstrap and script loader
script-api/     Public script interface and context
scripts/        Individual script modules
packaging/      Build and release scripts
docs/           Architecture and script-development notes
assets/         Project artwork
vendor/         Third-party launcher placeholder only
```

## Requirements for building

- Windows
- JDK 11 or newer with `javac` and `jar`
- An installed Alora RuneLite client JAR

The build script checks `ALORA_CLIENT_JAR` first and then:

```text
%USERPROFILE%\alora\client_runelite.jar
```

Build:

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\build.ps1
```

## Running locally

Rune Forge does not redistribute Alora's launcher.

Download the official JAR launcher from Alora and save it as:

```text
vendor\Alora-Launcher.jar
```

Then build Rune Forge and run:

```text
launcher\Start-RuneForge.cmd
```

The launcher downloads a private Temurin Java 21 JRE into the project folder if one is not already present.

## Building a portable release

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\package-release.ps1
```

This creates:

```text
build\Rune-Forge-v1.0.0-Windows.zip
```

## Writing scripts

See [docs/script-development.md](docs/script-development.md).

Each script implements `RuneForgeScript` and declares its entry class in `META-INF/MANIFEST.MF`:

```text
Rune-Forge-Script-Class: io.runeforge.scripts.example.ExampleScript
```

## Third-party software

Rune Forge is not affiliated with RuneLite, Jagex, or Alora. Third-party client and launcher binaries are not part of this repository or the MIT license.

## License

MIT. See [LICENSE](LICENSE).


## Client requirement

Rune Forge does not ship a game client or game-client launcher. Before using the
Windows package, place a compatible launcher that you obtained separately at:

```text
client/Client-Launcher.jar
```

That file is intentionally excluded from the repository and release archive.
