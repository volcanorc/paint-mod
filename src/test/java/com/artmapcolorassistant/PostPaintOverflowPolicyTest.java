package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostPaintOverflowPolicyTest {
    @Test
    void usesPv1ForTemporaryBlockerStorage() {
        assertEquals("/pv 1", PostPaintOverflowPolicy.VAULT_COMMAND);
    }

    @Test
    void clearAttemptsAreBounded() {
        assertTrue(PostPaintOverflowPolicy.canTryClear(0));
        assertTrue(PostPaintOverflowPolicy.canTryClear(PostPaintOverflowPolicy.MAX_CLEAR_ATTEMPTS - 1));
        assertFalse(PostPaintOverflowPolicy.canTryClear(PostPaintOverflowPolicy.MAX_CLEAR_ATTEMPTS));
    }

    @Test
    void randomDelayStaysInsideHalfToAlmostOneSecondRange() {
        for (int i = 0; i < 100; i++) {
            int delay = PostPaintOverflowPolicy.randomDelayTicks();
            assertTrue(delay >= PostPaintOverflowPolicy.COMMAND_DELAY_MIN_TICKS);
            assertTrue(delay <= PostPaintOverflowPolicy.COMMAND_DELAY_MAX_TICKS);
        }
    }
}
