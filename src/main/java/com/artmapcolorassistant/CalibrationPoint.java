package com.artmapcolorassistant;

import java.util.Locale;

public enum CalibrationPoint {
    TOP_LEFT("top-left"),
    TOP_RIGHT("top-right"),
    BOTTOM_LEFT("bottom-left"),
    BOTTOM_RIGHT("bottom-right");

    private final String commandName;

    CalibrationPoint(String commandName) {
        this.commandName = commandName;
    }

    public String commandName() {
        return commandName;
    }

    public static CalibrationPoint fromCommand(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (CalibrationPoint point : values()) {
            if (point.commandName.equals(normalized)) {
                return point;
            }
        }
        return null;
    }
}
