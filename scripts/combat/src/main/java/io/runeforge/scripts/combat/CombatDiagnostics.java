package io.runeforge.scripts.combat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/** Bounded rotating diagnostic files, separate from the loader's UI log. */
final class CombatDiagnostics implements AutoCloseable {
    private final FileHandler file;
    private long sequence;

    CombatDiagnostics(Path directory) throws java.io.IOException {
        java.nio.file.Files.createDirectories(directory);
        file = new FileHandler(directory.resolve("combat-debug-%g.log").toString(), 2_000_000, 3, true);
        file.setEncoding("UTF-8");
        file.setFormatter(new Formatter() {
            @Override public String format(LogRecord record) { return record.getMessage() + System.lineSeparator(); }
        });
    }

    synchronized void log(String event, String detail) {
        file.publish(new LogRecord(java.util.logging.Level.INFO,
            Instant.now() + " seq=" + (++sequence) + " thread=" + Thread.currentThread().getName()
                + " event=" + event + " " + detail.replace('\n', ' ').replace('\r', ' ')));
        file.flush();
    }

    synchronized void error(String event, Throwable error) {
        StringWriter stack = new StringWriter();
        error.printStackTrace(new PrintWriter(stack));
        log(event, stack.toString());
    }

    @Override public synchronized void close() { file.close(); }
}
