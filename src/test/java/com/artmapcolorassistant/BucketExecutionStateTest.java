package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BucketExecutionStateTest {
    private static final List<Integer> ANCHORS = List.of(10, 11, 12, 13, 14, 15, 16, 17, 18);

    @Test
    void fillClickCanOnlyBeMarkedOncePerImage() {
        BucketExecutionState state = new BucketExecutionState();
        state.beginImage(ANCHORS);
        state.beginBucketAction();

        assertTrue(state.markFillClickIfFirst());
        assertTrue(state.fillClickOccurred());
        assertFalse(state.markFillClickIfFirst());
    }

    @Test
    void newImageResetsClickGuardWithoutResettingAnchorLoop() {
        BucketExecutionState state = new BucketExecutionState();
        state.beginImage(ANCHORS);
        state.beginBucketAction();
        state.markFillClickIfFirst();

        state.beginImage(ANCHORS);
        state.beginBucketAction();

        assertFalse(state.fillClickOccurred());
        assertEquals(List.of(13, 14, 15), state.activeAnchors());
    }

    @Test
    void scriptedAnchorsConsumeThreePerImageAndLoopAfterNine() {
        BucketExecutionState state = new BucketExecutionState();

        state.beginImage(ANCHORS);
        state.beginBucketAction();
        assertEquals(List.of(10, 11, 12), state.activeAnchors());
        state.beginBucketAction();
        assertEquals(List.of(13, 14, 15), state.activeAnchors());
        state.beginBucketAction();
        assertEquals(List.of(16, 17, 18), state.activeAnchors());
        state.beginBucketAction();
        assertEquals(List.of(10, 11, 12), state.activeAnchors());
    }

    @Test
    void postClickResumeNeverRoutesBackToFillClick() {
        BucketExecutionState state = new BucketExecutionState();
        state.beginImage(ANCHORS);
        state.beginBucketAction();
        state.markFillClickIfFirst();

        assertEquals(BucketExecutionState.ResumeRoute.CONTINUE_POST_FILL, state.resumeRoute(true, false));
        assertEquals(BucketExecutionState.ResumeRoute.APPLY_COMPLETED_FILL, state.resumeRoute(false, true));
        assertEquals(BucketExecutionState.ResumeRoute.BLOCKED, state.resumeRoute(false, false));
    }

    @Test
    void preClickResumeRestartsPreparationOnly() {
        BucketExecutionState state = new BucketExecutionState();
        state.beginImage(ANCHORS);
        state.beginBucketAction();

        assertEquals(BucketExecutionState.ResumeRoute.RESTART_BEFORE_CLICK, state.resumeRoute(false, true));
    }
}
