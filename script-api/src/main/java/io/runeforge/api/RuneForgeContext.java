package io.runeforge.api;

import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;

import javax.swing.JFrame;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class RuneForgeContext {
    private final Client client;
    private final ClientThread clientThread;
    private final EventBus eventBus;
    private final JFrame loaderFrame;
    private final Path dataDirectory;
    private final Consumer<String> logger;

    public RuneForgeContext(
        Client client,
        ClientThread clientThread,
        EventBus eventBus,
        JFrame loaderFrame,
        Path dataDirectory,
        Consumer<String> logger) {
        this.client = client;
        this.clientThread = clientThread;
        this.eventBus = eventBus;
        this.loaderFrame = loaderFrame;
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    public Client getClient() {
        return client;
    }

    public ClientThread getClientThread() {
        return clientThread;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public JFrame getLoaderFrame() {
        return loaderFrame;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
}
