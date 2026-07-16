package com.artmapcolorassistant;

final class SmartBucketSwapRetryPolicy {
    private final int maxRetries;
    private int swapRetries;
    private int restoreRetries;

    SmartBucketSwapRetryPolicy(int maxRetries) {
        this.maxRetries = Math.max(0, maxRetries);
    }

    void resetBucketAction() {
        swapRetries = 0;
        restoreRetries = 0;
    }

    void resetSwap() {
        swapRetries = 0;
    }

    void resetRestore() {
        restoreRetries = 0;
    }

    boolean retrySwapIfReady(boolean stillReady) {
        if (!stillReady || swapRetries >= maxRetries) {
            return false;
        }
        swapRetries++;
        return true;
    }

    boolean retryRestoreIfSwapped(boolean stillSwapped) {
        if (!stillSwapped || restoreRetries >= maxRetries) {
            return false;
        }
        restoreRetries++;
        return true;
    }

    int swapRetries() {
        return swapRetries;
    }

    int restoreRetries() {
        return restoreRetries;
    }

    int maxRetries() {
        return maxRetries;
    }
}
