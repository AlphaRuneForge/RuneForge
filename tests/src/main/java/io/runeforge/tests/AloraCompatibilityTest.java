package io.runeforge.tests;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;

/** Optional offline compatibility check: takes a locally supplied client JAR; never logs in. */
public final class AloraCompatibilityTest {
    public static void main(String[] args) throws Exception {
        Constructor<?> adapter = Class.forName("io.runeforge.api.AloraMenuDispatcher")
            .getDeclaredConstructor(ClassLoader.class);
        adapter.setAccessible(true);
        URL[] urls = {Paths.get(args[0]).toUri().toURL()};
        try (URLClassLoader loader = new URLClassLoader(urls, AloraCompatibilityTest.class.getClassLoader())) {
            adapter.newInstance(loader);
        }
        try (URLClassLoader changed = new URLClassLoader(urls, AloraCompatibilityTest.class.getClassLoader()) {
            @Override public InputStream getResourceAsStream(String name) {
                return name.equals("com/alora/dG.class")
                    ? new ByteArrayInputStream(new byte[]{1, 2, 3}) : super.getResourceAsStream(name);
            }
        }) {
            try {
                adapter.newInstance(changed);
                throw new AssertionError("Changed client was accepted");
            } catch (InvocationTargetException expected) {
                if (!(expected.getCause() instanceof IllegalStateException)
                    || !expected.getCause().getMessage().contains("client changed")) throw expected;
            }
        }
        System.out.println("Alora adapter resolves against installed client and rejects changed bytecode.");
    }
}
