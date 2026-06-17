package com.artmapcolorassistant;

import java.util.Arrays;
import java.util.List;

public final class SmartCanvas {
    private final int width;
    private final int height;
    private final PaintStep[] target;
    private final ArtMapColor[] believed;
    private final ArtMapColor blankColor;
    private PaintAction plannedBaseCoat;
    private boolean trusted;
    private boolean baseCoatDone;
    private boolean baseCoatDecisionDone;

    private SmartCanvas(int width, int height, PaintStep[] target, ArtMapColor[] believed, ArtMapColor blankColor, boolean trusted, boolean baseCoatDone) {
        this.width = width;
        this.height = height;
        this.target = target;
        this.believed = believed;
        this.blankColor = blankColor;
        this.trusted = trusted;
        this.baseCoatDone = baseCoatDone;
    }

    public static SmartCanvas fresh(PaintSession session, ConfigManager.Config config) {
        PaintStep[] target = session.steps().toArray(PaintStep[]::new);
        ArtMapColor blankColor = findBlankColor(session.availableColors(), config);
        ArtMapColor[] believed = new ArtMapColor[target.length];
        Arrays.fill(believed, blankColor);
        return new SmartCanvas(config.canvasWidth(), config.canvasHeight(), target, believed, blankColor, true, false);
    }

    public SmartCanvas copy() {
        SmartCanvas copy = new SmartCanvas(width, height, target, Arrays.copyOf(believed, believed.length), blankColor, trusted, baseCoatDone);
        copy.plannedBaseCoat = plannedBaseCoat;
        copy.baseCoatDecisionDone = baseCoatDecisionDone;
        return copy;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int size() {
        return target.length;
    }

    public PaintStep targetStep(int index) {
        return target[index];
    }

    public ArtMapColor targetColor(int index) {
        PaintStep step = target[index];
        return step == null ? null : step.matchedColor();
    }

    public ArtMapColor currentColor(int index) {
        return believed[index];
    }

    public ArtMapColor blankColor() {
        return blankColor;
    }

    public boolean trusted() {
        return trusted;
    }

    public boolean baseCoatDone() {
        return baseCoatDone;
    }

    public boolean baseCoatDecisionDone() {
        return baseCoatDecisionDone;
    }

    public void markBaseCoatDecisionDone() {
        baseCoatDecisionDone = true;
    }

    public PaintAction plannedBaseCoat() {
        return plannedBaseCoat;
    }

    public void setPlannedBaseCoat(PaintAction plannedBaseCoat) {
        this.plannedBaseCoat = plannedBaseCoat;
    }

    public void invalidateTrust() {
        trusted = false;
    }

    public boolean skipped(int index) {
        PaintStep step = target[index];
        return step == null || step.skip();
    }

    public boolean wrong(int index) {
        if (skipped(index)) {
            return false;
        }
        return !sameColor(targetColor(index), believed[index]);
    }

    public int wrongCount() {
        int count = 0;
        for (int i = 0; i < size(); i++) {
            if (wrong(i)) {
                count++;
            }
        }
        return count;
    }

    public int paintableCount() {
        int count = 0;
        for (int i = 0; i < size(); i++) {
            if (!skipped(i)) {
                count++;
            }
        }
        return count;
    }

    public void apply(PaintAction action) {
        if (action == null || action.color() == null || action.indexes() == null) {
            return;
        }
        for (int index : action.indexes()) {
            if (index >= 0 && index < believed.length && !skipped(index)) {
                believed[index] = action.color();
            }
        }
        if (action.type() == PaintActionType.BUCKET_BASE_COAT) {
            baseCoatDone = true;
            plannedBaseCoat = null;
        }
        baseCoatDecisionDone = true;
    }

    public static boolean sameColor(ArtMapColor a, ArtMapColor b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.item().equals(b.item()) && a.rgb() == b.rgb();
    }

    private static ArtMapColor findBlankColor(List<ArtMapColor> colors, ConfigManager.Config config) {
        for (ArtMapColor color : colors) {
            if ("minecraft:bone_meal".equals(color.item().toString()) || "WHITE".equalsIgnoreCase(color.name())) {
                return color;
            }
        }
        for (ArtMapColor color : config.effectiveArtMapColors()) {
            if ("minecraft:bone_meal".equals(color.item().toString()) || "WHITE".equalsIgnoreCase(color.name())) {
                return color;
            }
        }
        return null;
    }
}
