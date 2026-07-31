package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchNaturalTimingTest {
    @Test
    void nextImageDelayUsesNaturalTwoToSixSecondRange() {
        for (int i = 0; i < 100; i++) {
            int delay = BatchNaturalTiming.randomNextImageDelayTicks();
            assertTrue(delay >= BatchNaturalTiming.NEXT_IMAGE_MIN_TICKS);
            assertTrue(delay <= BatchNaturalTiming.NEXT_IMAGE_MAX_TICKS);
        }
    }
}
