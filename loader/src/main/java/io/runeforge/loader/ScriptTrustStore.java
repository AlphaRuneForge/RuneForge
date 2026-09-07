package io.runeforge.loader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ScriptTrustStore {
    private final Path file;
    private final Set<String> trusted = new HashSet<>();

    ScriptTrustStore(Path file) {
        this.file = file;
        load();
    }

    boolean isTrusted(String sha256) {
        return trusted.contains(normalize(sha256));
    }

    void trust(String sha256) {
        String normalized = normalize(sha256);
        if (!trusted.add(normalized)) {
            return;
        }

        try {
            Files.createDirectories(file.getParent());
            Files.write(
                file,
                (normalized + System.lineSeparator()).getBytes(StandardCharsets.US_ASCII),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        } catch (IOException e) {
            trusted.remove(normalized);
            throw new IllegalStateException("Unable to save script trust decision.", e);
        }
    }

    static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];

            try (java.io.InputStream in = Files.newInputStream(file)) {
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }

            StringBuilder out = new StringBuilder();
            for (byte b : digest.digest()) {
                out.append(String.format("%02x", b & 0xff));
            }
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash script JAR.", e);
        }
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.US_ASCII);
            for (String line : lines) {
                String value = normalize(line);
                if (!value.isEmpty()) {
                    trusted.add(value);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read script trust store.", e);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
