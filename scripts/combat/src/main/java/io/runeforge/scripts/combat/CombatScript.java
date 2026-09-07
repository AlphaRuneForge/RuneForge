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
        return "1.0.5";
    }

    @Override
    public void onLoad(RuneForgeContext context) {
        this.context = context;
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
        context = null;
    }

    private void createUi() {
        frame = new JFrame("Rune Forge - Combat 1.0.4");
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

    private void tick() {
        if (!running || System.currentTimeMillis() - lastActionAt < ACTION_COOLDOWN_MS) {
            return;
        }

        RuneForgeContext current = context;
        if (current == null) return;

        RuneForgeClient client = current.getGameClient();
        RuneForgeClient.PlayerRef player = client.localPlayer();
        CombatConfig currentConfig = config;

        if (player == null) {
            setStatus("Waiting for player");
            return;
        }

        if (client.currentHitpoints() > 0
            && client.currentHitpoints() <= currentConfig.eatAtHp
            && inventoryAction(client, currentConfig.foodName, "Eat")) {
            markAction("Eating " + currentConfig.foodName);
            return;
        }

        if (player.interacting()) {
            setStatus("In combat");
            return;
        }

        RuneForgeClient.GroundItemRef loot = findNearestLoot(client, currentConfig);
        if (loot != null && client.take(loot)) {
            markAction("Taking " + loot.name);
            return;
        }

        if (currentConfig.buryBones) {
            RuneForgeClient.InventoryItemRef bones = firstInventoryAction(client, null, "Bury");
            if (bones != null && client.inventoryAction(bones, "Bury")) {
                markAction("Burying " + bones.name);
                return;
            }
        }

        RuneForgeClient.NpcRef target = findNearestTarget(client, player, currentConfig.targetName);
        if (target != null && client.attack(target)) {
            markAction("Attacking " + target.name());
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

        return item != null && client.inventoryAction(item, action);
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

    private void markAction(String description) {
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
