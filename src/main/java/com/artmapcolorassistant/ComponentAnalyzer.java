package com.artmapcolorassistant;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class ComponentAnalyzer {
    private ComponentAnalyzer() {
    }

    public static List<List<Integer>> wrongPixelComponents(SmartCanvas canvas) {
        boolean[] visited = new boolean[canvas.size()];
        ArrayList<List<Integer>> components = new ArrayList<>();
        for (int i = 0; i < canvas.size(); i++) {
            if (visited[i] || !canvas.wrong(i)) {
                continue;
            }
            ArtMapColor color = canvas.targetColor(i);
            ArrayList<Integer> component = new ArrayList<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            visited[i] = true;
            queue.add(i);
            while (!queue.isEmpty()) {
                int index = queue.removeFirst();
                if (!canvas.wrong(index) || !SmartCanvas.sameColor(color, canvas.targetColor(index))) {
                    continue;
                }
                component.add(index);
                int x = CanvasMath.toX(index, canvas.width());
                int y = CanvasMath.toY(index, canvas.width());
                enqueue(canvas, visited, queue, x - 1, y);
                enqueue(canvas, visited, queue, x + 1, y);
                enqueue(canvas, visited, queue, x, y - 1);
                enqueue(canvas, visited, queue, x, y + 1);
            }
            components.add(component);
        }
        return components;
    }

    public static List<Integer> horizontalRun(SmartCanvas canvas, int startIndex, int minLength) {
        if (!canvas.wrong(startIndex)) {
            return List.of();
        }
        ArtMapColor color = canvas.targetColor(startIndex);
        int y = CanvasMath.toY(startIndex, canvas.width());
        int x = CanvasMath.toX(startIndex, canvas.width());
        while (x > 0) {
            int previous = CanvasMath.toIndex(x - 1, y, canvas.width());
            if (!canvas.wrong(previous) || !SmartCanvas.sameColor(color, canvas.targetColor(previous))) {
                break;
            }
            x--;
        }
        ArrayList<Integer> run = new ArrayList<>();
        while (x < canvas.width()) {
            int index = CanvasMath.toIndex(x, y, canvas.width());
            if (!canvas.wrong(index) || !SmartCanvas.sameColor(color, canvas.targetColor(index))) {
                break;
            }
            run.add(index);
            x++;
        }
        return run.size() >= minLength ? run : List.of();
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
