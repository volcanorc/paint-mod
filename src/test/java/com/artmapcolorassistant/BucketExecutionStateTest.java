package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BucketExecutionStateTest {
    private static final int WIDTH = 32;
    private static final List<Integer> TOO_FEW_ANCHORS = List.of(10, 11, 12, 13, 14, 15, 16, 17, 18);
    private static final List<Integer> NATURAL_ANCHORS = SmartBucketAnchorPlanner.naturalCandidates(WIDTH, 32);

    @Test
    void fillClickCanOnlyBeMarkedOncePerImage() {
        BucketExecutionState state = new BucketExecutionState(new Random(1));
        state.beginImage(NATURAL_ANCHORS, WIDTH);
        state.beginBucketAction();

        assertTrue(state.markFillClickIfFirst());
        assertTrue(state.fillClickOccurred());
        assertFalse(state.markFillClickIfFirst());
    }

    @Test
    void newImageResetsClickGuardWithoutResettingAnchorLoop() {
        BucketExecutionState state = new BucketExecutionState(new Random(1));
        state.beginImage(NATURAL_ANCHORS, WIDTH);
        state.beginBucketAction();
        state.markFillClickIfFirst();

        state.beginImage(NATURAL_ANCHORS, WIDTH);
        state.beginBucketAction();

        assertFalse(state.fillClickOccurred());
        assertEquals(BucketExecutionState.PATH_POINTS_PER_BUCKET_ACTION, state.activeAnchors().size());
    }

    @Test
    void bucketActionUsesSevenMovementPoints() {
        BucketExecutionState state = new BucketExecutionState(new Random(2));

        state.beginImage(NATURAL_ANCHORS, WIDTH);
        state.beginBucketAction();

        assertEquals(BucketExecutionState.PATH_POINTS_PER_BUCKET_ACTION, state.activeAnchors().size());
        assertTrue(NATURAL_ANCHORS.containsAll(state.activeAnchors()));
        assertTrue(state.fillClickAnchor() >= 0);
    }

    @Test
    void bucketActionAvoidsRepeatingExactPointsAndYRowsWhenAvailable() {
        BucketExecutionState state = new BucketExecutionState(new Random(7));
        state.beginImage(NATURAL_ANCHORS, WIDTH);

        state.beginBucketAction();

        assertEquals(BucketExecutionState.PATH_POINTS_PER_BUCKET_ACTION, new HashSet<>(state.activeAnchors()).size());
        assertEquals(BucketExecutionState.PATH_POINTS_PER_BUCKET_ACTION,
                state.activeAnchors().stream()
                        .map(index -> CanvasMath.toY(index, WIDTH))
                        .collect(java.util.stream.Collectors.toSet())
                        .size());
    }

    @Test
    void bucketActionStillCompletesWhenYRowsMustRepeat() {
        List<Integer> sameFewRows = IntStream.range(0, SmartBucketAnchorPlanner.NATURAL_TARGET_POINTS)
                .map(index -> CanvasMath.toIndex(index % WIDTH, index / WIDTH % 2, WIDTH))
                .boxed()
                .toList();
        BucketExecutionState state = new BucketExecutionState(new Random(8));
        state.beginImage(sameFewRows, WIDTH);

        state.beginBucketAction();

        assertEquals(BucketExecutionState.PATH_POINTS_PER_BUCKET_ACTION, state.activeAnchors().size());
    }

    @Test
    void naturalPathDoesNotReuseFillClickForFirstThreeBucketActions() {
        BucketExecutionState state = new BucketExecutionState(new Random(3));
        state.beginImage(NATURAL_ANCHORS, WIDTH);

        state.beginBucketAction();
        int inkSacFill = state.fillClickAnchor();
        state.beginBucketAction();
        int coalOneFill = state.fillClickAnchor();
        state.beginBucketAction();
        int coalTwoFill = state.fillClickAnchor();

        assertEquals(3, new HashSet<>(List.of(inkSacFill, coalOneFill, coalTwoFill)).size());
    }

    @Test
    void postClickResumeNeverRoutesBackToFillClick() {
        BucketExecutionState state = new BucketExecutionState(new Random(1));
        state.beginImage(NATURAL_ANCHORS, WIDTH);
        state.beginBucketAction();
        state.markFillClickIfFirst();

        assertEquals(BucketExecutionState.ResumeRoute.CONTINUE_POST_FILL, state.resumeRoute(true, false));
        assertEquals(BucketExecutionState.ResumeRoute.APPLY_COMPLETED_FILL, state.resumeRoute(false, true));
        assertEquals(BucketExecutionState.ResumeRoute.BLOCKED, state.resumeRoute(false, false));
    }

    @Test
    void preClickResumeRestartsPreparationOnly() {
        BucketExecutionState state = new BucketExecutionState(new Random(1));
        state.beginImage(NATURAL_ANCHORS, WIDTH);
        state.beginBucketAction();

        assertEquals(BucketExecutionState.ResumeRoute.RESTART_BEFORE_CLICK, state.resumeRoute(false, true));
    }

    @Test
    void fewerThanThirtyAnchorsAreRejected() {
        BucketExecutionState state = new BucketExecutionState(new Random(1));

        assertThrows(IllegalArgumentException.class, () -> state.beginImage(TOO_FEW_ANCHORS, WIDTH));
    }
}
