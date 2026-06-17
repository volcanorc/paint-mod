package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostPaintPv2AutomationTest {
    @Test
    void defaultPostPaintDoesNotRequireRecordedPv2ClickPoint() {
        ConfigManager.Config config = ConfigManager.Config.defaults();

        assertTrue(config.postPaintAutomationEnabled());
        assertEquals(0, config.postPaintFinishedHotbarSlot());
        assertNull(config.postPaintPv2ClickPoint());
    }

    @Test
    void commandGuideMarksPv2ClickAsLegacy() {
        List<CommandGuide.Entry> entries = CommandGuide.suggestions("#painting pv2");

        assertTrue(entries.stream().anyMatch(entry ->
                entry.command().equals("click")
                        && entry.description().toLowerCase().contains("legacy")
                        && entry.description().toLowerCase().contains("automatic slot transfer")));
    }
}
