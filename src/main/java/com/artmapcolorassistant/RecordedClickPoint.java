package com.artmapcolorassistant;

public record RecordedClickPoint(double x, double y, double normalizedX, double normalizedY, int button, String source) {
    public RecordedClickPoint(double x, double y, double normalizedX, double normalizedY, int button) {
        this(x, y, normalizedX, normalizedY, button, "cursor");
    }

    public RecordedClickPoint {
        source = source == null || source.isBlank() ? "cursor" : source;
    }

    public double replayX(int width) {
        return normalizedX * width;
    }

    public double replayY(int height) {
        return normalizedY * height;
    }
}
