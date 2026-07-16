package com.artmapcolorassistant;

import java.util.concurrent.ThreadLocalRandom;

final class PostPaintOverflowPolicy {
    static final int MAX_CLEAR_ATTEMPTS = 5;
    static final int COMMAND_DELAY_MIN_TICKS = 10;
    static final int COMMAND_DELAY_MAX_TICKS = 18;
    static final String VAULT_COMMAND = "/pv 1";

    private PostPaintOverflowPolicy() {
    }

    static boolean canTryClear(int attemptsAlreadyUsed) {
        return attemptsAlreadyUsed < MAX_CLEAR_ATTEMPTS;
    }

    static int randomDelayTicks() {
        return ThreadLocalRandom.current().nextInt(COMMAND_DELAY_MIN_TICKS, COMMAND_DELAY_MAX_TICKS + 1);
    }
}
