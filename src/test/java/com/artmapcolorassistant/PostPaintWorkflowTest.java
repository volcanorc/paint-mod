package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostPaintWorkflowTest {
    private static final int WIDTH = 32;
    private static final int HEIGHT = 32;

    @Test
    void placementAimClampsCornerPreferredPointIntoSafeInnerCanvas() {
        List<Integer> candidates = PostPaintWorkflow.postPaintPlacementAimCandidates(WIDTH, HEIGHT,
                CanvasMath.toIndex(0, 0, WIDTH), CanvasMath.toIndex(16, 16, WIDTH));

        assertEquals(CanvasMath.toIndex(4, 4, WIDTH), candidates.getFirst());
        assertTrue(candidates.stream().allMatch(index -> PostPaintWorkflow.isSafePlacementIndex(index, WIDTH, HEIGHT)));
    }

    @Test
    void placementAimClampsNearEdgePreferredPointIntoSafeInnerCanvas() {
        List<Integer> candidates = PostPaintWorkflow.postPaintPlacementAimCandidates(WIDTH, HEIGHT,
                CanvasMath.toIndex(27, 1, WIDTH), CanvasMath.toIndex(16, 16, WIDTH));

        assertEquals(CanvasMath.toIndex(27, 4, WIDTH), candidates.getFirst());
        assertTrue(candidates.stream().allMatch(index -> PostPaintWorkflow.isSafePlacementIndex(index, WIDTH, HEIGHT)));
    }

    @Test
    void placementAimKeepsCenterPreferredPointNearItself() {
        List<Integer> candidates = PostPaintWorkflow.postPaintPlacementAimCandidates(WIDTH, HEIGHT,
                CanvasMath.toIndex(16, 16, WIDTH), CanvasMath.toIndex(0, 0, WIDTH));

        assertEquals(CanvasMath.toIndex(16, 16, WIDTH), candidates.getFirst());
        assertTrue(candidates.stream().allMatch(index -> PostPaintWorkflow.isSafePlacementIndex(index, WIDTH, HEIGHT)));
    }

    @Test
    void placementAimDoesNotUseConfiguredFallbackWhenFallbackIsOnEdge() {
        List<Integer> candidates = PostPaintWorkflow.postPaintPlacementAimCandidates(WIDTH, HEIGHT,
                -1, CanvasMath.toIndex(0, 0, WIDTH));

        assertFalse(candidates.contains(CanvasMath.toIndex(0, 0, WIDTH)));
        assertEquals(CanvasMath.toIndex(15, 15, WIDTH), candidates.getFirst());
    }

    @Test
    void saveAimStillAllowsPreferredCornerPoint() {
        List<Integer> candidates = PostPaintWorkflow.postPaintSaveAimCandidates(WIDTH, HEIGHT,
                CanvasMath.toIndex(0, 0, WIDTH), CanvasMath.toIndex(16, 16, WIDTH));

        assertEquals(CanvasMath.toIndex(0, 0, WIDTH), candidates.getFirst());
        assertTrue(candidates.contains(CanvasMath.toIndex(16, 16, WIDTH)));
    }
}
