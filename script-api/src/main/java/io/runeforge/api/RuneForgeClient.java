package io.runeforge.api;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class RuneForgeClient {
    private final Object client;
    private final ClassLoader runtimeLoader;
    private AloraMenuDispatcher aloraDispatcher;

    public RuneForgeClient(Object client, ClassLoader runtimeLoader) {
        this.client = client;
        this.runtimeLoader = runtimeLoader;
    }

    public Object rawClient() {
        return client;
    }

    public int currentHitpoints() {
        Object skill = Reflection.enumConstant(
            runtimeLoader,
            "net.runelite.api.Skill",
            "HITPOINTS");
        return Reflection.intValue(
            Reflection.invoke(client, "getBoostedSkillLevel", skill),
            -1);
    }

    public PlayerRef localPlayer() {
        Object raw = Reflection.invoke(client, "getLocalPlayer");
        return raw == null ? null : new PlayerRef(raw);
    }

    public List<NpcRef> npcs() {
        List<Object> values = Reflection.asList(Reflection.invoke(client, "getNpcs"));
        List<NpcRef> result = new ArrayList<>(values.size());
        for (Object value : values) {
            if (value != null) {
                result.add(new NpcRef(value));
            }
        }
        return result;
    }

    public List<InventoryItemRef> inventoryItems() {
        Object inventoryEnum = Reflection.enumConstant(
            runtimeLoader,
            "net.runelite.api.InventoryID",
            "INVENTORY");

        Object container = Reflection.invoke(client, "getItemContainer", inventoryEnum);
        if (container == null) {
            return Collections.emptyList();
        }

        Object itemsArray = Reflection.invoke(container, "getItems");
        if (itemsArray == null || !itemsArray.getClass().isArray()) {
            return Collections.emptyList();
        }

        int widgetId = inventoryWidgetId();
        List<InventoryItemRef> result = new ArrayList<>();

        for (int slot = 0; slot < Array.getLength(itemsArray); slot++) {
            Object item = Array.get(itemsArray, slot);
            if (item == null) {
                continue;
            }

            int id = Reflection.intValue(Reflection.invoke(item, "getId"), -1);
            if (id <= 0) {
                continue;
            }

            Object composition = Reflection.invoke(client, "getItemComposition", id);
            String name = stringValue(Reflection.invoke(composition, "getName"));
            String[] actions = stringArray(
                Reflection.invoke(composition, "getInventoryActions"));

            result.add(new InventoryItemRef(id, slot, widgetId, name, actions));
        }

        return result;
    }

    public List<GroundItemRef> groundItems(int maximumDistance) {
        PlayerRef local = localPlayer();
        if (local == null || local.worldLocation() == null) {
            return Collections.emptyList();
        }

        Object scene = Reflection.invoke(client, "getScene");
        if (scene == null) {
            return Collections.emptyList();
        }

        Object tiles = Reflection.invoke(scene, "getTiles");
        if (tiles == null || !tiles.getClass().isArray()) {
            return Collections.emptyList();
        }

        List<GroundItemRef> result = new ArrayList<>();

        int planes = Array.getLength(tiles);
        for (int p = 0; p < planes; p++) {
            Object rows = Array.get(tiles, p);
            if (rows == null || !rows.getClass().isArray()) continue;

            for (int x = 0; x < Array.getLength(rows); x++) {
                Object columns = Array.get(rows, x);
                if (columns == null || !columns.getClass().isArray()) continue;

                for (int y = 0; y < Array.getLength(columns); y++) {
                    Object tile = Array.get(columns, y);
                    if (tile == null) continue;

                    Object tileLocation = Reflection.invoke(tile, "getWorldLocation");
                    int distance = distance(local.worldLocation(), tileLocation);
                    if (distance < 0 || distance > maximumDistance) continue;

                    Object scenePoint = Reflection.invoke(tile, "getSceneLocation");
                    int sceneX = Reflection.intValue(Reflection.invoke(scenePoint, "getX"), -1);
                    int sceneY = Reflection.intValue(Reflection.invoke(scenePoint, "getY"), -1);

                    for (Object item : tileItems(tile)) {

                        int id = Reflection.intValue(Reflection.invoke(item, "getId"), -1);
                        if (id <= 0) continue;

                        Object composition = Reflection.invoke(client, "getItemComposition", id);
                        String name = stringValue(Reflection.invoke(composition, "getName"));

                        result.add(new GroundItemRef(
                            id, name, sceneX, sceneY, distance));
                    }
                }
            }
        }

        return result;
    }

    public boolean attack(NpcRef npc) {
        if (npc == null) return false;

        Object composition = Reflection.invoke(npc.raw, "getComposition");
        String[] actions = stringArray(
            composition == null ? null : Reflection.invoke(composition, "getActions"));
        int actionIndex = actionIndex(actions, "Attack");
        if (actionIndex < 0 || actionIndex > 4) return false;

        return menuAction(
            actionName("NPC", actionIndex),
            0,
            0,
            npc.index(),
            0,
            "Attack",
            npc.name());
    }

    public boolean take(GroundItemRef item) {
        if (item == null || item.sceneX < 0 || item.sceneY < 0) return false;

        return menuAction(
            "GROUND_ITEM_THIRD_OPTION",
            item.sceneX,
            item.sceneY,
            item.id,
            0,
            "Take",
            item.name);
    }

    private List<Object> tileItems(Object tile) {
        List<Object> items = Reflection.asList(Reflection.invoke(tile, "getGroundItems"));
        if (!items.isEmpty()) return items;
        // Some clients expose a stub getGroundItems() but populate the visible item layer.
        Method layerMethod = Reflection.findCompatibleMethod(tile.getClass(), "getItemLayer");
        if (layerMethod == null) return items;
        Object layer = Reflection.invoke(tile, "getItemLayer");
        if (layer == null) return items;
        List<Object> visible = new ArrayList<>();
        java.util.Set<Object> seen = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (String accessor : new String[]{"getBottom", "getMiddle", "getTop"}) {
            Object renderable = Reflection.invoke(layer, accessor);
            if (renderable != null && seen.add(renderable)
                && Reflection.findCompatibleMethod(renderable.getClass(), "getId") != null) {
                visible.add(renderable);
            }
        }
        return visible;
    }

    public boolean inventoryAction(InventoryItemRef item, String option) {
        if (item == null || option == null) return false;

        int actionIndex = actionIndex(item.actions, option);
        if (actionIndex < 0 || actionIndex > 4) return false;

        return menuAction(
            actionName("ITEM", actionIndex),
            item.slot,
            item.widgetId,
            item.id,
            item.id,
            option,
            item.name);
    }

    public static int actionIndex(String[] actions, String wanted) {
        if (actions == null || wanted == null) {
            return -1;
        }

        for (int i = 0; i < actions.length; i++) {
            String action = actions[i];
            if (action != null && action.equalsIgnoreCase(wanted)) {
                return i;
            }
        }

        return -1;
    }

    public static String actionName(String family, int zeroBasedIndex) {
        if (zeroBasedIndex < 0 || zeroBasedIndex > 4) {
            throw new IllegalArgumentException("Action index must be between 0 and 4.");
        }

        String ordinal;
        switch (zeroBasedIndex) {
            case 0: ordinal = "FIRST"; break;
            case 1: ordinal = "SECOND"; break;
            case 2: ordinal = "THIRD"; break;
            case 3: ordinal = "FOURTH"; break;
            case 4: ordinal = "FIFTH"; break;
            default: throw new AssertionError();
        }

        return family.toUpperCase(Locale.ROOT) + "_" + ordinal + "_OPTION";
    }

    private boolean menuAction(
        String actionName,
        int param0,
        int param1,
        int identifier,
        int itemId,
        String option,
        String target) {

        Object action = Reflection.enumConstant(
            runtimeLoader,
            "net.runelite.api.MenuAction",
            actionName);

        if (client.getClass().getName().equals("com.alora.Alora")) {
            if (aloraDispatcher == null) aloraDispatcher = new AloraMenuDispatcher(runtimeLoader);
            aloraDispatcher.invoke(client, action, param0, param1, identifier, option, target);
            return true;
        }

        Method method = Reflection.findCompatibleMethod(
            client.getClass(),
            "menuAction",
            param0,
            param1,
            action,
            identifier,
            itemId,
            option,
            target);

        if (method == null) {
            throw new IllegalStateException(
                "The client does not expose the required menuAction(...) method.");
        }

        try {
            method.setAccessible(true);
            method.invoke(
                client,
                param0,
                param1,
                action,
                identifier,
                itemId,
                option,
                target);
            return true;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "Client menu action failed: " + option + " " + target, e);
        }
    }

    private int inventoryWidgetId() {
        try {
            Object widgetInfo = Reflection.enumConstant(
                runtimeLoader,
                "net.runelite.api.widgets.WidgetInfo",
                "INVENTORY");
            Object widget = Reflection.invoke(client, "getWidget", widgetInfo);
            return Reflection.intValue(
                widget == null ? null : Reflection.invoke(widget, "getId"),
                0);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static int distance(Object a, Object b) {
        if (a == null || b == null) return -1;
        return Reflection.intValue(Reflection.invoke(a, "distanceTo", b), -1);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String[] stringArray(Object value) {
        if (value == null || !value.getClass().isArray()) {
            return new String[0];
        }

        int length = Array.getLength(value);
        String[] result = new String[length];
        for (int i = 0; i < length; i++) {
            Object entry = Array.get(value, i);
            result[i] = entry == null ? null : String.valueOf(entry);
        }
        return result;
    }

    public static class ActorRef {
        final Object raw;

        ActorRef(Object raw) {
            this.raw = raw;
        }

        public String name() {
            Object value = Reflection.invoke(raw, "getName");
            return value == null ? "" : String.valueOf(value);
        }

        public Object worldLocation() {
            return Reflection.invoke(raw, "getWorldLocation");
        }

        public boolean interacting() {
            return Reflection.invoke(raw, "getInteracting") != null;
        }
    }

    public static final class PlayerRef extends ActorRef {
        PlayerRef(Object raw) {
            super(raw);
        }
    }

    public static final class NpcRef extends ActorRef {
        NpcRef(Object raw) {
            super(raw);
        }

        public int index() {
            return Reflection.intValue(Reflection.invoke(raw, "getIndex"), -1);
        }

        public boolean dead() {
            return Reflection.boolValue(Reflection.invoke(raw, "isDead"));
        }

        public int distanceTo(PlayerRef player) {
            return distance(worldLocation(), player == null ? null : player.worldLocation());
        }
    }

    public static final class InventoryItemRef {
        public final int id;
        public final int slot;
        public final int widgetId;
        public final String name;
        public final String[] actions;

        InventoryItemRef(
            int id,
            int slot,
            int widgetId,
            String name,
            String[] actions) {

            this.id = id;
            this.slot = slot;
            this.widgetId = widgetId;
            this.name = name;
            this.actions = actions;
        }

        public boolean hasAction(String option) {
            return actionIndex(actions, option) >= 0;
        }
    }

    public static final class GroundItemRef {
        public final int id;
        public final String name;
        public final int sceneX;
        public final int sceneY;
        public final int distance;

        GroundItemRef(int id, String name, int sceneX, int sceneY, int distance) {
            this.id = id;
            this.name = name;
            this.sceneX = sceneX;
            this.sceneY = sceneY;
            this.distance = distance;
        }
    }
}
