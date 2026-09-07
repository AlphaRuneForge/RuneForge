package io.runeforge.api;

import javax.swing.JFrame;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class RuneForgeContext {
    private final Object client;
    private final Object clientThread;
    private final Object eventBus;
    private final ClassLoader runtimeClassLoader;
    private final JFrame loaderFrame;
    private final Path dataDirectory;
    private final Consumer<String> logger;
    private final RuneForgeClient gameClient;

    public RuneForgeContext(
        Object client,
        Object clientThread,
        Object eventBus,
        ClassLoader runtimeClassLoader,
        JFrame loaderFrame,
        Path dataDirectory,
        Consumer<String> logger) {

        this.client = client;
        this.clientThread = clientThread;
        this.eventBus = eventBus;
        this.runtimeClassLoader = runtimeClassLoader;
        this.loaderFrame = loaderFrame;
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        this.gameClient = new RuneForgeClient(client, runtimeClassLoader);
    }

    public RuneForgeClient getGameClient() {
        return gameClient;
    }

    public Object getRawClient() {
        return client;
    }

    public Object getRawClientThread() {
        return clientThread;
    }

    public Object getRawEventBus() {
        return eventBus;
    }

    public ClassLoader getRuntimeClassLoader() {
        return runtimeClassLoader;
    }

    public JFrame getLoaderFrame() {
        return loaderFrame;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public void invokeOnClientThread(Runnable runnable) {
        if (clientThread == null || runnable == null) {
            return;
        }

        Method method = Reflection.findCompatibleMethod(
            clientThread.getClass(),
            "invokeLater",
            runnable);

        if (method == null) {
            throw new IllegalStateException(
                "ClientThread.invokeLater(Runnable) is not available.");
        }

        try {
            method.setAccessible(true);
            method.invoke(clientThread, runnable);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to schedule client-thread work.", e);
        }
    }

    public void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
}
