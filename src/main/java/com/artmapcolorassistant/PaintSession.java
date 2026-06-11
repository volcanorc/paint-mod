package com.artmapcolorassistant;

import java.util.List;

public final class PaintSession {
    private final String filename;
    private final List<PaintStep> steps;
    private final List<ArtMapColor> availableColors;
    private int currentIndex;
    private boolean paused;
    private boolean stopped;
    private String warning;

    public PaintSession(String filename, List<PaintStep> steps, List<ArtMapColor> availableColors) {
        this.filename = filename;
        this.steps = List.copyOf(steps);
        this.availableColors = List.copyOf(availableColors);
        this.currentIndex = 0;
    }

    public String filename() {
        return filename;
    }

    public List<PaintStep> steps() {
        return steps;
    }

    public List<ArtMapColor> availableColors() {
        return availableColors;
    }

    public int currentIndex() {
        return currentIndex;
    }

    public boolean paused() {
        return paused;
    }

    public boolean stopped() {
        return stopped;
    }

    public String warning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }

    public void clearWarning() {
        this.warning = null;
    }

    public void stop() {
        stopped = true;
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
        stopped = false;
    }

    public boolean advance() {
        if (currentIndex + 1 >= steps.size()) {
            currentIndex = steps.size();
            stopped = true;
            return false;
        }
        currentIndex++;
        return true;
    }

    public boolean back() {
        if (currentIndex <= 0) {
            currentIndex = 0;
            return false;
        }
        currentIndex--;
        stopped = false;
        return true;
    }

    public boolean skip() {
        return advance();
    }

    public boolean gotoIndex(int index) {
        if (index < 0 || index >= steps.size()) {
            return false;
        }
        currentIndex = index;
        stopped = false;
        return true;
    }

    public boolean gotoXY(int x, int y, int width) {
        return gotoIndex(CanvasMath.toIndex(x, y, width));
    }

    public PaintStep currentStep() {
        if (currentIndex < 0 || currentIndex >= steps.size()) {
            return null;
        }
        return steps.get(currentIndex);
    }

    public PaintStep nextStep() {
        int next = currentIndex + 1;
        if (next < 0 || next >= steps.size()) {
            return null;
        }
        return steps.get(next);
    }

    public boolean isComplete() {
        return currentIndex >= steps.size() || stopped && currentIndex >= steps.size() - 1;
    }
}
