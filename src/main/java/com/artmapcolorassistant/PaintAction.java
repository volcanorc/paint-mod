package com.artmapcolorassistant;

import net.minecraft.util.Identifier;

import java.util.List;

public record PaintAction(
        PaintActionType type,
        ArtMapColor color,
        Identifier item,
        List<Integer> indexes,
        int seedIndex,
        int startIndex,
        int endIndex,
        int estimatedTicks,
        String reason
) {
    public int affectedCount() {
        return indexes == null ? 0 : indexes.size();
    }

    public boolean bucket() {
        return type == PaintActionType.BUCKET_BASE_COAT || type == PaintActionType.BUCKET_FILL;
    }
}
