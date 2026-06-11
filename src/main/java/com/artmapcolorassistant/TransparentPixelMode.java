package com.artmapcolorassistant;

public enum TransparentPixelMode {
    SKIP,
    MATCH_WHITE,
    IGNORE_ALPHA;

    public static TransparentPixelMode fromString(String value) {
        if (value == null) {
            return SKIP;
        }
        for (TransparentPixelMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return SKIP;
    }
}
