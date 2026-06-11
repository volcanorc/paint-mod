package com.artmapcolorassistant;

public final class CanvasMath {
    private CanvasMath() {
    }

    public static int toIndex(int x, int y, int width) {
        return y * width + x;
    }

    public static int toX(int index, int width) {
        return index % width;
    }

    public static int toY(int index, int width) {
        return index / width;
    }
}
