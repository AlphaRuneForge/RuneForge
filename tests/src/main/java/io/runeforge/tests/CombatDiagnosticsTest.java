package io.runeforge.tests;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CombatDiagnosticsTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("combat-diagnostics-test-");
        Class<?> type = Class.forName("io.runeforge.scripts.combat.CombatDiagnostics");
        Constructor<?> constructor = type.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        Object logger = constructor.newInstance(directory);
        Method log = type.getDeclaredMethod("log", String.class, String.class);
        Method error = type.getDeclaredMethod("error", String.class, Throwable.class);
        log.setAccessible(true);
        error.setAccessible(true);
        try (AutoCloseable closeable = (AutoCloseable) logger) {
            log.invoke(logger, "CONFIG", "loot=Bones bury=true café\nfake-line");
            error.invoke(logger, "LOOP_ERROR", new IllegalStateException("outer", new Exception("root cause")));
            String content = Files.readString(directory.resolve("combat-debug-0.log"));
            if (!content.contains("café fake-line") || !content.contains("root cause")
                || !content.contains("seq=2") || content.lines().count() != 2) {
                throw new AssertionError("Missing UTF-8, single-line events, sequence, or nested error details");
            }
            String large = "x".repeat(100_000);
            for (int i = 0; i < 70; i++) log.invoke(logger, "ROTATION", large);
        }
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            Path[] logs = files.toArray(Path[]::new);
            if (logs.length != 3) throw new AssertionError("Expected exactly three rotated files");
            for (Path path : logs) {
                if (Files.size(path) > 2_200_000) throw new AssertionError("Rotation size exceeded");
                Files.delete(path);
            }
        }
        Files.delete(directory);
        System.out.println("Combat diagnostic encoding, error chains, flushing, and bounded rotation passed.");
    }
}
