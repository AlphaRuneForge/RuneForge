# Script development

A script implements `io.runeforge.api.RuneForgeScript`.

```java
public final class MyScript implements RuneForgeScript {
    public String getName() { return "My Script"; }
    public String getVersion() { return "1.0.0"; }

    public void onLoad(RuneForgeContext context) {}
    public void onStart() {}
    public void onStop() {}
    public void onUnload() {}
}
```

Use `context.getGameClient()` for supported client operations and
`context.invokeOnClientThread(...)` for work that must run on the client thread.

Avoid importing a private client fork's classes directly. Keeping script code on
the Rune Forge bridge allows the public source tree to compile without
redistributing or depending on third-party client binaries.

Third-party script JARs are trusted code. They share the client JVM and are not
security-sandboxed, so users are shown a SHA-256 trust prompt before a new or
changed script is loaded.
