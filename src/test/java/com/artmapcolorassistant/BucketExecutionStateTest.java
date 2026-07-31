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
    private static final List<Integer> FILL_ANCHORS = SmartBucketAnchorPlanner.fillClickCandidates(WIDTH, 32);

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
        int lastAnchor = state.activeAnchors().getLast();
        int lastRow = CanvasMath.toY(lastAnchor, WIDTH);

        state.beginImage(NATURAL_ANCHORS, WIDTH);
        assertFalse(state.fillClickOccurred());
        assertEquals(BucketExecutionState.PATH_POINTS_PER_BUCKET_ACTION, state.movementStageCounterForTesting());
        assertTrue(state.recentPointForTesting(lastAnchor));
        assertTrue(state.recentRowForTesting(lastRow));

        state.beginBucketAction();

        assertEquals(BucketExecutionState.PATH_POINTS_PER_BUCKET_ACTION, state.activeAnchors().size());
    }

    @Test
    void changedBucketContextClearsCrossImageRecencyMemory() {
        BucketExecutionState state = new BucketExecutionState(new Random(1));
        state.beginImage(NATURAL_ANCHORS, WIDTH);
        state.beginBucketAction();
        int lastAnchor = state.activeAnchors().getLast();

        state.beginImage(NATURAL_ANCHORS, WIDTH + 1);

        assertEquals(0, state.movementStageCounterForTesting());
        assertFalse(state.recentPointForTesting(lastAnchor));
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
        state.beginImage(NATURAL_ANCHORS, FILL_ANCHORS, WIDTH);

        state.beginBucketAction();
        int inkSacFill = state.fillClickAnchor();
        state.beginBucketAction();
        int coalOneFill = state.fillClickAnchor();
        state.beginBucketAction();
        int coalTwoFill = state.fillClickAnchor();

        assertEquals(3, new HashSet<>(List.of(inkSacFill, coalOneFill, coalTwoFill)).size());
    }

    @Test
    void whiteMajorityBatchDoesNotReuseFillClicksForTwentyImages() {
        assertUniqueFillClicks(20, 1);
    }

    @Test
    void blackMajorityWithCoalOffDoesNotReuseInkSacFillClicksForTwentyImages() {
        assertUniqueFillClicks(20, 1);
    }

    @Test
    void coloredMajorityBatchDoesNotReuseBasecoatFillClicksForTwentyImages() {
        assertUniqueFillClicks(20, 1);
    }

    @Test
    void coalEnabledBatchDoesNotReuseFillClicksForTwentyImages() {
        assertUniqueFillClicks(20, 3);
    }

    @Test
    void fillClicksRepeatOnlyAfterFillPoolExhaustion() {
        List<Integer> tinyFillPool = FILL_ANCHORS.subList(0, 5);
        BucketExecutionState state = new BucketExecutionState(new Random(4));
        state.beginImage(NATURAL_ANCHORS, tinyFillPool, WIDTH);
        HashSet<Integer> firstCycle = new HashSet<>();

        for (int i = 0; i < tinyFillPool.size(); i++) {
            state.beginBucketAction();
            assertTrue(firstCycle.add(state.fillClickAnchor()));
        }

        state.beginBucketAction();

        assertTrue(firstCycle.contains(state.fillClickAnchor()));
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

    private void assertUniqueFillClicks(int images, int bucketActionsPerImage) {
        BucketExecutionState state = new BucketExecutionState(new Random(11));
        state.beginImage(NATURAL_ANCHORS, FILL_ANCHORS, WIDTH);
        HashSet<Integer> fills = new HashSet<>();
        for (int image = 0; image < images; image++) {
            state.beginImage(NATURAL_ANCHORS, FILL_ANCHORS, WIDTH);
            for (int action = 0; action < bucketActionsPerImage; action++) {
                state.beginBucketAction();
                assertTrue(fills.add(state.fillClickAnchor()));
            }
        }
        assertEquals(images * bucketActionsPerImage, fills.size());
        assertEquals(FILL_ANCHORS.size() - fills.size(), state.remainingFillClickAnchorsForTesting());
    }
}
