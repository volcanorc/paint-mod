package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartFinishCooldownTest {
    @Test
    void deepBlackBucketOnlyFinishDelayUsesNaturalTwoToSixSecondRange() {
        for (int i = 0; i < 100; i++) {
            int delay = SmartFinishCooldown.randomDelayTicks();
            assertTrue(delay >= SmartFinishCooldown.MIN_TICKS);
            assertTrue(delay <= SmartFinishCooldown.MAX_TICKS);
        }
    }
}
