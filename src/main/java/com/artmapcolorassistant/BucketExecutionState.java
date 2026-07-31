package com.artmapcolorassistant;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

final class BucketExecutionState {
    static final int MIN_RECENCY_COOLDOWN_STAGES = 4;
    static final int MAX_RECENCY_COOLDOWN_STAGES = 7;
    static final int STAGE_AFTER_SELECT_LOOK = 0;
    static final int STAGE_BEFORE_SWAP_LOOK = 1;
    static final int STAGE_AFTER_SWAP_LOOK = 2;
    static final int STAGE_FILL_CLICK = 3;
    static final int STAGE_AFTER_CLICK_LOOK = 4;
    static final int STAGE_BEFORE_RESTORE_LOOK = 5;
    static final int STAGE_AFTER_RESTORE_LOOK = 6;
    static final int PATH_POINTS_PER_BUCKET_ACTION = 7;

    enum ResumeRoute {
        RESTART_BEFORE_CLICK,
        CONTINUE_POST_FILL,
        APPLY_COMPLETED_FILL,
        BLOCKED
    }

    private boolean fillClickOccurred;
    private int canvasWidth;
    private int movementStageCounter;
    private final Random random;
    private List<Integer> scriptedAnchors = List.of();
    private List<Integer> fillClickAnchors = List.of();
    private List<Integer> shuffledAnchors = List.of();
    private List<Integer> remainingFillClickAnchors = List.of();
    private List<Integer> activeAnchors = List.of();
    private final Map<Integer, Integer> recentPointExpiresAt = new HashMap<>();
    private final Map<Integer, Integer> recentRowExpiresAt = new HashMap<>();

    BucketExecutionState() {
        this(new Random());
    }

    BucketExecutionState(Random random) {
        this.random = random == null ? new Random() : random;
    }

    void beginImage(List<Integer> scriptedAnchors, List<Integer> fillClickAnchors, int canvasWidth) {
        if (scriptedAnchors == null || scriptedAnchors.size() < SmartBucketAnchorPlanner.NATURAL_TARGET_POINTS) {
            throw new IllegalArgumentException("Smart bucket requires thirty natural scripted anchors.");
        }
        if (fillClickAnchors == null || fillClickAnchors.isEmpty()) {
            throw new IllegalArgumentException("Smart bucket requires at least one exact fill-click anchor.");
        }
        if (canvasWidth <= 0) {
            throw new IllegalArgumentException("Smart bucket requires a valid canvas width.");
        }
        List<Integer> nextAnchors = List.copyOf(scriptedAnchors);
        List<Integer> nextFillClickAnchors = List.copyOf(fillClickAnchors);
        boolean movementContextChanged = this.canvasWidth != canvasWidth
                || !this.scriptedAnchors.equals(nextAnchors)
                || !this.fillClickAnchors.equals(nextFillClickAnchors);
        this.scriptedAnchors = nextAnchors;
        this.fillClickAnchors = nextFillClickAnchors;
        this.shuffledAnchors = shuffledCopy(this.scriptedAnchors);
        this.canvasWidth = canvasWidth;
        activeAnchors = List.of();
        fillClickOccurred = false;
        if (movementContextChanged) {
            remainingFillClickAnchors = shuffledCopy(this.fillClickAnchors);
            movementStageCounter = 0;
            recentPointExpiresAt.clear();
            recentRowExpiresAt.clear();
        } else if (remainingFillClickAnchors.isEmpty()) {
            remainingFillClickAnchors = shuffledCopy(this.fillClickAnchors);
        }
    }

    void beginImage(List<Integer> scriptedAnchors, int canvasWidth) {
        beginImage(scriptedAnchors, scriptedAnchors, canvasWidth);
    }

    void beginBucketAction() {
        if (scriptedAnchors.size() < SmartBucketAnchorPlanner.NATURAL_TARGET_POINTS) {
            throw new IllegalStateException("Smart bucket anchors are not initialized.");
        }
        activeAnchors = nextMovementPath();
        fillClickOccurred = false;
    }

    int activeAnchor(int stage) {
        if (stage < 0 || stage >= activeAnchors.size()) {
            return -1;
        }
        return activeAnchors.get(stage);
    }

    List<Integer> activeAnchors() {
        return activeAnchors;
    }

    boolean markFillClickIfFirst() {
        if (fillClickOccurred) {
            return false;
        }
        fillClickOccurred = true;
        return true;
    }

    boolean fillClickOccurred() {
        return fillClickOccurred;
    }

    int movementStageCounterForTesting() {
        return movementStageCounter;
    }

    boolean recentPointForTesting(int anchor) {
        return isRecentPoint(anchor);
    }

    boolean recentRowForTesting(int row) {
        return isRecentRow(row);
    }

    int fillClickAnchor() {
        return activeAnchor(STAGE_FILL_CLICK);
    }

    int remainingFillClickAnchorsForTesting() {
        return remainingFillClickAnchors.size();
    }

    ResumeRoute resumeRoute(boolean handsSwapped, boolean handsReady) {
        if (!fillClickOccurred) {
            return ResumeRoute.RESTART_BEFORE_CLICK;
        }
        if (handsSwapped) {
            return ResumeRoute.CONTINUE_POST_FILL;
        }
        if (handsReady) {
            return ResumeRoute.APPLY_COMPLETED_FILL;
        }
        return ResumeRoute.BLOCKED;
    }

    private List<Integer> nextMovementPath() {
        if (shuffledAnchors.size() < SmartBucketAnchorPlanner.NATURAL_TARGET_POINTS) {
            shuffledAnchors = shuffledCopy(scriptedAnchors);
        }
        ArrayList<Integer> path = new ArrayList<>(PATH_POINTS_PER_BUCKET_ACTION);
        for (int stage = 0; stage < PATH_POINTS_PER_BUCKET_ACTION; stage++) {
            int anchor = stage == STAGE_FILL_CLICK ? nextFillClickAnchor(path) : chooseAnchor(path);
            path.add(anchor);
            remember(anchor);
        }
        return List.copyOf(path);
    }

    private int nextFillClickAnchor(List<Integer> path) {
        if (remainingFillClickAnchors.isEmpty()) {
            remainingFillClickAnchors = shuffledCopy(fillClickAnchors);
        }
        int selectedIndex = firstFillClickCandidateIndex(path, true, true);
        if (selectedIndex < 0) {
            selectedIndex = firstFillClickCandidateIndex(path, true, false);
        }
        if (selectedIndex < 0) {
            selectedIndex = firstFillClickCandidateIndex(path, false, false);
        }
        if (selectedIndex < 0) {
            selectedIndex = 0;
        }
        int anchor = remainingFillClickAnchors.get(selectedIndex);
        ArrayList<Integer> nextRemaining = new ArrayList<>(remainingFillClickAnchors);
        nextRemaining.remove(selectedIndex);
        remainingFillClickAnchors = List.copyOf(nextRemaining);
        return anchor;
    }

    private int firstFillClickCandidateIndex(List<Integer> path, boolean avoidPathPoint, boolean avoidPathRow) {
        for (int i = 0; i < remainingFillClickAnchors.size(); i++) {
            int anchor = remainingFillClickAnchors.get(i);
            if (avoidPathPoint && path.contains(anchor)) {
                continue;
            }
            if (avoidPathRow && path.stream().anyMatch(existing -> row(existing) == row(anchor))) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private int chooseAnchor(List<Integer> path) {
        List<Integer> candidates = shuffledCopy(scriptedAnchors);
        Integer best = firstMatching(candidates, path, true, true, true, true);
        if (best != null) {
            return best;
        }
        best = firstMatching(candidates, path, true, false, true, true);
        if (best != null) {
            return best;
        }
        best = firstMatching(candidates, path, true, false, true, false);
        if (best != null) {
            return best;
        }
        best = firstMatching(candidates, path, false, false, true, false);
        if (best != null) {
            return best;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private Integer firstMatching(List<Integer> candidates, List<Integer> path, boolean avoidRecentPoint,
                                  boolean avoidRecentRow, boolean avoidPathPoint, boolean avoidPathRow) {
        for (int anchor : candidates) {
            if (avoidPathPoint && path.contains(anchor)) {
                continue;
            }
            int row = row(anchor);
            if (avoidPathRow && path.stream().anyMatch(existing -> row(existing) == row)) {
                continue;
            }
            if (avoidRecentPoint && isRecentPoint(anchor)) {
                continue;
            }
            if (avoidRecentRow && isRecentRow(row)) {
                continue;
            }
            return anchor;
        }
        return null;
    }

    private void remember(int anchor) {
        int cooldown = MIN_RECENCY_COOLDOWN_STAGES
                + random.nextInt(MAX_RECENCY_COOLDOWN_STAGES - MIN_RECENCY_COOLDOWN_STAGES + 1);
        int expiresAt = movementStageCounter + cooldown + 1;
        recentPointExpiresAt.put(anchor, expiresAt);
        recentRowExpiresAt.put(row(anchor), expiresAt);
        movementStageCounter++;
        expireOldEntries();
    }

    private void expireOldEntries() {
        recentPointExpiresAt.entrySet().removeIf(entry -> entry.getValue() <= movementStageCounter);
        recentRowExpiresAt.entrySet().removeIf(entry -> entry.getValue() <= movementStageCounter);
    }

    private boolean isRecentPoint(int anchor) {
        return recentPointExpiresAt.getOrDefault(anchor, -1) > movementStageCounter;
    }

    private boolean isRecentRow(int row) {
        return recentRowExpiresAt.getOrDefault(row, -1) > movementStageCounter;
    }

    private int row(int anchor) {
        return CanvasMath.toY(anchor, canvasWidth);
    }

    private List<Integer> shuffledCopy(List<Integer> anchors) {
        ArrayList<Integer> copy = new ArrayList<>(anchors);
        for (int i = copy.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Integer tmp = copy.get(i);
            copy.set(i, copy.get(j));
            copy.set(j, tmp);
        }
        return List.copyOf(copy);
    }
}
