package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PostPaintNaturalTimingTest {
    @Test
    void shortDelayStaysInsideNaturalRangeAndVaries() {
        HashSet<Integer> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            int delay = PostPaintNaturalTiming.shortDelayTicks();
            assertTrue(delay >= PostPaintNaturalTiming.SHORT_MIN_TICKS);
            assertTrue(delay <= PostPaintNaturalTiming.SHORT_MAX_TICKS);
            seen.add(delay);
        }
        assertTrue(seen.size() > 1);
    }

    @Test
    void vaultDelayStaysInsideNaturalRangeAndVaries() {
        HashSet<Integer> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            int delay = PostPaintNaturalTiming.vaultDelayTicks();
            assertTrue(delay >= PostPaintNaturalTiming.VAULT_MIN_TICKS);
            assertTrue(delay <= PostPaintNaturalTiming.VAULT_MAX_TICKS);
            seen.add(delay);
        }
        assertTrue(seen.size() > 1);
    }

    @Test
    void shortDelayAtLeastPreservesConfiguredMinimumAndStillVaries() {
        HashSet<Integer> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            int delay = PostPaintNaturalTiming.shortDelayAtLeast(24);
            assertTrue(delay >= 24);
            assertTrue(delay <= 32);
            seen.add(delay);
        }
        assertTrue(seen.size() > 1);
    }
}
