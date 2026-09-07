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

The context provides the RuneLite `Client`, `ClientThread`, `EventBus`, the loader frame, a per-script data directory, and a logger.

Keep script-specific UI and behavior inside the script module. The loader should only be responsible for discovery, lifecycle, shared context, and logging.
