package com.artmapcolorassistant;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class FloodFill4 {
    private FloodFill4() {
    }

    public static List<Integer> fill(SmartCanvas canvas, int seedIndex) {
        if (canvas == null || seedIndex < 0 || seedIndex >= canvas.size() || canvas.skipped(seedIndex)) {
            return List.of();
        }
        ArtMapColor source = canvas.currentColor(seedIndex);
        boolean[] visited = new boolean[canvas.size()];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        ArrayList<Integer> region = new ArrayList<>();
        visited[seedIndex] = true;
        queue.add(seedIndex);
        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            if (!SmartCanvas.sameColor(source, canvas.currentColor(index))) {
                continue;
            }
            region.add(index);
            int x = CanvasMath.toX(index, canvas.width());
            int y = CanvasMath.toY(index, canvas.width());
            enqueue(canvas, visited, queue, x - 1, y);
            enqueue(canvas, visited, queue, x + 1, y);
            enqueue(canvas, visited, queue, x, y - 1);
            enqueue(canvas, visited, queue, x, y + 1);
        }
        return region;
    }

    private static void enqueue(SmartCanvas canvas, boolean[] visited, ArrayDeque<Integer> queue, int x, int y) {
        if (x < 0 || y < 0 || x >= canvas.width() || y >= canvas.height()) {
            return;
        }
        int index = CanvasMath.toIndex(x, y, canvas.width());
        if (!visited[index]) {
            visited[index] = true;
            queue.add(index);
        }
    }
}
