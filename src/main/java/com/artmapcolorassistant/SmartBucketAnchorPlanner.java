package com.artmapcolorassistant;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class SmartBucketAnchorPlanner {
    static final int NATURAL_TARGET_POINTS = 30;

    private SmartBucketAnchorPlanner() {
    }

    static List<Integer> naturalCandidates(int width, int height) {
        if (width < 3 || height < 3) {
            return List.of();
        }
        Set<Integer> indexes = new LinkedHashSet<>();
        int innerMinX = width >= 8 ? 2 : 1;
        int innerMaxX = width >= 8 ? width - 3 : width - 2;
        int innerMinY = height >= 8 ? 2 : 1;
        int innerMaxY = height >= 8 ? height - 3 : height - 2;
        int rows = Math.min(15, Math.max(1, innerMaxY - innerMinY + 1));
        int cols = Math.min(Math.max(1, innerMaxX - innerMinX + 1),
                (int) Math.ceil(NATURAL_TARGET_POINTS / (double) rows));
        for (int row = 0; row < rows; row++) {
            int y = spread(innerMinY, innerMaxY, row, rows);
            for (int col = 0; col < cols; col++) {
                int x = spread(innerMinX, innerMaxX, col, cols);
                indexes.add(CanvasMath.toIndex(x, y, width));
                if (indexes.size() >= NATURAL_TARGET_POINTS) {
                    return List.copyOf(indexes);
                }
            }
        }
        for (int y = 1; y < height - 1 && indexes.size() < NATURAL_TARGET_POINTS; y++) {
            for (int x = 1; x < width - 1 && indexes.size() < NATURAL_TARGET_POINTS; x++) {
                indexes.add(CanvasMath.toIndex(x, y, width));
            }
        }
        return List.copyOf(indexes);
    }

    private static int spread(int min, int max, int index, int count) {
        if (count <= 1) {
            return (min + max) / 2;
        }
        return min + (int) Math.round((max - min) * (index / (double) (count - 1)));
    }
}
