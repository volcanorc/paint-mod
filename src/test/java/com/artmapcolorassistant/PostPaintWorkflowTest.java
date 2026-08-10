package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostPaintWorkflowTest {
    private static final int WIDTH = 32;
    private static final int HEIGHT = 32;

    @Test
    void saveAimUsesCenterZoneInsteadOfCornerPreferredPoint() {
        List<Integer> candidates = PostPaintWorkflow.postPaintSaveAimCandidates(WIDTH, HEIGHT,
                CanvasMath.toIndex(0, 0, WIDTH), CanvasMath.toIndex(16, 16, WIDTH));

        assertFalse(candidates.contains(CanvasMath.toIndex(0, 0, WIDTH)));
        assertTrue(candidates.stream().allMatch(index -> PostPaintWorkflow.isCenterZoneAimIndex(index, WIDTH, HEIGHT)));
    }

    @Test
    void placementAimUsesCenterZoneInsteadOfNearEdgePreferredPoint() {
        List<Integer> candidates = PostPaintWorkflow.postPaintPlacementAimCandidates(WIDTH, HEIGHT,
                CanvasMath.toIndex(27, 1, WIDTH), CanvasMath.toIndex(16, 16, WIDTH));

        assertFalse(candidates.contains(CanvasMath.toIndex(27, 1, WIDTH)));
        assertTrue(candidates.stream().allMatch(index -> PostPaintWorkflow.isCenterZoneAimIndex(index, WIDTH, HEIGHT)));
    }

    @Test
    void centerPreferredPointStaysInsideCenterZone() {
        List<Integer> candidates = PostPaintWorkflow.postPaintPlacementAimCandidates(WIDTH, HEIGHT,
                CanvasMath.toIndex(16, 16, WIDTH), CanvasMath.toIndex(0, 0, WIDTH));

        assertTrue(candidates.contains(CanvasMath.toIndex(16, 16, WIDTH)));
        assertTrue(candidates.stream().allMatch(index -> PostPaintWorkflow.isCenterZoneAimIndex(index, WIDTH, HEIGHT)));
    }

    @Test
    void placementAimDoesNotUseConfiguredFallbackWhenFallbackIsOnEdge() {
        List<Integer> candidates = PostPaintWorkflow.postPaintPlacementAimCandidates(WIDTH, HEIGHT,
                -1, CanvasMath.toIndex(0, 0, WIDTH));

        assertFalse(candidates.contains(CanvasMath.toIndex(0, 0, WIDTH)));
        assertTrue(candidates.stream().allMatch(index -> PostPaintWorkflow.isCenterZoneAimIndex(index, WIDTH, HEIGHT)));
    }

    @Test
    void centerAimCandidatesAreFourCenterPixelsPlusRadiusSixOnly() {
        List<Integer> candidates = PostPaintWorkflow.postPaintCenterAimCandidates(WIDTH, HEIGHT);

        assertTrue(candidates.contains(CanvasMath.toIndex(16, 16, WIDTH)));
        assertTrue(candidates.contains(CanvasMath.toIndex(15, 15, WIDTH)));
        assertTrue(candidates.contains(CanvasMath.toIndex(9, 15, WIDTH)));
        assertFalse(candidates.contains(CanvasMath.toIndex(8, 15, WIDTH)));
        assertFalse(candidates.contains(CanvasMath.toIndex(0, 0, WIDTH)));
        assertTrue(candidates.stream().allMatch(index -> PostPaintWorkflow.isSafePlacementIndex(index, WIDTH, HEIGHT)));
    }

    @Test
    void centerTargetPoolAvoidsRepeatsUntilExhausted() {
        PostPaintWorkflow.CenterAimTargetPool pool = new PostPaintWorkflow.CenterAimTargetPool(new Random(1));
        List<Integer> candidates = PostPaintWorkflow.postPaintCenterAimCandidates(WIDTH, HEIGHT);
        Set<Integer> used = new HashSet<>();

        for (int i = 0; i < candidates.size(); i++) {
            int target = pool.next(WIDTH, HEIGHT);
            assertTrue(used.add(target), "center target repeated before pool exhaustion: " + target);
            assertTrue(PostPaintWorkflow.isCenterZoneAimIndex(target, WIDTH, HEIGHT));
        }
        assertEquals(0, pool.remainingForTesting());
    }

    @Test
    void centerTargetPoolCanResetForNewBatch() {
        PostPaintWorkflow.CenterAimTargetPool pool = new PostPaintWorkflow.CenterAimTargetPool(new Random(2));
        List<Integer> candidates = PostPaintWorkflow.postPaintCenterAimCandidates(WIDTH, HEIGHT);

        pool.next(WIDTH, HEIGHT);
        assertEquals(candidates.size() - 1, pool.remainingForTesting());
        pool.reset();
        pool.next(WIDTH, HEIGHT);

        assertEquals(candidates.size() - 1, pool.remainingForTesting());
    }

    @Test
    void centerWalkPathMovesLocallyFromCornerToCenterTarget() {
        int start = CanvasMath.toIndex(0, 0, WIDTH);
        int target = CanvasMath.toIndex(16, 16, WIDTH);
        List<Integer> path = PostPaintWorkflow.postPaintCenterWalkPath(WIDTH, HEIGHT, start, target);

        assertFalse(path.isEmpty());
        assertEquals(target, path.getLast());
        for (int i = 1; i < path.size(); i++) {
            int previous = path.get(i - 1);
            int current = path.get(i);
            int dx = Math.abs(CanvasMath.toX(previous, WIDTH) - CanvasMath.toX(current, WIDTH));
            int dy = Math.abs(CanvasMath.toY(previous, WIDTH) - CanvasMath.toY(current, WIDTH));
            assertTrue(dx <= 3, "post-paint center walk jumped too far horizontally: " + dx);
            assertTrue(dy <= 3, "post-paint center walk jumped too far vertically: " + dy);
        }
        assertTrue(path.stream().allMatch(index -> index >= 0 && index < WIDTH * HEIGHT));
    }

    @Test
    void centerWalkPathFromEdgeDoesNotUseEdgeAsFinalClickTarget() {
        int start = CanvasMath.toIndex(31, 1, WIDTH);
        int target = PostPaintWorkflow.postPaintCenterAimCandidates(WIDTH, HEIGHT).getFirst();
        List<Integer> path = PostPaintWorkflow.postPaintCenterWalkPath(WIDTH, HEIGHT, start, target);

        assertEquals(target, path.getLast());
        assertTrue(PostPaintWorkflow.isCenterZoneAimIndex(path.getLast(), WIDTH, HEIGHT));
        assertFalse(PostPaintWorkflow.isCenterZoneAimIndex(start, WIDTH, HEIGHT));
    }

    @Test
    void startAimPrefersLastPaintedThenConfiguredThenTarget() {
        int preferred = CanvasMath.toIndex(31, 31, WIDTH);
        int configured = CanvasMath.toIndex(0, 0, WIDTH);
        int target = CanvasMath.toIndex(15, 15, WIDTH);

        assertEquals(preferred, PostPaintWorkflow.postPaintStartAimIndex(WIDTH, HEIGHT, preferred, configured, target));
        assertEquals(configured, PostPaintWorkflow.postPaintStartAimIndex(WIDTH, HEIGHT, -1, configured, target));
        assertEquals(target, PostPaintWorkflow.postPaintStartAimIndex(WIDTH, HEIGHT, -1, -1, target));
    }
}
