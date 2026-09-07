# Update the GitHub repository to v1.0.4

Copy the contents of this source package into the root of the cloned Rune Forge
repository, replacing the older source files.

Then run:

```powershell
git status
git add -A
git commit -m "Release Rune Forge v1.0.4"
git push origin main

git tag -a v1.0.4 -m "Rune Forge v1.0.4"
git push origin v1.0.4
```

Create a GitHub release from tag `v1.0.4` and attach:

```text
Rune-Forge-v1.0.4-Windows.zip
```

Do not commit downloaded runtimes, logs, `runeforge-data`, or game-client
binaries.
