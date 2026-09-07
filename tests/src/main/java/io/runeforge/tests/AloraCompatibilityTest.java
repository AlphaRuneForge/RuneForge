package io.runeforge.tests;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;

/** Optional offline compatibility check: takes a locally supplied client JAR; never logs in. */
public final class AloraCompatibilityTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected path to Alora client JAR");
        }
        Constructor<?> adapter = Class.forName("io.runeforge.api.AloraMenuDispatcher")
            .getDeclaredConstructor(ClassLoader.class);
        adapter.setAccessible(true);
        URL[] urls = {Paths.get(args[0]).toUri().toURL()};
        try (URLClassLoader loader = new URLClassLoader(urls, AloraCompatibilityTest.class.getClassLoader())) {
            adapter.newInstance(loader);
            GroundItemsTest.verifyTake(loader);
        }
        System.out.println("Alora action adapter resolves against the supplied compatible client.");
    }
}
