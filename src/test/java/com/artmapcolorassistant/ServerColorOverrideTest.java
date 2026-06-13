package com.artmapcolorassistant;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerColorOverrideTest {
    private static final Identifier RED_DYE = Identifier.of("minecraft:red_dye");
    private static final Identifier APPLE = Identifier.of("minecraft:apple");
    private static final Identifier CRIMSON_NYLIUM = Identifier.of("minecraft:crimson_nylium");

    @Test
    void replacesRgbForExistingItemsAndAddsMissingItems() {
        List<ArtMapColor> base = List.of(
                color("RED", RED_DYE, 0xB02E26),
                color("APPLE_RED", APPLE, 0x8E3C2E)
        );
        List<ServerColorOverride> overrides = List.of(
                new ServerColorOverride(RED_DYE, 0xD70000),
                new ServerColorOverride(APPLE, 0x773126),
                new ServerColorOverride(CRIMSON_NYLIUM, 0x9F2829)
        );

        ConfigManager.OverrideResult result = ConfigManager.applyServerColorOverrides(base, true, overrides);

        assertEquals(3, result.applied());
        assertEquals(3, result.colors().size());
        assertEquals(0xD70000, rgb(result.colors(), RED_DYE));
        assertEquals(0x773126, rgb(result.colors(), APPLE));
        assertEquals(0x9F2829, rgb(result.colors(), CRIMSON_NYLIUM));
    }

    @Test
    void disabledOverridesLeavePaletteUnchanged() {
        List<ArtMapColor> base = List.of(color("RED", RED_DYE, 0xB02E26));
        List<ServerColorOverride> overrides = List.of(new ServerColorOverride(RED_DYE, 0xD70000));

        ConfigManager.OverrideResult result = ConfigManager.applyServerColorOverrides(base, false, overrides);

        assertEquals(0, result.applied());
        assertEquals(1, result.colors().size());
        assertEquals(0xB02E26, rgb(result.colors(), RED_DYE));
    }

    private ArtMapColor color(String name, Identifier item, int rgb) {
        return new ArtMapColor(name, name, item, null, rgb, false);
    }

    private int rgb(List<ArtMapColor> colors, Identifier item) {
        return colors.stream()
                .filter(color -> color.item().equals(item))
                .findFirst()
                .orElseThrow()
                .rgb();
    }
}
