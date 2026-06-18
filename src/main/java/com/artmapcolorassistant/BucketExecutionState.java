package com.artmapcolorassistant;

import java.util.List;

final class BucketExecutionState {
    enum ResumeRoute {
        RESTART_BEFORE_CLICK,
        CONTINUE_POST_FILL,
        APPLY_COMPLETED_FILL,
        BLOCKED
    }

    private boolean fillClickOccurred;
    private int anchorCursor;
    private List<Integer> activeAnchors = List.of();

    void beginImage(List<Integer> scriptedAnchors) {
        if (scriptedAnchors == null || scriptedAnchors.size() != 9) {
            throw new IllegalArgumentException("Smart bucket requires exactly nine scripted anchors.");
        }
        activeAnchors = List.of(
                scriptedAnchors.get(anchorCursor),
                scriptedAnchors.get((anchorCursor + 1) % scriptedAnchors.size()),
                scriptedAnchors.get((anchorCursor + 2) % scriptedAnchors.size())
        );
        anchorCursor = (anchorCursor + 3) % scriptedAnchors.size();
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
}
