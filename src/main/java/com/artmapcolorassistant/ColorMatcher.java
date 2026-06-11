package com.artmapcolorassistant;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ColorMatcher {
    public List<ArtMapColor> buildMatchingPalette(ConfigManager.Config config, InventoryHelper.InventorySnapshot inventory) {
        Set<Identifier> available = inventory.availableItemIds();
        List<ArtMapColor> result = new ArrayList<>();
        for (ArtMapColor color : config.artMapColors()) {
            if (color.tool() && !config.includeToolsInColorMatching()) {
                continue;
            }
            if (config.useOnlyInventoryAvailableColors() && !containsColorItem(color, available)) {
                continue;
            }
            result.add(color);
        }
        return result;
    }

    public List<PaintStep> convert(ImageLoader.LoadedImage image, ConfigManager.Config config, List<ArtMapColor> palette) throws MatchException {
        if (palette.isEmpty()) {
            throw new MatchException("No usable ArtMap color items were found in hotbar/main inventory.");
        }
        List<PaintStep> steps = new ArrayList<>(image.argb().length);
        for (int index = 0; index < image.argb().length; index++) {
            int argb = image.argb()[index];
            int alpha = (argb >>> 24) & 0xFF;
            int x = CanvasMath.toX(index, image.width());
            int y = CanvasMath.toY(index, image.width());
            if (alpha <= config.alphaThreshold() && config.transparentPixelMode() == TransparentPixelMode.SKIP) {
                steps.add(new PaintStep(index, x, y, argb, true, null, null));
                continue;
            }
            int rgb = argb & 0xFFFFFF;
            if (alpha <= config.alphaThreshold() && config.transparentPixelMode() == TransparentPixelMode.MATCH_WHITE) {
                rgb = 0xFFFFFF;
            }
            ArtMapColor color = nearest(rgb, palette);
            steps.add(new PaintStep(index, x, y, argb, alpha <= config.alphaThreshold(), color, color.item()));
        }
        return steps;
    }

    public Map<ArtMapColor, Long> counts(List<PaintStep> steps) {
        Map<ArtMapColor, Long> counts = new HashMap<>();
        for (PaintStep step : steps) {
            if (step.matchedColor() != null) {
                counts.put(step.matchedColor(), counts.getOrDefault(step.matchedColor(), 0L) + 1L);
            }
        }
        return counts;
    }

    public ArtMapColor nearest(int rgb, List<ArtMapColor> palette) {
        return palette.stream()
                .min(Comparator.comparingInt(color -> RgbUtil.squaredDistance(rgb, color.rgb())))
                .orElseThrow();
    }

    private boolean containsColorItem(ArtMapColor color, Set<Identifier> available) {
        return available.contains(color.item()) || (color.legacyItem() != null && available.contains(color.legacyItem()));
    }

    public Set<ArtMapColor> detectedTools(ConfigManager.Config config, InventoryHelper.InventorySnapshot inventory) {
        Set<ArtMapColor> tools = new HashSet<>();
        for (ArtMapColor color : config.artMapColors()) {
            if (color.tool() && containsColorItem(color, inventory.availableItemIds())) {
                tools.add(color);
            }
        }
        return tools;
    }

    public static final class MatchException extends Exception {
        public MatchException(String message) {
            super(message);
        }
    }
}
