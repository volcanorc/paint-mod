package com.artmapcolorassistant;

import java.util.concurrent.ThreadLocalRandom;

final class SmartFinishCooldown {
    static final int MIN_TICKS = 40;
    static final int MAX_TICKS = 120;

    private SmartFinishCooldown() {
    }

    static int randomDelayTicks() {
        return ThreadLocalRandom.current().nextInt(MIN_TICKS, MAX_TICKS + 1);
    }
}
