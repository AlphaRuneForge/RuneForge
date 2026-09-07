package io.runeforge.tests;

import io.runeforge.api.RuneForgeClient;
import java.util.Arrays;

public final class RuneForgeSelfTest {
    private RuneForgeSelfTest() {}

    public static void main(String[] args) {
        testActionMapping();
        testActionIndex();
        testMockMenuParameterOrder();
        testInventoryActionSelection();
        testNpcAttackSelection();
        testGroundItemTakeSelection();
        System.out.println("Rune Forge self-tests passed.");
    }

    private static void testActionMapping() {
        assertEquals("NPC_FIRST_OPTION", RuneForgeClient.actionName("NPC", 0));
        assertEquals("NPC_SECOND_OPTION", RuneForgeClient.actionName("NPC", 1));
        assertEquals("ITEM_FIFTH_OPTION", RuneForgeClient.actionName("ITEM", 4));
    }

    private static void testActionIndex() {
        String[] actions = {"Use", "Eat", null, "Drop", "Examine"};
        assertEquals(1, RuneForgeClient.actionIndex(actions, "eat"));
        assertEquals(3, RuneForgeClient.actionIndex(actions, "DROP"));
        assertEquals(-1, RuneForgeClient.actionIndex(actions, "Bury"));
    }

    private static void testMockMenuParameterOrder() {
        MockMenuSink sink = new MockMenuSink();
        sink.menuAction(13, 9764864, "ITEM_FIRST_OPTION", 379, 379, "Eat", "Lobster");
        assertEquals(13, sink.param0);
        assertEquals(9764864, sink.param1);
        assertEquals(379, sink.identifier);
        assertEquals(379, sink.itemId);
        assertEquals("Eat", sink.option);
        assertEquals("Lobster", sink.target);
    }

    private static void testInventoryActionSelection() {
        assertEquals(0, RuneForgeClient.actionIndex(new String[]{"Eat","Use","Drop"}, "Eat"));
        assertEquals("ITEM_FIRST_OPTION", RuneForgeClient.actionName("ITEM", 0));
        assertEquals(0, RuneForgeClient.actionIndex(new String[]{"Bury","Use","Drop"}, "Bury"));
    }

    private static void testNpcAttackSelection() {
        String[] actions = {"Talk-to", "Attack", "Examine"};
        int slot = RuneForgeClient.actionIndex(actions, "Attack");
        assertEquals(1, slot);
        assertEquals("NPC_SECOND_OPTION", RuneForgeClient.actionName("NPC", slot));
    }

    private static void testGroundItemTakeSelection() {
        assertEquals("GROUND_ITEM_FIRST_OPTION",
            RuneForgeClient.actionName("GROUND_ITEM", 0));
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }

    private static final class MockMenuSink {
        int param0;
        int param1;
        Object action;
        int identifier;
        int itemId;
        String option;
        String target;

        void menuAction(int param0, int param1, Object action, int identifier,
                        int itemId, String option, String target) {
            this.param0 = param0;
            this.param1 = param1;
            this.action = action;
            this.identifier = identifier;
            this.itemId = itemId;
            this.option = option;
            this.target = target;
        }
    }
}
