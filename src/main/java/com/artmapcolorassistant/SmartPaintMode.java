package com.artmapcolorassistant;

import java.util.Locale;

public enum SmartPaintMode {
    AGGRESSIVE,
    SAFE;

    public static SmartPaintMode fromString(String value) {
        if (value == null) {
            return AGGRESSIVE;
        }
        try {
            return SmartPaintMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return AGGRESSIVE;
        }
    }
}
