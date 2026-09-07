# Architecture

Rune Forge 1.0.4 has four layers.

1. **Bootstrap** — Java 8-compatible agent entry point. It uses
   `Instrumentation.getAllLoadedClasses()` to locate the actual classloader that
   loaded `net.runelite.client.RuneLite`. It writes a dedicated bootstrap log and
   reports a fatal timeout instead of silently disappearing.
2. **Loader** — obtains the runtime injector and required services through
   reflection. It manages script discovery, trust confirmation, lifecycle, and
   logging.
3. **Script API** — Rune Forge-owned interfaces and a reflection-based
   `RuneForgeClient` bridge. Public source compilation no longer requires a
   private client JAR.
4. **Scripts** — separate JAR files implementing `RuneForgeScript`.

The reflection bridge intentionally centralizes client-fork compatibility.
Scripts do not import third-party client classes directly.

Scripts are trusted JVM code, not sandboxed code. The loader stores accepted
SHA-256 fingerprints in `trusted-scripts.sha256` and prompts again whenever a
script JAR changes.
