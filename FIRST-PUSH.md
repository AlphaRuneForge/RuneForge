# First push

Extract this package into the root of your cloned GitHub repository.

Then open PowerShell in that folder:

```powershell
git status
git add .
git commit -m "Initial Rune Forge release"
git push origin main
```

After the push, add a short repository description such as:

> Modular Java script loader with a portable Windows runtime and pluggable script JARs.

Suggested topics:

```text
java runelite plugin-loader windows automation modular scripting
```

Do not commit `vendor/Alora-Launcher.jar`, `client_runelite.jar`, downloaded Java runtimes, logs, or `alora-data`.
