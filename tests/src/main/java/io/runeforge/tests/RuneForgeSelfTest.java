package io.runeforge.tests;

import io.runeforge.api.RuneForgeClient;

public final class RuneForgeSelfTest {
    private RuneForgeSelfTest() {
    }

    public static void main(String[] args) {
        assertEquals("NPC_FIRST_OPTION", RuneForgeClient.actionName("NPC", 0));
        assertEquals("NPC_SECOND_OPTION", RuneForgeClient.actionName("NPC", 1));
        assertEquals("ITEM_FIFTH_OPTION", RuneForgeClient.actionName("ITEM", 4));

        String[] actions = {"Use", "Eat", null, "Drop", "Examine"};
        assertEquals(1, RuneForgeClient.actionIndex(actions, "eat"));
        assertEquals(3, RuneForgeClient.actionIndex(actions, "DROP"));
        assertEquals(-1, RuneForgeClient.actionIndex(actions, "Bury"));

        System.out.println("Rune Forge self-tests passed.");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                "Expected " + expected + " but got " + actual);
        }
    }
}
