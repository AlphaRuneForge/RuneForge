# Changelog

## 1.0.2

- Fixed Windows startup failure caused by assigning to PowerShell's read-only
  automatic `$HOME` variable.
- Renamed the portable data-path variable to `$RuneForgeHome`.
- Rebuilt the source and Windows release from the corrected launcher.

## 1.0.1

- Made the GitHub source tree and packaged Windows release use the same layout.
- Added clearer startup diagnostics and loader log locations.
- Added explicit checks for missing built script output.
- Fixed the Combat script's eat-threshold check to use the client's current Hitpoints level.
- Added third-party notices.
- Added visible loader/release versioning.

## 1.0.0

- Initial public source and Windows release.
