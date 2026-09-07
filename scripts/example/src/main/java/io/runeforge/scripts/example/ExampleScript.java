package io.runeforge.scripts.example;

import io.runeforge.api.RuneForgeContext;
import io.runeforge.api.RuneForgeScript;

public final class ExampleScript implements RuneForgeScript {
    private RuneForgeContext context;

    @Override
    public String getName() {
        return "Example";
    }

    @Override
    public String getVersion() {
        return "1.0.4";
    }

    @Override
    public void onLoad(RuneForgeContext context) {
        this.context = context;
        context.log("Example script loaded.");
    }

    @Override
    public void onStart() {
        if (context != null) context.log("Example script started.");
    }

    @Override
    public void onStop() {
        if (context != null) context.log("Example script stopped.");
    }

    @Override
    public void onUnload() {
        RuneForgeContext old = context;
        context = null;
        if (old != null) old.log("Example script unloaded.");
    }
}
