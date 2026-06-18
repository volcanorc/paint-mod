package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardedPaintStarterTest {
    @Test
    void startsSelectedModeOnlyAfterSessionLoads() {
        List<String> events = new ArrayList<>();

        boolean started = GuardedPaintStarter.start("1.png",
                () -> events.add("stop"),
                filename -> {
                    events.add("load:" + filename);
                    return true;
                },
                () -> {
                    events.add("start-mode");
                    return true;
                });

        assertTrue(started);
        assertEquals(List.of("stop", "load:1.png", "start-mode"), events);
    }

    @Test
    void failedImageLoadNeverStartsOldSessionAutomation() {
        List<String> events = new ArrayList<>();

        boolean started = GuardedPaintStarter.start("missing.png",
                () -> events.add("stop"),
                filename -> {
                    events.add("failed-load:" + filename);
                    return false;
                },
                () -> {
                    events.add("must-not-start");
                    return true;
                });

        assertFalse(started);
        assertEquals(List.of("stop", "failed-load:missing.png"), events);
    }

    @Test
    void modePreflightFailureIsReturnedToScreen() {
        boolean started = GuardedPaintStarter.start("1.png", () -> { }, filename -> true, () -> false);

        assertFalse(started);
    }
}
