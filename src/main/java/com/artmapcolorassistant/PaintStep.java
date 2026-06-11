package com.artmapcolorassistant;

import net.minecraft.util.Identifier;

public record PaintStep(
        int index,
        int x,
        int y,
        int originalArgb,
        boolean transparent,
        ArtMapColor matchedColor,
        Identifier item
) {
    public boolean skip() {
        return transparent && matchedColor == null;
    }
}
