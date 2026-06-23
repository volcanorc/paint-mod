package com.artmapcolorassistant;

import net.minecraft.util.Identifier;

import java.util.List;

public final class DeepBlackAnalyzer {
    public static final Identifier INK_SAC = Identifier.of("minecraft:ink_sac");
    public static final Identifier COAL = Identifier.of("minecraft:coal");

    private DeepBlackAnalyzer() {
    }

    public static Analysis analyze(SmartCanvas canvas, ConfigManager.Config config) {
        if (canvas == null || !config.smartCoalBlackBasecoatEnabled()) {
            return Analysis.disabled();
        }
        int paintable = 0;
        int deepBlack = 0;
        for (int i = 0; i < canvas.size(); i++) {
            if (canvas.skipped(i)) {
                continue;
            }
            paintable++;
            PaintStep step = canvas.targetStep(i);
            if (step != null && isDeepBlack(step.originalArgb())) {
                deepBlack++;
            }
        }
        boolean active = paintable > 0 && ((double) deepBlack / (double) paintable) >= config.smartCoalBlackDominanceThreshold();
        return new Analysis(active, deepBlack, paintable, config.smartCoalBlackPasses());
    }

    public static boolean isDeepBlack(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha == 0) {
            return false;
        }
        int rgb = argb & 0xFFFFFF;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        if (max > 36) {
            return false;
        }
        if (max == 0) {
            return true;
        }
        double saturation = (double) (max - min) / (double) max;
        return saturation <= 0.35D;
    }

    public static ArtMapColor findInkSac(ConfigManager.Config config) {
        return findColor(config.effectiveArtMapColors(), INK_SAC);
    }

    public static ArtMapColor findCoal(ConfigManager.Config config) {
        return findColor(config.effectiveArtMapColors(), COAL);
    }

    private static ArtMapColor findColor(List<ArtMapColor> colors, Identifier item) {
        for (ArtMapColor color : colors) {
            if (color.item().equals(item)) {
                return color;
            }
        }
        return null;
    }

    public record Analysis(boolean active, int deepBlackPixels, int paintablePixels, int coalPasses) {
        static Analysis disabled() {
            return new Analysis(false, 0, 0, 0);
        }
    }
}
