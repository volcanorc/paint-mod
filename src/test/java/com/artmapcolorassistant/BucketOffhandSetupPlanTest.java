package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BucketOffhandSetupPlanTest {
    @Test
    void emptyOffhandNeedsTwoDelayedInventoryActions() {
        assertEquals(List.of(
                BucketOffhandSetupPlan.Step.PICK_BUCKET,
                BucketOffhandSetupPlan.Step.PLACE_BUCKET_OFFHAND
        ), BucketOffhandSetupPlan.steps(false));
    }

    @Test
    void occupiedOffhandIsParkedBeforeBucketPlacement() {
        assertEquals(List.of(
                BucketOffhandSetupPlan.Step.PICK_OFFHAND,
                BucketOffhandSetupPlan.Step.PLACE_PARKED_OFFHAND,
                BucketOffhandSetupPlan.Step.PICK_BUCKET,
                BucketOffhandSetupPlan.Step.PLACE_BUCKET_OFFHAND
        ), BucketOffhandSetupPlan.steps(true));
    }

    @Test
    void inventorySetupDelayUsesNaturalHalfToAlmostOneSecondRange() {
        for (int i = 0; i < 100; i++) {
            int delay = BucketOffhandSetupPlan.randomDelayTicks();
            assertTrue(delay >= BucketOffhandSetupPlan.ACTION_DELAY_MIN_TICKS);
            assertTrue(delay <= BucketOffhandSetupPlan.ACTION_DELAY_MAX_TICKS);
        }
    }
}
