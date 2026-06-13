package com.artmapcolorassistant;

public enum AutoClickButton {
    RIGHT,
    LEFT;

    public static AutoClickButton fromString(String value) {
        if (value == null) {
            return RIGHT;
        }
        for (AutoClickButton button : values()) {
            if (button.name().equalsIgnoreCase(value)) {
                return button;
            }
        }
        return RIGHT;
    }
}
