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
        return "1.0.0";
    }

    @Override
    public void onLoad(RuneForgeContext context) {
        this.context = context;
        context.log("Example script loaded.");
    }

    @Override
    public void onStart() {
        context.log("Example script started.");
    }

    @Override
    public void onStop() {
        context.log("Example script stopped.");
    }

    @Override
    public void onUnload() {
        context.log("Example script unloaded.");
        context = null;
    }
}
