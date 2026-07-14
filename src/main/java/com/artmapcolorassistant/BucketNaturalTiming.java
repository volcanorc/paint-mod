package com.artmapcolorassistant;

import java.util.Random;

final class BucketNaturalTiming {
    private final Random random;

    BucketNaturalTiming() {
        this(new Random());
    }

    BucketNaturalTiming(Random random) {
        this.random = random == null ? new Random() : random;
    }

    int delay(ConfigManager.Config config, int fixedDelayTicks) {
        if (config == null || !config.bucketNaturalMovementEnabled()) {
            return Math.max(0, fixedDelayTicks);
        }
        return randomDelay(config.bucketNaturalDelayMinTicks(), config.bucketNaturalDelayMaxTicks());
    }

    int randomDelay(int minTicks, int maxTicks) {
        int min = Math.max(0, minTicks);
        int max = Math.max(min, maxTicks);
        if (min == max) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }
}
