# Changelog

## 1.0.5

- Fixed Gradle by applying the Java plugin instead of the Base plugin.
- Declared loader before bootstrap and configured cross-source-set classpaths after creation.
- Added checked-in wrapper scripts, wrapper properties, and wrapper bootstrap JAR.
- Made `gradlew` executable in the source archive.
- CI now runs the checked-in wrapper.
- Expanded self-tests for menu-action parameter order and Attack/Take/Eat/Bury action selection.
- Runtime compatibility still requires a controlled test against the separately obtained compatible client.

## 1.0.4

- Unified launcher documentation on `client/Client-Launcher.jar`.
- Replaced the placeholder Gradle task with a real compilation/package build.
- Added automated self-tests and a GitHub Actions workflow.
- Removed compile-time dependency on a private client JAR by introducing a
  Rune Forge-owned reflection bridge.
- Replaced combat coordinate clicking with normal client menu-action calls for
  Attack, Take, Eat, and Bury.
- Changed bootstrap detection to inspect loaded classes and use the runtime's
  actual classloader.
- Added visible bootstrap timeout/failure diagnostics.
- Fixed restart behavior when the combat scheduler terminates.
- Moved combat configuration reads onto Swing's EDT through immutable snapshots.
- Fixed script logging so unload cannot dereference a cleared global script.
- Added SHA-256 trust confirmation for new/changed script JARs.
- Added SHA-256 verification for downloaded Temurin runtime archives.
- Fixed release documentation/version mismatches.
- Added an automated release-content audit.

## 1.0.3

- Removed all Alora, RuneLite, and Jagex binaries from the public Windows release.
- Public release contains Rune Forge-owned loader/scripts/launcher assets only.
- The launcher requires the user to supply their compatible client launcher locally.
- Added release-time checks that reject third-party client binaries from the package.

## 1.0.2

- Fixed Windows startup failure caused by assigning to PowerShell's read-only `$HOME` variable.

## 1.0.1

- Initial public-source cleanup and diagnostics.

## 1.0.0

- Initial release.
