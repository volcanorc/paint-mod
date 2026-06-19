package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerVaultSelectionTest {
    @Test
    void acceptsBoundariesAndBuildsServerCommandAndDisplayName() {
        assertSelection(1);
        assertSelection(2);
        assertSelection(40);
    }

    @Test
    void rejectsMissingMalformedAndOutOfRangeNumbers() {
        assertFalse(PlayerVaultSelection.parseNumber(null).valid());
        assertFalse(PlayerVaultSelection.parseNumber("").valid());
        assertFalse(PlayerVaultSelection.parseNumber("three").valid());
        assertFalse(PlayerVaultSelection.parseNumber("0").valid());
        assertFalse(PlayerVaultSelection.parseNumber("-1").valid());
        assertFalse(PlayerVaultSelection.parseNumber("41").valid());
    }

    @Test
    void parsesCompactCommandsWithoutConfusingLegacyPv2Arguments() {
        assertEquals(1, PlayerVaultSelection.parseCompact("pv1").selection().number());
        assertEquals(2, PlayerVaultSelection.parseCompact("pv2").selection().number());
        assertEquals(40, PlayerVaultSelection.parseCompact("PV40").selection().number());
        assertFalse(PlayerVaultSelection.parseCompact("pv0").valid());
        assertFalse(PlayerVaultSelection.parseCompact("pv-1").valid());
        assertFalse(PlayerVaultSelection.parseCompact("pv41").valid());
        assertFalse(PlayerVaultSelection.looksCompact("pv2 click"));
    }

    @Test
    void readsLegacyStoredVaultFormsAndLeavesCustomCommandsReadable() {
        assertEquals(3, PlayerVaultSelection.fromStoredCommand("/pv 3").number());
        assertEquals(20, PlayerVaultSelection.fromStoredCommand("playervault 20").number());
        assertEquals(40, PlayerVaultSelection.fromStoredCommand("/PLAYERVAULT 40").number());
        assertNull(PlayerVaultSelection.fromStoredCommand("/home storage"));
        assertEquals("Custom storage", PlayerVaultSelection.displayName("/home storage"));
    }

    @Test
    void configDefaultsToVaultTwoAndPreservesSelectionAcrossOtherSettings() {
        ConfigManager.Config selected = ConfigManager.Config.defaults().withPostPaintVaultCommand("/pv 20");

        assertEquals("/pv 2", ConfigManager.Config.defaults().postPaintVaultCommand());
        assertEquals("/pv 20", selected.postPaintVaultCommand());
        assertEquals("/pv 20", selected.withPaintingModePreset(PaintingMode.SMART).postPaintVaultCommand());
        assertEquals("/pv 20", selected.withPostPaintAutomationEnabled(false).postPaintVaultCommand());
        assertEquals("/pv 20", selected.withSmartSettings(false, SmartPaintMode.AGGRESSIVE, false, 12, 4)
                .postPaintVaultCommand());
    }

    @Test
    void suggestionsSeparateCanonicalSelectionFromLegacyPv2Recorder() {
        assertTrue(CommandGuide.suggestions("#painting pv").stream()
                .anyMatch(entry -> entry.command().equals("<1-40>")));
        assertTrue(CommandGuide.suggestions("#painting pv2").stream()
                .anyMatch(entry -> entry.command().equals("click")));
        assertFalse(CommandGuide.suggestions("#painting pv20").stream()
                .anyMatch(entry -> entry.command().equals("click")));
    }

    private static void assertSelection(int number) {
        PlayerVaultSelection selection = new PlayerVaultSelection(number);
        assertEquals("/pv " + number, selection.command());
        assertEquals("Player Vault " + number, selection.displayName());
        assertTrue(PlayerVaultSelection.parseNumber(Integer.toString(number)).valid());
    }
}
