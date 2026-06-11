package com.artmapcolorassistant;

import net.minecraft.util.Identifier;

public record ArtMapColor(
        String name,
        String bukkitMaterial,
        Identifier item,
        Identifier legacyItem,
        int rgb,
        boolean tool
) {
    public boolean matches(Identifier id) {
        return item.equals(id) || (legacyItem != null && legacyItem.equals(id));
    }
}
