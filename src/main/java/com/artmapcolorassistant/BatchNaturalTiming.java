package com.artmapcolorassistant;

import java.util.concurrent.ThreadLocalRandom;

final class BatchNaturalTiming {
    static final int NEXT_IMAGE_MIN_TICKS = 40;
    static final int NEXT_IMAGE_MAX_TICKS = 120;

    private BatchNaturalTiming() {
    }

    static int randomNextImageDelayTicks() {
        return ThreadLocalRandom.current().nextInt(NEXT_IMAGE_MIN_TICKS, NEXT_IMAGE_MAX_TICKS + 1);
    }
}
