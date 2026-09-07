package io.runeforge.api;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Compatibility adapter for Alora's RuneLite-backed menu dispatcher. */
final class AloraMenuDispatcher {
    private final Constructor<?> entryConstructor;
    private final Method dispatch;

    AloraMenuDispatcher(ClassLoader loader) {
        try {
            Class<?> entry = Class.forName("com.alora.aW", false, loader);
            Class<?> world = Class.forName("com.alora.WorldView", false, loader);
            Class<?> dispatcher = Class.forName("com.alora.dG", false, loader);

            // Resolve the stable MenuEntry-shaped contract instead of pinning the whole
            // obfuscated classes to one SHA-256. Alora can rebuild unrelated bytecode
            // without changing this action path.
            entryConstructor = entry.getConstructor(String.class, String.class, int.class,
                long.class, int.class, int.class, boolean.class, int.class);
            requireMethod(entry, "getParam0");
            requireMethod(entry, "getParam1");
            requireMethod(entry, "getIdentifier");
            requireMethod(entry, "getType");
            requireMethod(entry, "getWorldViewId");

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

    String invoke(Object client, Object action, int param0, int param1,
                  int identifier, String option, String target) {
        Object world = Reflection.invoke(client, "getTopLevelWorldView");
        if (world == null) throw new IllegalStateException("No active world for menu action");
        int worldId = Reflection.intValue(Reflection.invoke(world, "getId"), -1);
        int opcode = Reflection.intValue(Reflection.invoke(action, "getId"), -1);
        try {
            Object entry = entryConstructor.newInstance(option, target, opcode,
                (long) identifier, param0, param1, false, worldId);
            String before = describe(entry, worldId, opcode);
            // The normal click handler posts MenuOptionClicked and respects event consumption.
            dispatch.invoke(null, entry, world);
            return "before={" + before + "} after={" + describe(entry, worldId, opcode) + "}";
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Alora action dispatch failed: " + option + " " + target, e);
        }
    }

    private static String describe(Object entry, int requestedWorldId, int requestedOpcode) {
        return "option=" + quote(Reflection.invoke(entry, "getOption"))
            + " target=" + quote(Reflection.invoke(entry, "getTarget"))
            + " requestedOpcode=" + requestedOpcode
            + " type=" + String.valueOf(Reflection.invoke(entry, "getType"))
            + " identifier=" + Reflection.intValue(Reflection.invoke(entry, "getIdentifier"), -1)
            + " param0=" + Reflection.intValue(Reflection.invoke(entry, "getParam0"), -1)
            + " param1=" + Reflection.intValue(Reflection.invoke(entry, "getParam1"), -1)
            + " widgetId=" + optionalInt(entry, "getWidgetId")
            + " itemId=" + optionalInt(entry, "getItemId")
            + " requestedWorldViewId=" + requestedWorldId
            + " entryWorldViewId=" + optionalInt(entry, "getWorldViewId")
            + " forceLeftClick=" + optionalValue(entry, "isForceLeftClick");
    }

    private static String optionalInt(Object target, String method) {
        Object value = optionalInvoke(target, method);
        return value instanceof Number ? Integer.toString(((Number) value).intValue()) : "n/a";
    }

    private static String optionalValue(Object target, String method) {
        Object value = optionalInvoke(target, method);
        return value == null ? "n/a" : String.valueOf(value);
    }

    private static Object optionalInvoke(Object target, String method) {
        try {
            Method found = Reflection.findCompatibleMethod(target.getClass(), method);
            if (found == null) return null;
            found.setAccessible(true);
            return found.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static String quote(Object value) {
        return "\"" + String.valueOf(value).replace("\"", "\\\"") + "\"";
    }

    private static void requireMethod(Class<?> type, String name) throws NoSuchMethodException {
        type.getMethod(name);
    }
}
