package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BucketNaturalTimingTest {
    @Test
    void randomDelayStaysInsideConfiguredRange() {
        BucketNaturalTiming timing = new BucketNaturalTiming(new Random(4));

        for (int i = 0; i < 100; i++) {
            int delay = timing.randomDelay(10, 32);
            assertTrue(delay >= 10 && delay <= 32);
        }
    }

    @Test
    void bucketNaturalSettingsCannotDisableNaturalMovement() {
        ConfigManager.Config config = ConfigManager.Config.defaults()
                .withBucketNaturalSettings(false, 10, 32);

        assertTrue(config.bucketNaturalMovementEnabled());
    }

    @Test
    void enabledNaturalMovementUsesConfiguredRange() {
        ConfigManager.Config config = ConfigManager.Config.defaults()
                .withBucketNaturalSettings(true, 11, 11);

        assertEquals(11, new BucketNaturalTiming(new Random(6)).delay(config, 24));
    }
}
