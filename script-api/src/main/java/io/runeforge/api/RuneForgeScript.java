package io.runeforge.api;

public interface RuneForgeScript {
    String getName();
    String getVersion();

    void onLoad(RuneForgeContext context) throws Exception;
    void onStart() throws Exception;
    void onStop() throws Exception;
    void onUnload() throws Exception;
}
