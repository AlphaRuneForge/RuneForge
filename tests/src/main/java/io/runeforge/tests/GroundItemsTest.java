package io.runeforge.tests;

import io.runeforge.api.RuneForgeClient;
import java.util.Collections;
import java.util.List;

public final class GroundItemsTest {
    public static void main(String[] args) {
        FakeClient raw = new FakeClient();
        RuneForgeClient client = new RuneForgeClient(raw, GroundItemsTest.class.getClassLoader());
        List<RuneForgeClient.GroundItemRef> items = client.groundItems(12);
        if (items.size() != 1 || items.get(0).id != 526 || !items.get(0).name.equals("Bones")
            || items.get(0).sceneX != 3 || items.get(0).sceneY != 4) {
            throw new AssertionError("Empty ground API must fall back to layer without duplicates");
        }
        raw.scene.tile.items = Collections.singletonList(new Item(995));
        if (client.groundItems(12).get(0).id != 995) throw new AssertionError("Prefer complete ground list");
        raw.scene.tile.point.distance = 13;
        if (!client.groundItems(12).isEmpty()) throw new AssertionError("Out-of-range item included");
        raw.scene.tile.point.distance = 1;
        raw.scene.tile.items = Collections.emptyList();
        raw.scene.tile.layer = null;
        if (!client.groundItems(12).isEmpty()) throw new AssertionError("Empty tile produced loot");
        System.out.println("Ground-item fallback, deduplication, coordinates, range and empty tiles passed.");
    }
    public static void verifyTake(ClassLoader runtime) {
        FakeClient raw = new FakeClient();
        RuneForgeClient client = new RuneForgeClient(raw, runtime);
        if (!client.take(client.groundItems(12).get(0))
            || !"GROUND_ITEM_THIRD_OPTION".equals(String.valueOf(raw.action))
            || raw.param0 != 3 || raw.param1 != 4 || raw.identifier != 526
            || !"Take".equals(raw.option)) {
            throw new AssertionError("Pickup must dispatch third option with scene coordinates and item ID");
        }
        System.out.println("Pickup dispatch uses the installed client's third-option enum and correct parameters.");
    }
    public static final class FakeClient {
        Object action;
        int param0, param1, identifier;
        String option;
        public void menuAction(int p0, int p1, Object opcode, int id, int itemId, String option, String target) {
            this.param0 = p0;
            this.param1 = p1;
            this.action = opcode;
            this.identifier = id;
            this.option = option;
        }
        final Scene scene = new Scene();
        public Player getLocalPlayer() { return new Player(); }
        public Scene getScene() { return scene; }
        public Composition getItemComposition(int id) { return new Composition(id); }
    }
    public static final class Player { public Point getWorldLocation() { return new Point(); } }
    public static final class Point {
        int distance = 1;
        public int distanceTo(Point other) { return other.distance; }
        public int getX() { return 3; }
        public int getY() { return 4; }
    }
    public static final class Scene {
        final Tile tile = new Tile();
        public Tile[][][] getTiles() { return new Tile[][][]{{{tile}}}; }
    }
    public static final class Tile {
        final Point point = new Point();
        List<Item> items = Collections.emptyList();
        Layer layer = new Layer();
        public Point getWorldLocation() { return point; }
        public Point getSceneLocation() { return point; }
        public List<Item> getGroundItems() { return items; }
        public Layer getItemLayer() { return layer; }
    }
    public static final class Layer {
        final Item bones = new Item(526);
        public Item getBottom() { return bones; }
        public Item getMiddle() { return null; }
        public Item getTop() { return bones; }
    }
    public static final class Item {
        final int id;
        Item(int id) { this.id = id; }
        public int getId() { return id; }
    }
    public static final class Composition {
        final int id;
        Composition(int id) { this.id = id; }
        public String getName() { return id == 526 ? "Bones" : "Coins"; }
    }
}
