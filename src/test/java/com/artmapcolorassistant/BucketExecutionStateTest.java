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
        state.beginImage(FILL_ANCHORS, FILL_ANCHORS, WIDTH);

        state.beginBucketAction();

        assertEquals(BucketExecutionState.PATH_POINTS_PER_BUCKET_ACTION, new HashSet<>(state.activeAnchors()).size());
        assertTrue(maxConsecutiveRowJump(state.activeAnchors()) <= BucketExecutionState.LOCAL_LOOK_ROW_WINDOW);
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
    void sameColorBatchDoesNotReuseFillClicksForFiftyImages() {
        assertUniqueFillClicks(50, 1);
    }

    @Test
    void sameColorBatchDoesNotReuseFillClicksForOneHundredImages() {
        assertUniqueFillClicks(100, 1);
    }

    @Test
    void coalEnabledBatchDoesNotReuseFillClicksForTwentyImages() {
        assertUniqueFillClicks(20, 3);
    }

    @Test
    void longCoalBlackBatchKeepsVisibleBucketCameraSmoothAcrossPoolExhaustion() {
        BucketExecutionState state = new BucketExecutionState(new Random(50005));
        state.beginImage(FILL_ANCHORS, FILL_ANCHORS, WIDTH);
        int previousEnd = -1;

        for (int image = 0; image < 500; image++) {
            state.beginImage(FILL_ANCHORS, FILL_ANCHORS, WIDTH);
            HashSet<Integer> imageFills = new HashSet<>();
            for (int pass = 0; pass < 3; pass++) {
                state.beginBucketAction();
                assertTrue(imageFills.add(state.fillClickAnchor()));
                assertLocalBucketTransitions(previousEnd, state.activeAnchors());
                previousEnd = state.activeAnchors().getLast();
            }
        }
    }

    @Test
    void coalBlackFillClicksDoNotRepeatBeforeGlobalPoolExhaustion() {
        BucketExecutionState state = new BucketExecutionState(new Random(50005));
        state.beginImage(FILL_ANCHORS, FILL_ANCHORS, WIDTH);
        HashSet<Integer> fills = new HashSet<>();

        for (int action = 0; action < FILL_ANCHORS.size(); action++) {
            if (action % 3 == 0) {
                state.beginImage(FILL_ANCHORS, FILL_ANCHORS, WIDTH);
            }
            state.beginBucketAction();
            assertTrue(fills.add(state.fillClickAnchor()));
        }

        assertEquals(FILL_ANCHORS.size(), fills.size());
    }

    @Test
    void sameColorBatchKeepsBucketPathLocallySmoothAcrossImages() {
        BucketExecutionState state = new BucketExecutionState(new Random(12));
        state.beginImage(FILL_ANCHORS, FILL_ANCHORS, WIDTH);
        int previousEnd = -1;

        for (int image = 0; image < 100; image++) {
            state.beginImage(FILL_ANCHORS, FILL_ANCHORS, WIDTH);
            state.beginBucketAction();
            if (previousEnd >= 0) {
                assertTrue(rowDistance(previousEnd, state.activeAnchors().getFirst())
                                <= BucketExecutionState.LOCAL_LOOK_ROW_WINDOW,
                        "new image should continue near the previous bucket endpoint");
            }
            assertTrue(maxConsecutiveRowJump(state.activeAnchors()) <= BucketExecutionState.LOCAL_LOOK_ROW_WINDOW);
            previousEnd = state.activeAnchors().getLast();
        }
    }

    @Test
    void seedOneThousandRegressionDoesNotTeleportWhenFillMatchesPreviousEndpoint() {
        BucketExecutionState state = new BucketExecutionState(new Random(1000));
        state.beginImage(FILL_ANCHORS, FILL_ANCHORS, WIDTH);
        int previousEnd = -1;

        for (int action = 0; action < 100; action++) {
            state.beginImage(FILL_ANCHORS, FILL_ANCHORS, WIDTH);
            state.beginBucketAction();
            assertLocalBucketTransitions(previousEnd, state.activeAnchors());
            previousEnd = state.activeAnchors().getLast();
        }
    }

    @Test
    void fillClickStaysNearPreviousBucketEndpointWhenAlternativesExist() {
        BucketExecutionState state = new BucketExecutionState(new Random(13));
        state.beginImage(FILL_ANCHORS, FILL_ANCHORS, WIDTH);
        int previousEnd = -1;

        for (int action = 0; action < 100; action++) {
            state.beginBucketAction();
            if (previousEnd >= 0) {
                assertTrue(rowDistance(previousEnd, state.fillClickAnchor())
                                <= BucketExecutionState.LOCAL_FILL_ROW_WINDOW,
                        "fill click should not jump to a far pitch row while local unused points remain");
            }
            previousEnd = state.activeAnchors().getLast();
        }
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

    private int maxConsecutiveRowJump(List<Integer> anchors) {
        int max = 0;
        for (int i = 1; i < anchors.size(); i++) {
            max = Math.max(max, rowDistance(anchors.get(i - 1), anchors.get(i)));
        }
        return max;
    }

    private int rowDistance(int first, int second) {
        return Math.abs(CanvasMath.toY(first, WIDTH) - CanvasMath.toY(second, WIDTH));
    }

    private void assertLocalBucketTransitions(int previousEnd, List<Integer> anchors) {
        int previous = previousEnd;
        for (int anchor : anchors) {
            if (previous >= 0) {
                assertTrue(rowDistance(previous, anchor) <= BucketExecutionState.LOCAL_LOOK_ROW_WINDOW,
                        "bucket camera should not jump across distant pitch rows");
            }
            previous = anchor;
        }
    }
}
