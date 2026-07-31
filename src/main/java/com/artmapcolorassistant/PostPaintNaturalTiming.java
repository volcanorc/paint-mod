package com.artmapcolorassistant;

import java.util.concurrent.ThreadLocalRandom;

final class PostPaintNaturalTiming {
    static final int SHORT_MIN_TICKS = 10;
    static final int SHORT_MAX_TICKS = 18;
    static final int VAULT_MIN_TICKS = 14;
    static final int VAULT_MAX_TICKS = 28;

    private PostPaintNaturalTiming() {
    }

    static int shortDelayTicks() {
        return randomDelayTicks(SHORT_MIN_TICKS, SHORT_MAX_TICKS);
    }

    static int shortDelayAtLeast(int minimumTicks) {
        int min = Math.max(SHORT_MIN_TICKS, minimumTicks);
        int max = Math.max(SHORT_MAX_TICKS, min + 8);
        return randomDelayTicks(min, max);
    }

    static int vaultDelayTicks() {
        return randomDelayTicks(VAULT_MIN_TICKS, VAULT_MAX_TICKS);
    }

    static int randomDelayTicks(int minTicks, int maxTicks) {
        int min = Math.max(0, minTicks);
        int max = Math.max(min, maxTicks);
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
