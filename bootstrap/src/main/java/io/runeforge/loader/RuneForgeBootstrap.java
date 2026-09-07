package io.runeforge.loader;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public final class RuneForgeBootstrap {
    private static final String RUNTIME_CLASS = "net.runelite.client.RuneLite";
    private static final long POLL_MS = 250L;
    private static final int MAX_ATTEMPTS = 480;

    private RuneForgeBootstrap() {
    }

    public static void premain(String args, Instrumentation instrumentation) {
        Thread thread = new Thread(
            () -> waitForRuntime(instrumentation),
            "RuneForge-Bootstrap");
        thread.setDaemon(true);
        thread.start();
    }

    private static void waitForRuntime(Instrumentation instrumentation) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                ClassLoader runtimeLoader = findRuntimeLoader(instrumentation);
                if (runtimeLoader != null) {
                    bootstrapLog("Detected " + RUNTIME_CLASS
                        + " with loader " + runtimeLoader);
                    RuneForgeLoader.start(runtimeLoader);
                    return;
                }
            } catch (Throwable t) {
                bootstrapLog("Bootstrap probe failed: " + rootCause(t));
            }

            sleep(POLL_MS);
        }

        bootstrapLog("FATAL: timed out after "
            + (MAX_ATTEMPTS * POLL_MS / 1000L)
            + " seconds waiting for " + RUNTIME_CLASS + ".");
    }

    static ClassLoader findRuntimeLoader(Instrumentation instrumentation) {
        for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
            if (RUNTIME_CLASS.equals(loaded.getName())) {
                ClassLoader loader = loaded.getClassLoader();
                return loader != null ? loader : ClassLoader.getSystemClassLoader();
            }
        }
        return null;
    }

    private static void bootstrapLog(String message) {
        String line = Instant.now() + " " + message;
        System.err.println("[RuneForge] " + line);

        try {
            String home = System.getProperty("runeforge.home");
            if (home == null || home.trim().isEmpty()) {
                return;
            }

            Path directory = Paths.get(home);
            Files.createDirectories(directory);
            Files.write(
                directory.resolve("runeforge-bootstrap.log"),
                (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private static String rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": "
            + String.valueOf(current.getMessage());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
