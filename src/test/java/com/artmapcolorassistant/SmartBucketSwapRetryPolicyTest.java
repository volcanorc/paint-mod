package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartBucketSwapRetryPolicyTest {
    @Test
    void swapRetriesOnlyWhileHandsAreStillReady() {
        SmartBucketSwapRetryPolicy policy = new SmartBucketSwapRetryPolicy(2);

        assertFalse(policy.retrySwapIfReady(false));
        assertEquals(0, policy.swapRetries());

        assertTrue(policy.retrySwapIfReady(true));
        assertTrue(policy.retrySwapIfReady(true));
        assertFalse(policy.retrySwapIfReady(true));
        assertEquals(2, policy.swapRetries());
    }

    @Test
    void restoreRetriesOnlyWhileHandsAreStillSwapped() {
        SmartBucketSwapRetryPolicy policy = new SmartBucketSwapRetryPolicy(2);

        assertFalse(policy.retryRestoreIfSwapped(false));
        assertEquals(0, policy.restoreRetries());

        assertTrue(policy.retryRestoreIfSwapped(true));
        assertTrue(policy.retryRestoreIfSwapped(true));
        assertFalse(policy.retryRestoreIfSwapped(true));
        assertEquals(2, policy.restoreRetries());
    }

    @Test
    void resetBucketActionClearsBothRetryCounters() {
        SmartBucketSwapRetryPolicy policy = new SmartBucketSwapRetryPolicy(2);

        assertTrue(policy.retrySwapIfReady(true));
        assertTrue(policy.retryRestoreIfSwapped(true));

        policy.resetBucketAction();

        assertEquals(0, policy.swapRetries());
        assertEquals(0, policy.restoreRetries());
    }
}
