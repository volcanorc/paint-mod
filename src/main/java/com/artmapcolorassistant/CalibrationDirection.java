package com.artmapcolorassistant;

import java.util.Locale;
import java.util.Optional;

public enum CalibrationDirection {
    NORTH("north", 180.0D),
    SOUTH("south", 0.0D),
    WEST("west", 90.0D),
    EAST("east", -90.0D);

    private final String resourceName;
    private final double yaw;

    CalibrationDirection(String resourceName, double yaw) {
        this.resourceName = resourceName;
        this.yaw = yaw;
    }

    public String resourceName() {
        return resourceName;
    }

    public double yaw() {
        return yaw;
    }

    public static Optional<CalibrationDirection> nearest(double yaw, double toleranceDegrees) {
        double tolerance = Math.max(0.0D, toleranceDegrees);
        CalibrationDirection best = null;
        double bestDistance = Double.MAX_VALUE;
        for (CalibrationDirection direction : values()) {
            double distance = angularDistance(yaw, direction.yaw);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = direction;
            }
        }
        return best != null && bestDistance < tolerance ? Optional.of(best) : Optional.empty();
    }

    public static float wrapYaw(double yaw) {
        double wrapped = yaw % 360.0D;
        if (wrapped <= -180.0D) {
            wrapped += 360.0D;
        }
        if (wrapped > 180.0D) {
            wrapped -= 360.0D;
        }
        return (float) wrapped;
    }

    private static double angularDistance(double a, double b) {
        return Math.abs(wrapYaw(a - b));
    }

    public static CalibrationDirection fromResourceName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (CalibrationDirection direction : values()) {
            if (direction.resourceName.equals(normalized)) {
                return direction;
            }
        }
        return null;
    }
}
