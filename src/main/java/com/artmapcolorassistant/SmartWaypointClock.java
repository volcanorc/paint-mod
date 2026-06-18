package com.artmapcolorassistant;

final class SmartWaypointClock {
    static final int WAYPOINT_TICKS = 3;

    private int remaining;

    void reset() {
        remaining = WAYPOINT_TICKS;
    }

    boolean tick() {
        if (remaining <= 0) {
            reset();
        }
        remaining--;
        return remaining == 0;
    }

    int remaining() {
        return remaining;
    }
}
