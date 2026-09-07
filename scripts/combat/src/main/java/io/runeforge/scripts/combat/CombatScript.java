package io.runeforge.scripts.combat;

import io.runeforge.api.RuneForgeContext;
import io.runeforge.api.RuneForgeScript;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetItem;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;

public final class CombatScript implements RuneForgeScript {
    private static final int MAX_LOOT_DISTANCE = 12;
    private static final long LOOP_DELAY_MS = 350L;
    private static final long ACTION_COOLDOWN_MS = 800L;

    private RuneForgeContext context;
    private ScheduledExecutorService scheduler;
    private volatile boolean running;
    private volatile long lastActionAt;

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
        return "1.0.0";
    }

    @Override
    public void onLoad(RuneForgeContext context) {
        this.context = context;
        SwingUtilities.invokeLater(this::createUi);
        context.log("Combat script loaded.");
    }

    @Override
    public synchronized void onStart() {
        if (running) {
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
            this::requestTick,
            0L,
            LOOP_DELAY_MS,
            TimeUnit.MILLISECONDS);

        setStatus("Running");
        context.log("Combat script started.");
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
        onStop();

        SwingUtilities.invokeLater(() -> {
            if (frame != null) {
                frame.dispose();
                frame = null;
            }
        });

        context.log("Combat script unloaded.");
        context = null;
    }

    private void createUi() {
        frame = new JFrame("Rune Forge - Combat");
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

        startButton.addActionListener(e -> onStart());
        stopButton.addActionListener(e -> onStop());

        frame.setLayout(new BorderLayout());
        frame.add(form, BorderLayout.CENTER);
        frame.add(footer, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
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

    private void requestTick() {
        if (!running || context == null) {
            return;
        }

        context.getClientThread().invokeLater(this::tick);
    }

    private void tick() {
        if (!running || System.currentTimeMillis() - lastActionAt < ACTION_COOLDOWN_MS) {
            return;
        }

        Client client = context.getClient();
        Player player = client.getLocalPlayer();

        if (player == null) {
            return;
        }

        if (shouldEat(client, player) && clickInventoryItem(foodName())) {
            return;
        }

        if (player.getInteracting() != null) {
            setStatus("In combat");
            return;
        }

        GroundItemRef loot = findNearestLoot(client, player.getWorldLocation());
        if (loot != null && clickGroundItem(client, loot)) {
            return;
        }

        if (buryBonesCheck != null
            && buryBonesCheck.isSelected()
            && clickFirstBuryableBone(client)) {
            return;
        }

        NPC target = findNearestTarget(client, player.getWorldLocation(), targetName());
        if (target != null && clickNpc(client, target)) {
            setStatus("Attacking " + target.getName());
        } else {
            setStatus("Waiting for " + targetName());
        }
    }

    private boolean shouldEat(Client client, Player player) {
        int threshold = parseInt(hpField, 20);
        if (threshold <= 0) {
            return false;
        }

        int ratio = player.getHealthRatio();
        int scale = player.getHealthScale();

        if (ratio < 0 || scale <= 0) {
            return false;
        }

        int hitpoints = (int) Math.ceil((double) ratio * scale / scale);
        return hitpoints <= threshold;
    }

    private boolean clickInventoryItem(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return false;
        }

        Client client = context.getClient();
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);

        if (inventory == null || inventoryWidget == null) {
            return false;
        }

        Item[] items = inventory.getItems();
        for (int slot = 0; slot < items.length; slot++) {
            Item item = items[slot];
            if (item == null || item.getId() <= 0) {
                continue;
            }

            ItemComposition composition = client.getItemComposition(item.getId());
            if (composition == null
                || composition.getName() == null
                || !composition.getName().equalsIgnoreCase(itemName)) {
                continue;
            }

            WidgetItem widgetItem = inventoryWidget.getWidgetItem(slot);
            if (widgetItem == null) {
                continue;
            }

            return click(widgetItem.getCanvasBounds(), "inventory " + itemName);
        }

        return false;
    }

    private boolean clickFirstBuryableBone(Client client) {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);

        if (inventory == null || inventoryWidget == null) {
            return false;
        }

        Item[] items = inventory.getItems();
        for (int slot = 0; slot < items.length; slot++) {
            Item item = items[slot];
            if (item == null || item.getId() <= 0) {
                continue;
            }

            ItemComposition composition = client.getItemComposition(item.getId());
            if (!hasInventoryAction(composition, "Bury")) {
                continue;
            }

            WidgetItem widgetItem = inventoryWidget.getWidgetItem(slot);
            if (widgetItem == null) {
                continue;
            }

            return click(widgetItem.getCanvasBounds(), "bury " + composition.getName());
        }

        return false;
    }

    private boolean hasInventoryAction(ItemComposition composition, String wanted) {
        if (composition == null || composition.getInventoryActions() == null) {
            return false;
        }

        for (String action : composition.getInventoryActions()) {
            if (action != null && action.equalsIgnoreCase(wanted)) {
                return true;
            }
        }

        return false;
    }

    private NPC findNearestTarget(Client client, WorldPoint origin, String wantedName) {
        if (wantedName == null || wantedName.isBlank()) {
            return null;
        }

        NPC best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (NPC npc : client.getNpcs()) {
            if (npc == null || npc.isDead() || npc.getName() == null) {
                continue;
            }

            if (!npc.getName().equalsIgnoreCase(wantedName)) {
                continue;
            }

            WorldPoint location = npc.getWorldLocation();
            if (location == null) {
                continue;
            }

            int distance = origin.distanceTo(location);
            if (distance < bestDistance) {
                best = npc;
                bestDistance = distance;
            }
        }

        return best;
    }

    private GroundItemRef findNearestLoot(Client client, WorldPoint origin) {
        Set<String> wanted = configuredLoot();
        if (wanted.isEmpty()) {
            return null;
        }

        Tile[][][] tiles = client.getScene().getTiles();
        GroundItemRef best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (Tile[][] plane : tiles) {
            if (plane == null) {
                continue;
            }

            for (Tile[] row : plane) {
                if (row == null) {
                    continue;
                }

                for (Tile tile : row) {
                    if (tile == null || tile.getGroundItems() == null) {
                        continue;
                    }

                    WorldPoint location = tile.getWorldLocation();
                    int distance = origin.distanceTo(location);

                    if (distance > MAX_LOOT_DISTANCE || distance >= bestDistance) {
                        continue;
                    }

                    for (TileItem item : tile.getGroundItems()) {
                        ItemComposition composition = client.getItemComposition(item.getId());
                        String name = composition == null ? null : composition.getName();

                        if (name != null && wanted.contains(name.toLowerCase(Locale.ROOT))) {
                            best = new GroundItemRef(tile, item, name);
                            bestDistance = distance;
                            break;
                        }
                    }
                }
            }
        }

        return best;
    }

    private boolean clickNpc(Client client, NPC npc) {
        Shape hull = npc.getConvexHull();
        if (hull == null) {
            return false;
        }

        Rectangle bounds = hull.getBounds();
        return click(bounds, "npc " + npc.getName());
    }

    private boolean clickGroundItem(Client client, GroundItemRef item) {
        net.runelite.api.Point point = Perspective.localToCanvas(
            client,
            item.tile.getLocalLocation(),
            client.getPlane());

        if (point == null) {
            return false;
        }

        Rectangle target = new Rectangle(point.getX() - 4, point.getY() - 4, 8, 8);
        return click(target, "loot " + item.name);
    }

    private boolean click(Rectangle bounds, String label) {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return false;
        }

        int x = bounds.x + bounds.width / 2;
        int y = bounds.y + bounds.height / 2;

        Canvas canvas = context.getClient().getCanvas();
        if (canvas == null || x < 0 || y < 0 || x >= canvas.getWidth() || y >= canvas.getHeight()) {
            return false;
        }

        long now = System.currentTimeMillis();

        canvas.dispatchEvent(new MouseEvent(
            canvas, MouseEvent.MOUSE_MOVED, now, 0, x, y, 0, false, MouseEvent.NOBUTTON));
        canvas.dispatchEvent(new MouseEvent(
            canvas, MouseEvent.MOUSE_PRESSED, now + 10, 0, x, y, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(
            canvas, MouseEvent.MOUSE_RELEASED, now + 35, 0, x, y, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(
            canvas, MouseEvent.MOUSE_CLICKED, now + 40, 0, x, y, 1, false, MouseEvent.BUTTON1));

        lastActionAt = now;
        context.log("Clicked " + label);
        return true;
    }

    private Set<String> configuredLoot() {
        if (lootArea == null) {
            return Collections.emptySet();
        }

        Set<String> items = new LinkedHashSet<>();
        String[] lines = lootArea.getText().split("[\\r\\n,]+");

        for (String line : lines) {
            String value = line.trim();
            if (!value.isEmpty()) {
                items.add(value.toLowerCase(Locale.ROOT));
            }
        }

        return items;
    }

    private String targetName() {
        return text(targetField);
    }

    private String foodName() {
        return text(foodField);
    }

    private static String text(JTextField field) {
        return field == null ? "" : field.getText().trim();
    }

    private static int parseInt(JTextField field, int fallback) {
        try {
            return Integer.parseInt(text(field));
        } catch (NumberFormatException e) {
            return fallback;
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

    private static final class GroundItemRef {
        private final Tile tile;
        private final TileItem item;
        private final String name;

        private GroundItemRef(Tile tile, TileItem item, String name) {
            this.tile = tile;
            this.item = item;
            this.name = name;
        }
    }
}
