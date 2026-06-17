package com.artmapcolorassistant;

import java.util.Locale;

public enum PaintingMode {
    MANUAL,
    AUTO,
    SMART;

    public static PaintingMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return SMART;
        }
        try {
            return PaintingMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SMART;
        }
    }

    public String commandName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean allowsBatch() {
        return this != MANUAL;
    }

    public boolean allowsBucketConfig() {
        return this == SMART;
    }

    public boolean allowsAutoDragConfig() {
        return this != MANUAL;
    }
}
