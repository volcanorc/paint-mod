package com.artmapcolorassistant;

public record RecoveryProgress(
        int schemaVersion,
        String filename,
        int currentIndex,
        int totalSteps,
        PaintingMode paintingMode,
        boolean batchActive,
        int batchFirst,
        int batchLast,
        int batchCurrent,
        String batchSuffix,
        int smartActionBoundary,
        boolean smartBucketInFlight,
        long pngSize,
        long pngLastModifiedMillis,
        long savedAtMillis,
        String lastWarning
) {
    public static final int SCHEMA_VERSION = 1;

    public RecoveryProgress {
        schemaVersion = SCHEMA_VERSION;
        filename = filename == null ? "" : filename;
        currentIndex = Math.max(0, currentIndex);
        totalSteps = Math.max(0, totalSteps);
        paintingMode = paintingMode == null ? PaintingMode.SMART : paintingMode;
        batchFirst = Math.max(0, batchFirst);
        batchLast = Math.max(0, batchLast);
        batchCurrent = Math.max(0, batchCurrent);
        batchSuffix = batchSuffix == null ? "" : batchSuffix;
        smartActionBoundary = Math.max(0, smartActionBoundary);
        lastWarning = lastWarning == null ? "" : lastWarning;
    }

    public static RecoveryProgress forSession(PaintSession session, PaintingMode mode, BatchSnapshot batch,
                                              int smartActionBoundary, boolean smartBucketInFlight,
                                              ImageFingerprint fingerprint, String warning) {
        if (session == null || fingerprint == null) {
            return null;
        }
        BatchSnapshot safeBatch = batch == null ? BatchSnapshot.none() : batch;
        return new RecoveryProgress(SCHEMA_VERSION, session.filename(), session.currentIndex(), session.steps().size(),
                mode, safeBatch.active(), safeBatch.first(), safeBatch.last(), safeBatch.current(), safeBatch.suffix(),
                smartActionBoundary, smartBucketInFlight, fingerprint.size(), fingerprint.lastModifiedMillis(),
                System.currentTimeMillis(), warning);
    }

    public boolean hasBatch() {
        return batchActive && batchFirst > 0 && batchLast >= batchFirst && batchCurrent >= batchFirst;
    }

    public boolean hasWarning() {
        return !lastWarning.isBlank();
    }

    public record BatchSnapshot(boolean active, int first, int last, int current, String suffix) {
        public BatchSnapshot {
            suffix = suffix == null ? "" : suffix;
        }

        public static BatchSnapshot none() {
            return new BatchSnapshot(false, 0, 0, 0, "");
        }
    }

    public record ImageFingerprint(long size, long lastModifiedMillis) {
    }
}
