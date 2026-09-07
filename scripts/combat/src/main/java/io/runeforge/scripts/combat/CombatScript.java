package io.runeforge.scripts.combat;

import io.runeforge.api.RuneForgeClient;
import io.runeforge.api.RuneForgeContext;
import io.runeforge.api.RuneForgeScript;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class CombatScript implements RuneForgeScript {
    private static final int MAX_LOOT_DISTANCE = 12;
    private static final long LOOP_DELAY_MS = 350L;
    private static final long ACTION_COOLDOWN_MS = 800L;

    private RuneForgeContext context;
    private ScheduledExecutorService scheduler;
    private volatile boolean running;
    private volatile long lastActionAt;
    private volatile CombatConfig config = CombatConfig.defaults();

    private volatile CombatDiagnostics diagnostics;
    private long lastSnapshotAt;
    private final java.util.Map<String, Long> decisionTimes = new java.util.HashMap<>();
    private String lastConfig = "";
    private long actionSequence;
    private long pendingAt;
    private String pendingAction;
    private String pendingInventory;

    private JFrame frame;
    private JTextField targetField;
    private JTextField hpField;
    private JTextField foodField;
    private JTextArea lootArea;
    private JCheckBox buryBonesCheck;
    private JLabel statusLabel;

    @Override
    public String getName() {
        return "Combat";
    }

    @Override
    public String getVersion() {
        return "1.0.11";
    }

    @Override
    public void onLoad(RuneForgeContext context) {
        this.context = context;
        try {
            diagnostics = new CombatDiagnostics(context.getDataDirectory());
            debug("SESSION", "version=" + getVersion() + " client=" + context.getRawClient().getClass().getName()
                + " java=" + System.getProperty("java.version") + " script="
                + getClass().getProtectionDomain().getCodeSource().getLocation()
                + " api=" + RuneForgeClient.class.getProtectionDomain().getCodeSource().getLocation());
            context.log("Detailed combat diagnostics: " + context.getDataDirectory().resolve("combat-debug-0.log"));
        } catch (Exception e) {
            context.log("Cannot open combat debug log: " + e);
        }
        SwingUtilities.invokeLater(this::createUi);
        context.log("Combat script loaded.");
    }

    @Override
    public synchronized void onStart() {
        boolean schedulerAlive = scheduler != null
            && !scheduler.isShutdown()
            && !scheduler.isTerminated();

        if (running && schedulerAlive) {
            bringToFront();
            return;
        }

        lastSnapshotAt = 0;
        decisionTimes.clear();
        pendingAction = null;
        debug("START", "Combat loop starting");
        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "RuneForge-Combat");
            thread.setDaemon(true);
            return thread;
        });

        scheduler.scheduleWithFixedDelay(
            this::requestTickSafely,
            0L,
            LOOP_DELAY_MS,
            TimeUnit.MILLISECONDS);

        setStatus("Running");
        if (context != null) context.log("Combat script started.");
    }

    @Override
    public synchronized void onStop() {
        running = false;
        debug("STOP", "pending=" + pendingAction);

        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }

        setStatus("Stopped");
        if (context != null) {
            context.log("Combat script stopped.");
        }
    }

    @Override
    public void onUnload() {
        RuneForgeContext oldContext = context;
        onStop();

        SwingUtilities.invokeLater(() -> {
            if (frame != null) {
                frame.dispose();
                frame = null;
            }
        });

        if (oldContext != null) {
            oldContext.log("Combat script unloaded.");
        }
        CombatDiagnostics oldDiagnostics = diagnostics;
        diagnostics = null;
        if (oldDiagnostics != null) oldDiagnostics.close();
        context = null;
    }

    private void createUi() {
        frame = new JFrame("Rune Forge - Combat " + getVersion());
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        targetField = addField(form, c, 0, "Target NPC", "Sand Crab");
        hpField = addField(form, c, 1, "Eat at HP", "20");
        foodField = addField(form, c, 2, "Food", "Lobster");

        c.gridx = 0;
        c.gridy = 3;
        c.weightx = 0;
        form.add(new JLabel("Loot"), c);

        lootArea = new JTextArea(6, 24);
        lootArea.setLineWrap(true);
        lootArea.setWrapStyleWord(true);

        c.gridx = 1;
        c.weightx = 1;
        form.add(new JScrollPane(lootArea), c);

        buryBonesCheck = new JCheckBox("Bury bones");
        c.gridx = 1;
        c.gridy = 4;
        form.add(buryBonesCheck, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton startButton = new JButton("Start");
        JButton stopButton = new JButton("Stop");
        buttons.add(startButton);
        buttons.add(stopButton);

        statusLabel = new JLabel("Loaded");

        JPanel footer = new JPanel(new BorderLayout());
        footer.add(buttons, BorderLayout.WEST);
        footer.add(statusLabel, BorderLayout.EAST);

        startButton.addActionListener(e -> {
            captureConfigOnEdt();
            onStart();
        });
        stopButton.addActionListener(e -> onStop());

        DocumentListener configListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { captureConfigOnEdt(); }
            @Override public void removeUpdate(DocumentEvent e) { captureConfigOnEdt(); }
            @Override public void changedUpdate(DocumentEvent e) { captureConfigOnEdt(); }
        };

        targetField.getDocument().addDocumentListener(configListener);
        hpField.getDocument().addDocumentListener(configListener);
        foodField.getDocument().addDocumentListener(configListener);
        lootArea.getDocument().addDocumentListener(configListener);
        buryBonesCheck.addItemListener(e -> captureConfigOnEdt());

        frame.setLayout(new BorderLayout());
        frame.add(form, BorderLayout.CENTER);
        frame.add(footer, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);

        captureConfigOnEdt();
    }

    private JTextField addField(
        JPanel panel,
        GridBagConstraints c,
        int row,
        String label,
        String initialValue) {

        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        panel.add(new JLabel(label), c);

        JTextField field = new JTextField(initialValue, 20);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(field, c);
        return field;
    }

    private void captureConfigOnEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::captureConfigOnEdt);
            return;
        }

        config = CombatConfig.from(
            targetField == null ? "" : targetField.getText(),
            hpField == null ? "" : hpField.getText(),
            foodField == null ? "" : foodField.getText(),
            lootArea == null ? "" : lootArea.getText(),
            buryBonesCheck != null && buryBonesCheck.isSelected());
    }

    private void requestTickSafely() {
        RuneForgeContext current = context;
        if (!running || current == null) {
            return;
        }

        try {
            current.invokeOnClientThread(() -> {
                try {
                    tick();
                } catch (Throwable t) {
                    handleSchedulerFailure(t);
                }
            });
        } catch (Throwable t) {
            handleSchedulerFailure(t);
        }
    }

    private synchronized void handleSchedulerFailure(Throwable t) {
        running = false;
        CombatDiagnostics log = diagnostics;
        if (log != null) log.error("LOOP_ERROR", t);
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }

        if (context != null) {
            context.log("Combat loop stopped after error: "
                + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        setStatus("Stopped after error");
    }

    private synchronized void tick() {
        if (!running || System.currentTimeMillis() - lastActionAt < ACTION_COOLDOWN_MS) {
            return;
        }

        RuneForgeContext current = context;
        if (current == null) return;

        RuneForgeClient client = current.getGameClient();
        RuneForgeClient.PlayerRef player = client.localPlayer();
        CombatConfig currentConfig = config;
        diagnosticSnapshot(client, player, currentConfig);

        if (player == null) {
            decision("NO_PLAYER", "Waiting for player");
            return;
        }

        if (client.currentHitpoints() > 0
            && client.currentHitpoints() <= currentConfig.eatAtHp
            && inventoryAction(client, currentConfig.foodName, "Eat")) {
            markAction("Eating " + currentConfig.foodName);
            return;
        }

        if (player.interacting()) {
            decision("INTERACTING", "Loot and bury skipped: player interaction is active");
            setStatus("In combat");
            return;
        }

        RuneForgeClient.GroundItemRef loot = findNearestLoot(client, currentConfig);
        if (loot != null) {
            beginAttempt(client, "Take", groundDetail(loot) + " opcode=GROUND_ITEM_FIRST_OPTION");
            boolean accepted = client.take(loot);
            debug("MENU_TRACE", "id=" + actionSequence + " " + client.lastMenuDispatchTrace());
            debug("DISPATCH_RETURN", "id=" + actionSequence + " returned=" + accepted + " (not server confirmation)");
            if (accepted) {
                markAction("Take requested: " + loot.name);
                return;
            }
        } else {
            decision("NO_LOOT", currentConfig.lootNames.isEmpty() ? "Loot list empty" : "No matching ground item within 12 tiles");
        }

        if (currentConfig.buryBones) {
            RuneForgeClient.InventoryItemRef bones = firstInventoryAction(client, null, "Bury");
            if (bones != null && dispatchInventory(client, bones, "Bury")) {
                markAction("Bury requested: " + bones.name);
                return;
            }
            decision("NO_BURY_ITEM", "No inventory item exposing Bury was dispatched");
        }

        if (!currentConfig.buryBones) decision("BURY_DISABLED", "Bury bones checkbox is off");

        RuneForgeClient.NpcRef target = findNearestTarget(client, player, currentConfig.targetName);
        if (target != null && client.attack(target)) {
            markAction("Attack requested: " + target.name());
        } else {
            setStatus("Waiting for " + currentConfig.targetName);
        }
    }

    private boolean inventoryAction(
        RuneForgeClient client,
        String name,
        String action) {

        RuneForgeClient.InventoryItemRef item =
            firstInventoryAction(client, name, action);

        if (item == null) {
            decision("INVENTORY_MISS", "wanted=" + name + " action=" + action);
            return false;
        }
        return dispatchInventory(client, item, action);
    }

    private RuneForgeClient.InventoryItemRef firstInventoryAction(
        RuneForgeClient client,
        String wantedName,
        String wantedAction) {

        for (RuneForgeClient.InventoryItemRef item : client.inventoryItems()) {
            boolean nameMatches = wantedName == null
                || item.name.equalsIgnoreCase(wantedName);
            if (nameMatches && item.hasAction(wantedAction)) {
                return item;
            }
        }

        return null;
    }

    private RuneForgeClient.NpcRef findNearestTarget(
        RuneForgeClient client,
        RuneForgeClient.PlayerRef player,
        String wantedName) {

        if (wantedName == null || wantedName.isBlank()) {
            return null;
        }

        RuneForgeClient.NpcRef best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (RuneForgeClient.NpcRef npc : client.npcs()) {
            if (npc.dead() || !npc.name().equalsIgnoreCase(wantedName)) {
                continue;
            }

            int distance = npc.distanceTo(player);
            if (distance >= 0 && distance < bestDistance) {
                best = npc;
                bestDistance = distance;
            }
        }

        return best;
    }

    private RuneForgeClient.GroundItemRef findNearestLoot(
        RuneForgeClient client,
        CombatConfig currentConfig) {

        if (currentConfig.lootNames.isEmpty()) {
            return null;
        }

        RuneForgeClient.GroundItemRef best = null;

        for (RuneForgeClient.GroundItemRef item :
            client.groundItems(MAX_LOOT_DISTANCE)) {

            if (!currentConfig.lootNames.contains(
                item.name.toLowerCase(Locale.ROOT))) {
                continue;
            }

            if (best == null || item.distance < best.distance) {
                best = item;
            }
        }

        return best;
    }

    private boolean dispatchInventory(RuneForgeClient client, RuneForgeClient.InventoryItemRef item, String action) {
        int index = RuneForgeClient.actionIndex(item.actions, action);
        beginAttempt(client, action, inventoryDetail(item) + " actionIndex=" + index
            + " opcode=" + (index < 0 ? "NONE" : RuneForgeClient.actionName("ITEM", index)));
        boolean accepted = client.inventoryAction(item, action);
        debug("DISPATCH_RETURN", "id=" + actionSequence + " returned=" + accepted + " (not server confirmation)");
        return accepted;
    }

    private void beginAttempt(RuneForgeClient client, String action, String details) {
        if (pendingAction != null) observePending(client, "before next request");
        pendingAction = action;
        pendingAt = System.currentTimeMillis();
        pendingInventory = inventorySnapshot(client);
        debug("ACTION_REQUEST", "id=" + (++actionSequence) + " action=" + action + " " + details
            + " inventoryBefore=" + pendingInventory);
    }

    private void observePending(RuneForgeClient client, String reason) {
        if (pendingAction == null) return;
        String after = inventorySnapshot(client);
        debug("ACTION_OBSERVATION", "id=" + actionSequence + " action=" + pendingAction
            + " elapsedMs=" + (System.currentTimeMillis() - pendingAt) + " reason=" + reason
            + " inventorySlotsChanged=" + !after.equals(pendingInventory)
            + " inventoryAfter=" + after + " (slot changes are evidence, not proof of action success)");
        pendingAction = null;
    }

    private void diagnosticSnapshot(RuneForgeClient client, RuneForgeClient.PlayerRef player, CombatConfig c) {
        String settings = "target=" + c.targetName + " eatAt=" + c.eatAtHp + " food=" + c.foodName
            + " loot=" + c.lootNames + " bury=" + c.buryBones;
        if (!settings.equals(lastConfig)) {
            debug("CONFIG", settings);
            lastConfig = settings;
        }
        long now = System.currentTimeMillis();
        if (pendingAction != null && now - pendingAt >= 2500) observePending(client, "follow-up");
        if (now - lastSnapshotAt < 5000) return;
        lastSnapshotAt = now;
        long started = System.nanoTime();
        try {
            debug("PLAYER", "present=" + (player != null) + " interacting=" + (player != null && player.interacting())
                + " location=" + (player == null ? "none" : player.worldLocation()) + " hp=" + client.currentHitpoints()
                + " gameState=" + probe(context.getRawClient(), "getGameState")
                + " plane=" + probe(context.getRawClient(), "getPlane"));
            Object rawPlayer = probe(context.getRawClient(), "getLocalPlayer");
            Object interaction = rawPlayer == null ? null : probe(rawPlayer, "getInteracting");
            debug("PLAYER_DETAIL", "animation=" + (rawPlayer == null ? "none" : probe(rawPlayer, "getAnimation"))
                + " interactionType=" + (interaction == null ? "none" : interaction.getClass().getName())
                + " interactionName=" + (interaction == null ? "none" : probe(interaction, "getName"))
                + " interactionDead=" + (interaction == null ? "none" : probe(interaction, "isDead")));
            Object scene = probe(context.getRawClient(), "getScene");
            Object tiles = scene == null ? null : probe(scene, "getTiles");
            debug("SCENE", "sceneType=" + (scene == null ? "none" : scene.getClass().getName())
                + " tiles=" + (tiles == null ? "null" : tiles.getClass().isArray()
                    ? "planes:" + java.lang.reflect.Array.getLength(tiles) : tiles));
            debug("INVENTORY", inventorySnapshot(client));
            java.util.List<RuneForgeClient.GroundItemRef> items = client.groundItems(MAX_LOOT_DISTANCE);
            StringBuilder ground = new StringBuilder("radius=12 count=" + items.size());
            int shown = 0;
            for (RuneForgeClient.GroundItemRef item : items) {
                if (shown++ >= 100) { ground.append(" [remaining items omitted]"); break; }
                ground.append(" [").append(groundDetail(item)).append(" match=")
                    .append(c.lootNames.contains(item.name.toLowerCase(Locale.ROOT))).append(']');
            }
            debug("GROUND", ground.toString());
            debug("GATES", "interacting=" + (player != null && player.interacting())
                + " lootEnabled=" + !c.lootNames.isEmpty() + " buryEnabled=" + c.buryBones
                + " priority=eat,interaction-check,loot,bury,attack");
            debug("SNAPSHOT_END", "durationMs=" + (System.nanoTime() - started) / 1_000_000);
        } catch (Throwable e) {
            CombatDiagnostics log = diagnostics;
            if (log != null) log.error("SNAPSHOT_ERROR", e);
        }
    }

    private static Object probe(Object object, String method) {
        try {
            java.lang.reflect.Method accessor = object.getClass().getMethod(method);
            accessor.setAccessible(true);
            return accessor.invoke(object);
        }
        catch (Exception e) { return "unavailable:" + e; }
    }

    private String inventorySnapshot(RuneForgeClient client) {
        java.util.List<RuneForgeClient.InventoryItemRef> items;
        try { items = client.inventoryItems(); }
        catch (Throwable e) {
            CombatDiagnostics log = diagnostics;
            if (log != null) log.error("INVENTORY_READ_ERROR", e);
            return "unavailable (see INVENTORY_READ_ERROR)";
        }
        StringBuilder result = new StringBuilder("occupiedSlots=" + items.size());
        for (RuneForgeClient.InventoryItemRef item : items) result.append(" [").append(inventoryDetail(item)).append(']');
        return result.toString();
    }

    private static String inventoryDetail(RuneForgeClient.InventoryItemRef item) {
        return "name=" + item.name + " id=" + item.id + " slot=" + item.slot + " widget=" + item.widgetId
            + " actions=" + java.util.Arrays.toString(item.actions);
    }

    private static String groundDetail(RuneForgeClient.GroundItemRef item) {
        return "name=" + item.name + " id=" + item.id + " scene=" + item.sceneX + "," + item.sceneY + " distance=" + item.distance;
    }

    private void decision(String code, String detail) {
        long now = System.currentTimeMillis();
        Long previous = decisionTimes.get(code);
        if (previous == null || now - previous >= 5000) {
            debug("DECISION", code + " " + detail);
            decisionTimes.put(code, now);
        }
    }

    private void debug(String event, String detail) {
        CombatDiagnostics log = diagnostics;
        if (log != null) log.log(event, detail);
    }

    private void markAction(String description) {
        debug("ACTION_STATUS", description);
        lastActionAt = System.currentTimeMillis();
        setStatus(description);
        if (context != null) {
            context.log(description);
        }
    }

    private void setStatus(String value) {
        SwingUtilities.invokeLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(value);
            }
        });
    }

    private void bringToFront() {
        SwingUtilities.invokeLater(() -> {
            if (frame != null) {
                frame.setVisible(true);
                frame.setState(Frame.NORMAL);
                frame.toFront();
            }
        });
    }

    static final class CombatConfig {
        final String targetName;
        final int eatAtHp;
        final String foodName;
        final Set<String> lootNames;
        final boolean buryBones;

        private CombatConfig(
            String targetName,
            int eatAtHp,
            String foodName,
            Set<String> lootNames,
            boolean buryBones) {

            this.targetName = targetName;
            this.eatAtHp = eatAtHp;
            this.foodName = foodName;
            this.lootNames = lootNames;
            this.buryBones = buryBones;
        }

        static CombatConfig defaults() {
            return from("Sand Crab", "20", "Lobster", "", false);
        }

        static CombatConfig from(
            String targetName,
            String eatAtHp,
            String foodName,
            String lootText,
            boolean buryBones) {

            int hp = 20;
            try {
                hp = Integer.parseInt(eatAtHp == null ? "" : eatAtHp.trim());
            } catch (NumberFormatException ignored) {
            }

            hp = Math.max(1, Math.min(99, hp));

            Set<String> loot = new LinkedHashSet<>();
            if (lootText != null) {
                for (String value : lootText.split("[\\r\\n,]+")) {
                    String trimmed = value.trim();
                    if (!trimmed.isEmpty()) {
                        loot.add(trimmed.toLowerCase(Locale.ROOT));
                    }
                }
            }

            return new CombatConfig(
                targetName == null ? "" : targetName.trim(),
                hp,
                foodName == null ? "" : foodName.trim(),
                Collections.unmodifiableSet(loot),
                buryBones);
        }
    }
}
