package com.artmapcolorassistant;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorMatchModeTest {
    @Test
    void defaultsInvalidMatchModeToRgb() {
        assertEquals(ColorMatchMode.RGB, ColorMatchMode.fromString(null));
        assertEquals(ColorMatchMode.RGB, ColorMatchMode.fromString("unknown"));
        assertEquals(ColorMatchMode.PERCEPTUAL, ColorMatchMode.fromString("perceptual"));
    }

    @Test
    void rgbNearestPreservesExistingDistanceBehavior() {
        ColorMatcher matcher = new ColorMatcher();
        ArtMapColor red = color("RED", "minecraft:red_dye", 0xB02E26);
        ArtMapColor maroon = color("MAROON", "minecraft:nether_wart", 0x700200);

        assertEquals(red, matcher.nearest(0xB00020, List.of(red, maroon), ColorMatchMode.RGB));
    }

    @Test
    void perceptualDistanceIsAvailableForRanking() {
        ColorMatcher matcher = new ColorMatcher();
        ArtMapColor red = color("RED", "minecraft:red_dye", 0xB02E26);
        ArtMapColor maroon = color("MAROON", "minecraft:nether_wart", 0x700200);

        List<ColorMatcher.ColorDistance> ranked = matcher.nearestColors(0x7A0808, List.of(red, maroon), ColorMatchMode.PERCEPTUAL, 2);

        assertEquals(2, ranked.size());
        assertTrue(ranked.get(0).distance() <= ranked.get(1).distance());
    }

    @Test
    void redClassifierIncludesPinkAndMaroonButNotGreen() {
        assertTrue(RgbUtil.isReddish(0xB02E26));
        assertTrue(RgbUtil.isReddish(0xF38BAA));
        assertTrue(RgbUtil.isReddish(0x700200));
        assertTrue(!RgbUtil.isReddish(0x5E7C16));
    }

    private ArtMapColor color(String name, String item, int rgb) {
        return new ArtMapColor(name, name, Identifier.of(item), null, rgb, false);
    }
}
