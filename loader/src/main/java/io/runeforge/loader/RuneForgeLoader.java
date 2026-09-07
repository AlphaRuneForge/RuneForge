package io.runeforge.loader;

import com.google.inject.Injector;
import io.runeforge.api.RuneForgeContext;
import io.runeforge.api.RuneForgeScript;
import net.runelite.api.Client;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class RuneForgeLoader {
    private static final String VERSION = "1.0.2";
    private static volatile boolean started;

    private static Client client;
    private static ClientThread clientThread;
    private static EventBus eventBus;

    private static Path baseDirectory;
    private static Path scriptsDirectory;
    private static Path dataDirectory;
    private static Path logFile;

    private static JFrame frame;
    private static DefaultListModel<String> scriptModel;
    private static JList<String> scriptList;
    private static JTextArea logArea;
    private static JLabel statusLabel;

    private static RuneForgeScript loadedScript;
    private static URLClassLoader scriptClassLoader;

    private RuneForgeLoader() {
    }

    public static synchronized void start() {
        if (started) {
            return;
        }
        started = true;

        baseDirectory = Paths.get(System.getProperty(
            "runeforge.home",
            System.getProperty("user.home") + File.separator + "RuneForge"))
            .toAbsolutePath();

        scriptsDirectory = baseDirectory.resolve("scripts");
        dataDirectory = baseDirectory.resolve("script-data");
        logFile = baseDirectory.resolve("rune-forge.log");

        try {
            Files.createDirectories(scriptsDirectory);
            Files.createDirectories(dataDirectory);
        } catch (Exception e) {
            log("Unable to create Rune Forge directories: " + rootCause(e));
        }

        Thread init = new Thread(RuneForgeLoader::initializeRuneLite, "RuneForge-Init");
        init.setDaemon(true);
        init.start();
    }

    private static void initializeRuneLite() {
        try {
            Injector injector = null;

            for (int attempt = 0; attempt < 240 && injector == null; attempt++) {
                injector = RuneLite.getInjector();
                if (injector == null) {
                    Thread.sleep(250L);
                }
            }

            if (injector == null) {
                log("RuneLite injector was not available after startup wait.");
                return;
            }

            client = injector.getInstance(Client.class);
            clientThread = injector.getInstance(ClientThread.class);
            eventBus = injector.getInstance(EventBus.class);

            if (client == null || clientThread == null || eventBus == null) {
                log("RuneLite dependencies were incomplete.");
                return;
            }

            SwingUtilities.invokeLater(RuneForgeLoader::createUi);
        } catch (Throwable e) {
            log("Loader initialization failed: " + rootCause(e));
        }
    }

    private static void createUi() {
        frame = new JFrame("Rune Forge " + VERSION);
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel header = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Rune Forge");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        header.add(title, BorderLayout.WEST);

        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton refreshButton = new JButton("Refresh");
        JButton importButton = new JButton("Import Script...");
        headerActions.add(refreshButton);
        headerActions.add(importButton);
        header.add(headerActions, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        scriptModel = new DefaultListModel<>();
        scriptList = new JList<>(scriptModel);
        scriptList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scriptScroll = new JScrollPane(scriptList);
        scriptScroll.setPreferredSize(new Dimension(320, 220));

        logArea = new JTextArea(10, 44);
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);

        JSplitPane splitPane = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            scriptScroll,
            logScroll);
        splitPane.setResizeWeight(0.45);
        root.add(splitPane, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

        JButton loadButton = new JButton("Load");
        JButton startButton = new JButton("Start");
        JButton stopButton = new JButton("Stop");
        JButton unloadButton = new JButton("Unload");

        actions.add(loadButton);
        actions.add(startButton);
        actions.add(stopButton);
        actions.add(unloadButton);

        statusLabel = new JLabel("Ready");
        footer.add(actions, BorderLayout.WEST);
        footer.add(statusLabel, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        refreshButton.addActionListener(e -> refreshScripts());
        importButton.addActionListener(e -> importScript());
        loadButton.addActionListener(e -> loadSelectedScript());
        startButton.addActionListener(e -> startLoadedScript());
        stopButton.addActionListener(e -> stopLoadedScript());
        unloadButton.addActionListener(e -> unloadScript());

        frame.setContentPane(root);
        frame.setSize(590, 530);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);

        refreshScripts();
        log("Rune Forge " + VERSION + " loader ready.");
    }

    private static void refreshScripts() {
        scriptModel.clear();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(scriptsDirectory, "*.jar")) {
            for (Path script : stream) {
                scriptModel.addElement(script.getFileName().toString());
            }
            setStatus("Scripts: " + scriptModel.size());
        } catch (Exception e) {
            log("Script refresh failed: " + rootCause(e));
        }
    }

    private static void importScript() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JAR files", "jar"));

        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        try {
            Path source = chooser.getSelectedFile().toPath();
            Path destination = scriptsDirectory.resolve(source.getFileName());
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            refreshScripts();
            log("Imported " + destination.getFileName());
        } catch (Exception e) {
            log("Import failed: " + rootCause(e));
        }
    }

    private static void loadSelectedScript() {
        if (loadedScript != null) {
            setStatus("Unload the current script first.");
            return;
        }

        String selected = scriptList.getSelectedValue();
        if (selected == null) {
            setStatus("Select a script.");
            return;
        }

        try {
            Path jar = scriptsDirectory.resolve(selected);
            String scriptClass = readScriptClass(jar);

            scriptClassLoader = new URLClassLoader(
                new URL[]{jar.toUri().toURL()},
                RuneForgeLoader.class.getClassLoader());

            Object instance = Class.forName(scriptClass, true, scriptClassLoader)
                .getDeclaredConstructor()
                .newInstance();

            if (!(instance instanceof RuneForgeScript)) {
                throw new IllegalArgumentException(
                    scriptClass + " does not implement RuneForgeScript");
            }

            loadedScript = (RuneForgeScript) instance;
            Path scriptData = dataDirectory.resolve(sanitize(loadedScript.getName()));
            Files.createDirectories(scriptData);

            RuneForgeContext context = new RuneForgeContext(
                client,
                clientThread,
                eventBus,
                frame,
                scriptData,
                message -> log("[" + loadedScript.getName() + "] " + message));

            loadedScript.onLoad(context);
            setStatus("Loaded: " + loadedScript.getName());
            log("Loaded " + loadedScript.getName() + " " + loadedScript.getVersion());
        } catch (Throwable e) {
            log("Load failed: " + rootCause(e));
            setStatus("Load failed.");
            closeScriptClassLoader();
            loadedScript = null;
        }
    }

    private static void startLoadedScript() {
        if (loadedScript == null) {
            setStatus("Load a script first.");
            return;
        }

        try {
            loadedScript.onStart();
            setStatus("Running: " + loadedScript.getName());
        } catch (Exception e) {
            log("Start failed: " + rootCause(e));
        }
    }

    private static void stopLoadedScript() {
        if (loadedScript == null) {
            return;
        }

        try {
            loadedScript.onStop();
            setStatus("Stopped: " + loadedScript.getName());
        } catch (Exception e) {
            log("Stop failed: " + rootCause(e));
        }
    }

    private static void unloadScript() {
        if (loadedScript == null) {
            return;
        }

        try {
            try {
                loadedScript.onStop();
            } catch (Exception ignored) {
            }

            loadedScript.onUnload();
            log("Unloaded " + loadedScript.getName());
        } catch (Exception e) {
            log("Unload failed: " + rootCause(e));
        } finally {
            loadedScript = null;
            closeScriptClassLoader();
            setStatus("Ready");
        }
    }

    private static String readScriptClass(Path scriptJar) throws Exception {
        try (JarFile jar = new JarFile(scriptJar.toFile())) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) {
                throw new IllegalArgumentException("Script JAR has no manifest.");
            }

            Attributes attributes = manifest.getMainAttributes();
            String value = attributes.getValue("Rune-Forge-Script-Class");

            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(
                    "Missing Rune-Forge-Script-Class manifest attribute.");
            }

            return value.trim();
        }
    }

    private static void closeScriptClassLoader() {
        if (scriptClassLoader == null) {
            return;
        }

        try {
            scriptClassLoader.close();
        } catch (Exception ignored) {
        } finally {
            scriptClassLoader = null;
        }
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private static void setStatus(String value) {
        if (statusLabel != null) {
            statusLabel.setText(value);
        }
    }

    private static synchronized void log(String message) {
        String line = OffsetDateTime.now() + " " + message;

        try {
            if (logFile != null) {
                Files.write(
                    logFile,
                    (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            }
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            if (logArea != null) {
                logArea.append(line + System.lineSeparator());
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
        });
    }

    private static String rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current.getClass().getSimpleName()
            + ": "
            + String.valueOf(current.getMessage());
    }
}
