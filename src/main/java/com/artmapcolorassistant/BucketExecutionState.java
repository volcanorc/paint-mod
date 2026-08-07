package com.artmapcolorassistant;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

final class BucketExecutionState {
    static final int MIN_RECENCY_COOLDOWN_STAGES = 4;
    static final int MAX_RECENCY_COOLDOWN_STAGES = 7;
    static final int LOCAL_FILL_ROW_WINDOW = 4;
    static final int LOCAL_FILL_COLUMN_WINDOW = 8;
    static final int PREFERRED_LOOK_ROW_WINDOW = 2;
    static final int PREFERRED_LOOK_COLUMN_WINDOW = 4;
    static final int RELAXED_LOOK_ROW_WINDOW = 3;
    static final int RELAXED_LOOK_COLUMN_WINDOW = 6;
    static final int LOCAL_LOOK_ROW_WINDOW = 4;
    static final int LOCAL_LOOK_COLUMN_WINDOW = 8;
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
    private int lastBucketAnchor = -1;
    private int bucketActionCounter;
    private final Set<Integer> currentImageFillAnchors = new HashSet<>();
    private final Map<Integer, Integer> recentPointExpiresAt = new HashMap<>();
    private final Map<Integer, Integer> recentRowExpiresAt = new HashMap<>();
    private final Map<Integer, Integer> recentFillPointExpiresAt = new HashMap<>();
    private final Map<Integer, Integer> recentFillRowExpiresAt = new HashMap<>();

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
        currentImageFillAnchors.clear();
        if (movementContextChanged) {
            remainingFillClickAnchors = shuffledCopy(this.fillClickAnchors);
            movementStageCounter = 0;
            bucketActionCounter = 0;
            lastBucketAnchor = -1;
            recentPointExpiresAt.clear();
            recentRowExpiresAt.clear();
            recentFillPointExpiresAt.clear();
            recentFillRowExpiresAt.clear();
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
        int fillAnchor = nextFillClickAnchor();
        ArrayList<Integer> path = new ArrayList<>(PATH_POINTS_PER_BUCKET_ACTION);
        for (int stage = 0; stage < PATH_POINTS_PER_BUCKET_ACTION; stage++) {
            int anchor;
            if (stage == STAGE_FILL_CLICK) {
                anchor = fillAnchor;
            } else {
                int reference = path.isEmpty() ? lastBucketAnchor : path.get(path.size() - 1);
                anchor = chooseLocalLookAnchor(path, reference, fillAnchor, stage);
            }
            path.add(anchor);
            remember(anchor);
        }
        lastBucketAnchor = path.get(path.size() - 1);
        return List.copyOf(path);
    }

    private int nextFillClickAnchor() {
        if (remainingFillClickAnchors.isEmpty()) {
            remainingFillClickAnchors = shuffledCopy(fillClickAnchors);
        }
        int selectedIndex = localFillClickCandidateIndex();
        if (selectedIndex < 0) {
            selectedIndex = centralFillClickCandidateIndex();
        }
        if (selectedIndex < 0) {
            selectedIndex = nearestRemainingFillClickCandidateIndex();
        }
        int anchor = remainingFillClickAnchors.get(selectedIndex);
        ArrayList<Integer> nextRemaining = new ArrayList<>(remainingFillClickAnchors);
        nextRemaining.remove(selectedIndex);
        remainingFillClickAnchors = List.copyOf(nextRemaining);
        currentImageFillAnchors.add(anchor);
        rememberFill(anchor);
        return anchor;
    }

    private int localFillClickCandidateIndex() {
        if (lastBucketAnchor < 0) {
            return centralFillClickCandidateIndex();
        }
        int[][] windows = new int[][]{
                {3, 5},
                {4, 8},
                {6, 10},
                {8, 12},
                {12, 16}
        };
        for (int[] window : windows) {
            int index = bestFillClickCandidateIndex(window[0], window[1], true, true, true, true);
            if (index >= 0) {
                return index;
            }
        }
        for (int[] window : windows) {
            int index = bestFillClickCandidateIndex(window[0], window[1], true, true, true, false);
            if (index >= 0) {
                return index;
            }
        }
        for (int[] window : windows) {
            int index = bestFillClickCandidateIndex(window[0], window[1], true, false, false, false);
            if (index >= 0) {
                return index;
            }
        }
        return -1;
    }

    private int bestFillClickCandidateIndex(int rowWindow, int columnWindow, boolean avoidCurrentImageFill,
                                            boolean avoidLastBucketAnchor, boolean avoidRecentPoint,
                                            boolean avoidRecentRow) {
        ArrayList<Integer> bestIndexes = new ArrayList<>();
        int bestScore = Integer.MAX_VALUE;
        for (int i = 0; i < remainingFillClickAnchors.size(); i++) {
            int anchor = remainingFillClickAnchors.get(i);
            if (avoidCurrentImageFill && currentImageFillAnchors.contains(anchor)) {
                continue;
            }
            if (avoidLastBucketAnchor && anchor == lastBucketAnchor) {
                continue;
            }
            if (avoidRecentPoint && isRecentFillPoint(anchor)) {
                continue;
            }
            if (avoidRecentRow && isRecentFillRow(row(anchor))) {
                continue;
            }
            if (lastBucketAnchor >= 0 && !withinWindow(anchor, lastBucketAnchor, rowWindow, columnWindow)) {
                continue;
            }
            int score = lastBucketAnchor < 0 ? 0 : manhattanDistance(anchor, lastBucketAnchor);
            if (score < bestScore) {
                bestIndexes.clear();
                bestScore = score;
            }
            if (score == bestScore || score <= bestScore + 2) {
                bestIndexes.add(i);
            }
        }
        return randomBestIndex(bestIndexes);
    }

    private int centralFillClickCandidateIndex() {
        ArrayList<Integer> central = new ArrayList<>();
        int minRow = fillClickAnchors.stream().mapToInt(this::row).min().orElse(0);
        int maxRow = fillClickAnchors.stream().mapToInt(this::row).max().orElse(minRow);
        int minColumn = fillClickAnchors.stream().mapToInt(this::column).min().orElse(0);
        int maxColumn = fillClickAnchors.stream().mapToInt(this::column).max().orElse(minColumn);
        int rowMargin = Math.max(1, (maxRow - minRow + 1) / 5);
        int columnMargin = Math.max(1, (maxColumn - minColumn + 1) / 5);
        int lowRow = minRow + rowMargin;
        int highRow = maxRow - rowMargin;
        int lowColumn = minColumn + columnMargin;
        int highColumn = maxColumn - columnMargin;
        for (int i = 0; i < remainingFillClickAnchors.size(); i++) {
            int anchor = remainingFillClickAnchors.get(i);
            if (currentImageFillAnchors.contains(anchor)) {
                continue;
            }
            int row = row(anchor);
            int column = column(anchor);
            if (row >= lowRow && row <= highRow && column >= lowColumn && column <= highColumn) {
                central.add(i);
            }
        }
        if (!central.isEmpty()) {
            return central.get(random.nextInt(central.size()));
        }
        return remainingFillClickAnchors.isEmpty() ? -1 : random.nextInt(remainingFillClickAnchors.size());
    }

    private int nearestRemainingFillClickCandidateIndex() {
        if (remainingFillClickAnchors.isEmpty()) {
            return -1;
        }
        if (lastBucketAnchor < 0) {
            return random.nextInt(remainingFillClickAnchors.size());
        }
        ArrayList<Integer> bestIndexes = new ArrayList<>();
        int bestScore = Integer.MAX_VALUE;
        for (int i = 0; i < remainingFillClickAnchors.size(); i++) {
            int anchor = remainingFillClickAnchors.get(i);
            if (currentImageFillAnchors.contains(anchor) && remainingFillClickAnchors.size() > currentImageFillAnchors.size()) {
                continue;
            }
            int score = manhattanDistance(anchor, lastBucketAnchor);
            if (score < bestScore) {
                bestIndexes.clear();
                bestScore = score;
            }
            if (score == bestScore || score <= bestScore + 2) {
                bestIndexes.add(i);
            }
        }
        return randomBestIndex(bestIndexes);
    }

    private int randomBestIndex(List<Integer> indexes) {
        if (indexes == null || indexes.isEmpty()) {
            return -1;
        }
        int limit = Math.min(10, indexes.size());
        return indexes.get(random.nextInt(limit));
    }

    private int chooseLocalLookAnchor(List<Integer> path, int referenceAnchor, int fillAnchor, int stage) {
        boolean afterFill = stage > STAGE_FILL_CLICK;
        boolean requireFillWindow = afterFill || referenceAnchor < 0 || stage == STAGE_AFTER_SWAP_LOOK;
        List<Integer> candidates = shuffledCopy(scriptedAnchors);
        Integer best = chooseProgressiveBridgeAnchor(candidates, path, referenceAnchor, fillAnchor, stage, true, true);
        if (best != null) {
            return best;
        }
        best = chooseProgressiveBridgeAnchor(candidates, path, referenceAnchor, fillAnchor, stage, true, false);
        if (best != null) {
            return best;
        }
        best = chooseProgressiveBridgeAnchor(candidates, path, referenceAnchor, fillAnchor, stage, false, false);
        if (best != null) {
            return best;
        }
        best = firstLocalMatching(candidates, path, referenceAnchor, fillAnchor, true, true,
                PREFERRED_LOOK_ROW_WINDOW, PREFERRED_LOOK_COLUMN_WINDOW, afterFill, requireFillWindow);
        if (best != null) {
            return best;
        }
        best = firstLocalMatching(candidates, path, referenceAnchor, fillAnchor, true, false,
                PREFERRED_LOOK_ROW_WINDOW, PREFERRED_LOOK_COLUMN_WINDOW, afterFill, requireFillWindow);
        if (best != null) {
            return best;
        }
        best = firstLocalMatching(candidates, path, referenceAnchor, fillAnchor, true, true,
                RELAXED_LOOK_ROW_WINDOW, RELAXED_LOOK_COLUMN_WINDOW, afterFill, requireFillWindow);
        if (best != null) {
            return best;
        }
        best = firstLocalMatching(candidates, path, referenceAnchor, fillAnchor, true, false,
                RELAXED_LOOK_ROW_WINDOW, RELAXED_LOOK_COLUMN_WINDOW, afterFill, requireFillWindow);
        if (best != null) {
            return best;
        }
        best = firstLocalMatching(candidates, path, referenceAnchor, fillAnchor, true, true,
                LOCAL_LOOK_ROW_WINDOW, LOCAL_LOOK_COLUMN_WINDOW, afterFill, requireFillWindow);
        if (best != null) {
            return best;
        }
        best = firstLocalMatching(candidates, path, referenceAnchor, fillAnchor, true, false,
                LOCAL_LOOK_ROW_WINDOW, LOCAL_LOOK_COLUMN_WINDOW, afterFill, requireFillWindow);
        if (best != null) {
            return best;
        }
        best = firstLocalMatching(candidates, path, referenceAnchor, fillAnchor, false, false,
                LOCAL_LOOK_ROW_WINDOW, LOCAL_LOOK_COLUMN_WINDOW, afterFill, requireFillWindow);
        if (best != null) {
            return best;
        }
        best = firstLocalMatching(candidates, path, referenceAnchor, fillAnchor, false, false,
                8, 12, afterFill, requireFillWindow);
        if (best != null) {
            return best;
        }
        best = firstLocalMatching(candidates, path, referenceAnchor, fillAnchor, false, false,
                12, 16, afterFill, requireFillWindow);
        if (best != null) {
            return best;
        }
        best = nearestLocalBridgeAnchor(path, referenceAnchor, fillAnchor, stage);
        if (best != null) {
            return best;
        }
        return nearestSafeAnchor(path, referenceAnchor, fillAnchor);
    }

    private Integer chooseProgressiveBridgeAnchor(List<Integer> candidates, List<Integer> path, int referenceAnchor,
                                                  int fillAnchor, int stage, boolean avoidRecentPoint,
                                                  boolean avoidRecentRow) {
        if (stage >= STAGE_FILL_CLICK) {
            return null;
        }
        int remainingTransitionsToFill = STAGE_FILL_CLICK - stage;
        int maxRowsAfterThisStage = LOCAL_LOOK_ROW_WINDOW * remainingTransitionsToFill;
        int maxColumnsAfterThisStage = LOCAL_LOOK_COLUMN_WINDOW * remainingTransitionsToFill;
        ArrayList<Integer> best = new ArrayList<>();
        int bestScore = Integer.MAX_VALUE;
        for (int anchor : candidates) {
            if (path.contains(anchor) || anchor == fillAnchor) {
                continue;
            }
            if (referenceAnchor >= 0 && !withinWindow(anchor, referenceAnchor,
                    LOCAL_LOOK_ROW_WINDOW, LOCAL_LOOK_COLUMN_WINDOW)) {
                continue;
            }
            if (isFartherFromFill(anchor, referenceAnchor, fillAnchor)
                    || isFartherFromFillRow(anchor, referenceAnchor, fillAnchor)) {
                continue;
            }
            if (rowDistance(anchor, fillAnchor) > maxRowsAfterThisStage
                    || columnDistance(anchor, fillAnchor) > maxColumnsAfterThisStage) {
                continue;
            }
            if (avoidRecentPoint && isRecentPoint(anchor)) {
                continue;
            }
            if (avoidRecentRow && isRecentRow(row(anchor))) {
                continue;
            }
            int score = manhattanDistance(anchor, fillAnchor);
            if (referenceAnchor >= 0) {
                score += manhattanDistance(anchor, referenceAnchor);
            }
            if (score < bestScore) {
                best.clear();
                bestScore = score;
            }
            if (score == bestScore || score <= bestScore + 2) {
                best.add(anchor);
            }
        }
        int selected = randomBestAnchor(best);
        return selected < 0 ? null : selected;
    }

    private Integer nearestLocalBridgeAnchor(List<Integer> path, int referenceAnchor, int fillAnchor, int stage) {
        boolean beforeFillClick = stage < STAGE_FILL_CLICK;
        boolean afterFillClick = stage > STAGE_FILL_CLICK;
        Integer best = nearestSafeAnchor(path, referenceAnchor, fillAnchor,
                LOCAL_LOOK_ROW_WINDOW, LOCAL_LOOK_COLUMN_WINDOW, true, true);
        if (best != null) {
            return best;
        }
        if (beforeFillClick && stage == STAGE_AFTER_SWAP_LOOK) {
            best = nearestSafeAnchor(path, referenceAnchor, fillAnchor,
                    LOCAL_LOOK_ROW_WINDOW, LOCAL_LOOK_COLUMN_WINDOW, false, true);
            if (best != null) {
                return best;
            }
        }
        if (afterFillClick) {
            best = nearestSafeAnchor(path, referenceAnchor, fillAnchor,
                    LOCAL_LOOK_ROW_WINDOW, LOCAL_LOOK_COLUMN_WINDOW, true, false);
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private Integer firstLocalMatching(List<Integer> candidates, List<Integer> path, int referenceAnchor,
                                       int fillAnchor, boolean avoidRecentPoint, boolean avoidRecentRow,
                                       int rowWindow, int columnWindow, boolean afterFill,
                                       boolean requireFillWindow) {
        ArrayList<Integer> best = new ArrayList<>();
        int bestScore = Integer.MAX_VALUE;
        for (int anchor : candidates) {
            if (path.contains(anchor)) {
                continue;
            }
            if (anchor == fillAnchor) {
                continue;
            }
            if (referenceAnchor >= 0 && !withinWindow(anchor, referenceAnchor, rowWindow, columnWindow)) {
                continue;
            }
            if (requireFillWindow && !withinWindow(anchor, fillAnchor, rowWindow, columnWindow)) {
                continue;
            }
            if (!afterFill && isFartherFromFill(anchor, referenceAnchor, fillAnchor)) {
                continue;
            }
            if (!afterFill && isFartherFromFillRow(anchor, referenceAnchor, fillAnchor)) {
                continue;
            }
            int row = row(anchor);
            if (avoidRecentPoint && isRecentPoint(anchor)) {
                continue;
            }
            if (avoidRecentRow && isRecentRow(row)) {
                continue;
            }
            int score = manhattanDistance(anchor, fillAnchor);
            if (referenceAnchor >= 0) {
                score += manhattanDistance(anchor, referenceAnchor);
            }
            if (score < bestScore) {
                best.clear();
                bestScore = score;
            }
            if (score == bestScore || score <= bestScore + 2) {
                best.add(anchor);
            }
        }
        int selected = randomBestAnchor(best);
        return selected < 0 ? null : selected;
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

    private boolean isFartherFromFill(int anchor, int referenceAnchor, int fillAnchor) {
        if (referenceAnchor < 0 || referenceAnchor == fillAnchor) {
            return false;
        }
        return manhattanDistance(anchor, fillAnchor) > manhattanDistance(referenceAnchor, fillAnchor);
    }

    private boolean isFartherFromFillRow(int anchor, int referenceAnchor, int fillAnchor) {
        if (referenceAnchor < 0 || referenceAnchor == fillAnchor) {
            return false;
        }
        return Math.abs(row(anchor) - row(fillAnchor)) > Math.abs(row(referenceAnchor) - row(fillAnchor));
    }

    private int nearestSafeAnchor(List<Integer> path, int referenceAnchor, int fillAnchor) {
        List<Integer> candidates = shuffledCopy(scriptedAnchors);
        Integer best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int anchor : candidates) {
            if (path.contains(anchor) || anchor == fillAnchor) {
                continue;
            }
            int score = manhattanDistance(anchor, fillAnchor);
            if (referenceAnchor >= 0) {
                score += manhattanDistance(anchor, referenceAnchor) * 2;
            }
            if (score < bestScore) {
                best = anchor;
                bestScore = score;
            }
        }
        return best == null ? chooseAnchor(path) : best;
    }

    private Integer nearestSafeAnchor(List<Integer> path, int referenceAnchor, int fillAnchor, int rowWindow,
                                      int columnWindow, boolean requireReferenceWindow, boolean requireFillWindow) {
        List<Integer> candidates = shuffledCopy(scriptedAnchors);
        Integer best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int anchor : candidates) {
            if (path.contains(anchor) || anchor == fillAnchor) {
                continue;
            }
            if (requireReferenceWindow && referenceAnchor >= 0
                    && !withinWindow(anchor, referenceAnchor, rowWindow, columnWindow)) {
                continue;
            }
            if (requireFillWindow && !withinWindow(anchor, fillAnchor, rowWindow, columnWindow)) {
                continue;
            }
            int score = manhattanDistance(anchor, fillAnchor);
            if (referenceAnchor >= 0) {
                score += manhattanDistance(anchor, referenceAnchor);
            }
            if (score < bestScore) {
                best = anchor;
                bestScore = score;
            }
        }
        return best;
    }

    private int randomBestAnchor(List<Integer> anchors) {
        if (anchors == null || anchors.isEmpty()) {
            return -1;
        }
        int limit = Math.min(10, anchors.size());
        return anchors.get(random.nextInt(limit));
    }

    private boolean withinWindow(int anchor, int referenceAnchor, int rowWindow, int columnWindow) {
        return Math.abs(row(anchor) - row(referenceAnchor)) <= rowWindow
                && Math.abs(column(anchor) - column(referenceAnchor)) <= columnWindow;
    }

    private int manhattanDistance(int first, int second) {
        return rowDistance(first, second) + columnDistance(first, second);
    }

    private int rowDistance(int first, int second) {
        return Math.abs(row(first) - row(second));
    }

    private int columnDistance(int first, int second) {
        return Math.abs(column(first) - column(second));
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

    private void rememberFill(int anchor) {
        int cooldown = MIN_RECENCY_COOLDOWN_STAGES
                + random.nextInt(MAX_RECENCY_COOLDOWN_STAGES - MIN_RECENCY_COOLDOWN_STAGES + 1);
        int expiresAt = bucketActionCounter + cooldown + 1;
        recentFillPointExpiresAt.put(anchor, expiresAt);
        recentFillRowExpiresAt.put(row(anchor), expiresAt);
        bucketActionCounter++;
        expireOldFillEntries();
    }

    private void expireOldEntries() {
        recentPointExpiresAt.entrySet().removeIf(entry -> entry.getValue() <= movementStageCounter);
        recentRowExpiresAt.entrySet().removeIf(entry -> entry.getValue() <= movementStageCounter);
    }

    private void expireOldFillEntries() {
        recentFillPointExpiresAt.entrySet().removeIf(entry -> entry.getValue() <= bucketActionCounter);
        recentFillRowExpiresAt.entrySet().removeIf(entry -> entry.getValue() <= bucketActionCounter);
    }

    private boolean isRecentPoint(int anchor) {
        return recentPointExpiresAt.getOrDefault(anchor, -1) > movementStageCounter;
    }

    private boolean isRecentRow(int row) {
        return recentRowExpiresAt.getOrDefault(row, -1) > movementStageCounter;
    }

    private boolean isRecentFillPoint(int anchor) {
        return recentFillPointExpiresAt.getOrDefault(anchor, -1) > bucketActionCounter;
    }

    private boolean isRecentFillRow(int row) {
        return recentFillRowExpiresAt.getOrDefault(row, -1) > bucketActionCounter;
    }

    private int row(int anchor) {
        return CanvasMath.toY(anchor, canvasWidth);
    }

    private int column(int anchor) {
        return CanvasMath.toX(anchor, canvasWidth);
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
