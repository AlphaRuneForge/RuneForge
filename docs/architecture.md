# Architecture

Rune Forge has three layers:

1. **Bootstrap** — a Java 8-compatible agent entry point. It is deliberately limited to JDK classes so it can survive being inherited by an updater JVM.
2. **Loader** — starts only after the RuneLite runtime is available, obtains `Client`, `ClientThread`, and `EventBus`, and manages script JAR lifecycles.
3. **Scripts** — separate JAR files implementing `RuneForgeScript`. Each script owns its own UI and runtime behavior.

Scripts are discovered from the configured `scripts` directory. A script JAR identifies its entry point with the manifest attribute:

```text
Rune-Forge-Script-Class: io.runeforge.scripts.example.ExampleScript
```

The loader is intentionally independent from individual scripts. Adding a new script should not require a loader rebuild unless the public script API itself changes.
