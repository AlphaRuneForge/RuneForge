package io.runeforge.api;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class Reflection {
    private Reflection() {
    }

    static Object invoke(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }

        Method method = findCompatibleMethod(target.getClass(), name, args);
        if (method == null) {
            throw new IllegalStateException(
                "Required method not found: " + target.getClass().getName() + "." + name);
        }

        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "Invocation failed: " + target.getClass().getName() + "." + name, e);
        }
    }

    static Method findCompatibleMethod(Class<?> type, String name, Object... args) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name)
                || method.getParameterCount() != args.length) {
                continue;
            }

            Class<?>[] parameters = method.getParameterTypes();
            boolean compatible = true;

            for (int i = 0; i < parameters.length; i++) {
                if (!isCompatible(parameters[i], args[i])) {
                    compatible = false;
                    break;
                }
            }

            if (compatible) {
                return method;
            }
        }

        return null;
    }

    static Object enumConstant(ClassLoader loader, String className, String constant) {
        try {
            Class<?> enumType = Class.forName(className, false, loader);
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object value = Enum.valueOf((Class<? extends Enum>) enumType.asSubclass(Enum.class), constant);
            return value;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "Required enum constant unavailable: " + className + "." + constant, e);
        }
    }

    static List<Object> asList(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }

        if (value instanceof Collection<?>) {
            return new ArrayList<>((Collection<?>) value);
        }

        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> result = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                result.add(Array.get(value, i));
            }
            return result;
        }

        return Collections.singletonList(value);
    }

    static int intValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    static boolean boolValue(Object value) {
        return value instanceof Boolean && (Boolean) value;
    }

    private static boolean isCompatible(Class<?> parameter, Object argument) {
        if (argument == null) {
            return !parameter.isPrimitive();
        }

        Class<?> actual = argument.getClass();
        if (parameter.isAssignableFrom(actual)) {
            return true;
        }

        if (!parameter.isPrimitive()) {
            return false;
        }

        if (parameter == int.class) return actual == Integer.class;
        if (parameter == long.class) return actual == Long.class;
        if (parameter == boolean.class) return actual == Boolean.class;
        if (parameter == double.class) return actual == Double.class;
        if (parameter == float.class) return actual == Float.class;
        if (parameter == short.class) return actual == Short.class;
        if (parameter == byte.class) return actual == Byte.class;
        if (parameter == char.class) return actual == Character.class;

        return false;
    }
}
