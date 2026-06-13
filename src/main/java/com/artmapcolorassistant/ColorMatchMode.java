package com.artmapcolorassistant;

import java.util.Locale;

public enum ColorMatchMode {
    RGB,
    PERCEPTUAL;

    public static ColorMatchMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return RGB;
        }
        try {
            return ColorMatchMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RGB;
        }
    }
}
