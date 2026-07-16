package com.artmapcolorassistant;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

final class BucketOffhandSetupPlan {
    static final int ACTION_DELAY_MIN_TICKS = 10;
    static final int ACTION_DELAY_MAX_TICKS = 18;

    private BucketOffhandSetupPlan() {
    }

    static List<Step> steps(boolean offhandOccupied) {
        if (offhandOccupied) {
            return List.of(Step.PICK_OFFHAND, Step.PLACE_PARKED_OFFHAND,
                    Step.PICK_BUCKET, Step.PLACE_BUCKET_OFFHAND);
        }
        return List.of(Step.PICK_BUCKET, Step.PLACE_BUCKET_OFFHAND);
    }

    static int randomDelayTicks() {
        return ThreadLocalRandom.current().nextInt(ACTION_DELAY_MIN_TICKS, ACTION_DELAY_MAX_TICKS + 1);
    }

    enum Step {
        PICK_OFFHAND,
        PLACE_PARKED_OFFHAND,
        PICK_BUCKET,
        PLACE_BUCKET_OFFHAND
    }
}
