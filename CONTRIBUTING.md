# Contributing

Keep changes focused and readable.

- Build before opening a pull request.
- Keep loader changes separate from script behavior when practical.
- Do not commit downloaded runtimes, logs, account data, or third-party client binaries.
- Prefer small methods with descriptive names over large controller methods.
- Avoid reflection unless the public client API does not expose the required state.
- New scripts should include their own UI and lifecycle cleanup.

For local builds, set `ALORA_CLIENT_JAR` to the installed `client_runelite.jar` or pass `-ClientJar` to `packaging/build.ps1`.
