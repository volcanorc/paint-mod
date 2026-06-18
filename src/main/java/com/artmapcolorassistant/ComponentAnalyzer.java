package com.artmapcolorassistant;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
                enqueueSameWrongTargetColor(canvas, visited, queue, color, x - 1, y);
                enqueueSameWrongTargetColor(canvas, visited, queue, color, x + 1, y);
                enqueueSameWrongTargetColor(canvas, visited, queue, color, x, y - 1);
                enqueueSameWrongTargetColor(canvas, visited, queue, color, x, y + 1);
            }
            components.add(component);
        }
        return components;
    }

    public static List<List<Integer>> targetColorComponents(SmartCanvas canvas) {
        boolean[] visited = new boolean[canvas.size()];
        ArrayList<List<Integer>> components = new ArrayList<>();
        for (int i = 0; i < canvas.size(); i++) {
            if (visited[i] || canvas.skipped(i) || canvas.targetColor(i) == null) {
                continue;
            }
            ArtMapColor color = canvas.targetColor(i);
            ArrayList<Integer> component = new ArrayList<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            visited[i] = true;
            queue.add(i);
            while (!queue.isEmpty()) {
                int index = queue.removeFirst();
                component.add(index);
                int x = CanvasMath.toX(index, canvas.width());
                int y = CanvasMath.toY(index, canvas.width());
                enqueueSameTargetColor(canvas, visited, queue, color, x - 1, y);
                enqueueSameTargetColor(canvas, visited, queue, color, x + 1, y);
                enqueueSameTargetColor(canvas, visited, queue, color, x, y - 1);
                enqueueSameTargetColor(canvas, visited, queue, color, x, y + 1);
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

    public static ShapeComponent shape(SmartCanvas canvas, List<Integer> component) {
        if (canvas == null || component == null || component.isEmpty()) {
            return new ShapeComponent(List.of(), List.of());
        }
        Set<Integer> set = new HashSet<>(component);
        ArrayList<Integer> perimeter = new ArrayList<>();
        ArrayList<Integer> interior = new ArrayList<>();
        for (int index : component) {
            int x = CanvasMath.toX(index, canvas.width());
            int y = CanvasMath.toY(index, canvas.width());
            boolean edge = x == 0 || y == 0 || x == canvas.width() - 1 || y == canvas.height() - 1;
            boolean boundary = edge
                    || !set.contains(CanvasMath.toIndex(x - 1, y, canvas.width()))
                    || !set.contains(CanvasMath.toIndex(x + 1, y, canvas.width()))
                    || !set.contains(CanvasMath.toIndex(x, y - 1, canvas.width()))
                    || !set.contains(CanvasMath.toIndex(x, y + 1, canvas.width()));
            if (boundary) {
                perimeter.add(index);
            } else {
                interior.add(index);
            }
        }
        return new ShapeComponent(List.copyOf(perimeter), List.copyOf(interior));
    }

    private static void enqueueSameWrongTargetColor(SmartCanvas canvas, boolean[] visited, ArrayDeque<Integer> queue,
                                                   ArtMapColor color, int x, int y) {
        if (x < 0 || y < 0 || x >= canvas.width() || y >= canvas.height()) {
            return;
        }
        int index = CanvasMath.toIndex(x, y, canvas.width());
        if (!visited[index]
                && canvas.wrong(index)
                && SmartCanvas.sameColor(color, canvas.targetColor(index))) {
            visited[index] = true;
            queue.add(index);
        }
    }

    private static void enqueueSameTargetColor(SmartCanvas canvas, boolean[] visited, ArrayDeque<Integer> queue,
                                               ArtMapColor color, int x, int y) {
        if (x < 0 || y < 0 || x >= canvas.width() || y >= canvas.height()) {
            return;
        }
        int index = CanvasMath.toIndex(x, y, canvas.width());
        if (!visited[index]
                && !canvas.skipped(index)
                && SmartCanvas.sameColor(color, canvas.targetColor(index))) {
            visited[index] = true;
            queue.add(index);
        }
    }

    public record ShapeComponent(List<Integer> perimeter, List<Integer> interior) {
    }
}
