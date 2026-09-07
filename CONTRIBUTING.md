# Contributing

Keep changes focused and readable.

- Run `gradle build` or `packaging/build.ps1` before opening a pull request.
- Keep loader changes separate from script behavior when practical.
- Do not commit downloaded runtimes, logs, account data, or third-party client binaries.
- Prefer the Rune Forge client bridge over direct imports from a private client fork.
- Add or update self-tests for reusable logic.
- Preserve the script trust prompt and release-binary audit.
- New scripts should include lifecycle cleanup and keep Swing state on the EDT.
