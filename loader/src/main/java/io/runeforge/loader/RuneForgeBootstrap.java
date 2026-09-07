package io.runeforge.loader;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;

public final class RuneForgeBootstrap {
    private RuneForgeBootstrap() {
    }

    public static void premain(String args, Instrumentation instrumentation) {
        Thread bootstrap = new Thread(new Runnable() {
            @Override
            public void run() {
                ClassLoader loader = ClassLoader.getSystemClassLoader();

                for (int attempt = 0; attempt < 480; attempt++) {
                    try {
                        Class.forName("net.runelite.client.RuneLite", false, loader);
                        Class<?> runtime = Class.forName(
                            "io.runeforge.loader.RuneForgeLoader",
                            true,
                            loader);

                        Method start = runtime.getMethod("start");
                        start.invoke(null);
                        return;
                    } catch (ClassNotFoundException e) {
                        sleep(250L);
                    } catch (Throwable e) {
                        e.printStackTrace();
                        sleep(500L);
                    }
                }
            }
        }, "RuneForge-Bootstrap");

        bootstrap.setDaemon(true);
        bootstrap.start();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
