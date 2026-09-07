package io.runeforge.api;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;

/** Compatibility adapter for the inspected Alora build; never guesses after a client update. */
final class AloraMenuDispatcher {
    private final Constructor<?> entryConstructor;
    private final Method dispatch;

    AloraMenuDispatcher(ClassLoader loader) {
        try {
            verify(loader, "com/alora/dG.class", "19ac18a833025cdc8c6b057e409a53641b05d477650544265a4ce3226649c170");
            verify(loader, "com/alora/aW.class", "db3600c2212389115a949c0f98c8b01b8091d8369d17600838f556898a2adbc3");
            Class<?> entry = Class.forName("com.alora.aW", false, loader);
            Class<?> world = Class.forName("com.alora.WorldView", false, loader);
            Class<?> dispatcher = Class.forName("com.alora.dG", false, loader);
            entryConstructor = entry.getConstructor(String.class, String.class, int.class,
                long.class, int.class, int.class, boolean.class, int.class);
            Method found = null;
            for (Method method : dispatcher.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (Modifier.isStatic(method.getModifiers()) && method.getReturnType() == void.class
                    && parameters.length == 2 && parameters[0] == entry && parameters[1] == world) {
                    if (found != null) throw new IllegalStateException("Ambiguous Alora action dispatcher");
                    found = method;
                }
            }
            if (found == null) throw new IllegalStateException("Alora action dispatcher unavailable");
            found.setAccessible(true);
            dispatch = found;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported Alora action API", e);
        }
    }

    void invoke(Object client, Object action, int param0, int param1,
                int identifier, String option, String target) {
        Object world = Reflection.invoke(client, "getTopLevelWorldView");
        if (world == null) throw new IllegalStateException("No active world for menu action");
        int worldId = Reflection.intValue(Reflection.invoke(world, "getId"), -1);
        int opcode = Reflection.intValue(Reflection.invoke(action, "getId"), -1);
        try {
            Object entry = entryConstructor.newInstance(option, target, opcode,
                (long) identifier, param0, param1, false, worldId);
            // The normal click handler posts MenuOptionClicked and respects event consumption.
            dispatch.invoke(null, entry, world);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Alora action dispatch failed: " + option + " " + target, e);
        }
    }

    private static void verify(ClassLoader loader, String resource, String expected) {
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Missing " + resource);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes());
            StringBuilder actual = new StringBuilder();
            for (byte value : digest) actual.append(String.format("%02x", value & 255));
            if (!expected.equals(actual.toString())) {
                throw new IllegalStateException("Alora client changed; RuneForge action adapter needs updating");
            }
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to verify Alora action adapter", e);
        }
    }
}
