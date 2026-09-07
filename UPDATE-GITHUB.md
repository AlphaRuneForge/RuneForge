# Update an existing Rune Forge repository

Copy the contents of this source package into the root of your cloned repository,
replacing the older Rune Forge source files.

Then run:

```powershell
git status
git add -A
git commit -m "Release Rune Forge v1.0.3"
git push origin main
```

Create the release tag:

```powershell
git tag -a v1.0.3 -m "Rune Forge v1.0.3"
git push origin v1.0.3
```

On GitHub, create a release from tag `v1.0.3` and attach:

```text
Rune-Forge-v1.0.3-Windows.zip
```

Do not commit downloaded Java runtimes, logs, `alora-data`, or third-party client
JARs to the source repository.
