package com.artmapcolorassistant;

public final class AutoPaintSpeed {
    public static final String TOO_FAST_MESSAGE_20 = "Auto paint speed too fast. Minimum is 20 ticks / 1 second per click.";
    public static final String TOO_FAST_MESSAGE_5 = "Auto paint speed too fast. Minimum is 5 ticks / 0.25 seconds per click.";

    private AutoPaintSpeed() {
    }

    public static int normalizeDefault(int configuredDefault, int configuredMin) {
        return Math.max(configuredDefault, configuredMin);
    }

    public static Validation validate(int ticks, int minTicks) {
        if (ticks < minTicks) {
            if (minTicks == 5) {
                return new Validation(false, TOO_FAST_MESSAGE_5);
            }
            if (minTicks == 20) {
                return new Validation(false, TOO_FAST_MESSAGE_20);
            }
            return new Validation(false, "Auto paint speed too fast. Minimum is " + minTicks + " ticks.");
        }
        return new Validation(true, "Auto paint speed set to " + ticks + " ticks (" + formatSeconds(ticks) + ").");
    }

    public static String formatSeconds(int ticks) {
        double seconds = ticks / 20.0;
        if (seconds == (long) seconds) {
            return (long) seconds + "s";
        }
        return String.format("%.2fs", seconds);
    }

    public record Validation(boolean accepted, String message) {
    }
}
