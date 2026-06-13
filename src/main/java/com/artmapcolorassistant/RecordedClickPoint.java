package com.artmapcolorassistant;

public record RecordedClickPoint(double x, double y, double normalizedX, double normalizedY, int button) {
    public double replayX(int width) {
        return normalizedX * width;
    }

    public double replayY(int height) {
        return normalizedY * height;
    }
}
