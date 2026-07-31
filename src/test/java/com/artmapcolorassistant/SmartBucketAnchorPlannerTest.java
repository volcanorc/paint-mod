package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartBucketAnchorPlannerTest {
    @Test
    void naturalCandidatesBuildThirtyInnerCanvasPoints() {
        List<Integer> anchors = SmartBucketAnchorPlanner.naturalCandidates(32, 32);

        assertEquals(30, anchors.size());
        assertEquals(30, new HashSet<>(anchors).size());
        assertTrue(anchors.stream()
                .map(index -> CanvasMath.toY(index, 32))
                .collect(java.util.stream.Collectors.toSet())
                .size() >= 10);
        for (int index : anchors) {
            int x = CanvasMath.toX(index, 32);
            int y = CanvasMath.toY(index, 32);
            assertTrue(x >= 2 && x <= 29);
            assertTrue(y >= 2 && y <= 29);
        }
    }

    @Test
    void tinyCanvasCannotProduceNaturalBucketPath() {
        assertTrue(SmartBucketAnchorPlanner.naturalCandidates(3, 3).size()
                < SmartBucketAnchorPlanner.NATURAL_TARGET_POINTS);
    }

    @Test
    void fillClickCandidatesBuildLargeInnerCanvasPool() {
        List<Integer> anchors = SmartBucketAnchorPlanner.fillClickCandidates(32, 32);

        assertEquals(784, anchors.size());
        assertEquals(784, new HashSet<>(anchors).size());
        assertTrue(anchors.size() > SmartBucketAnchorPlanner.NATURAL_TARGET_POINTS);
        for (int index : anchors) {
            int x = CanvasMath.toX(index, 32);
            int y = CanvasMath.toY(index, 32);
            assertTrue(x >= 2 && x <= 29);
            assertTrue(y >= 2 && y <= 29);
        }
    }
}
