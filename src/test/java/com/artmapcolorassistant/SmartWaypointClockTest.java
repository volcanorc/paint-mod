package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartWaypointClockTest {
    @Test
    void advancesOnExactlyTheThirdTick() {
        SmartWaypointClock clock = new SmartWaypointClock();
        clock.reset();

        assertFalse(clock.tick());
        assertFalse(clock.tick());
        assertTrue(clock.tick());
        assertEquals(0, clock.remaining());
    }

    @Test
    void consecutiveWaypointsKeepTheThreeTickCadence() {
        SmartWaypointClock clock = new SmartWaypointClock();

        for (int waypoint = 0; waypoint < 4; waypoint++) {
            clock.reset();
            assertFalse(clock.tick());
            assertFalse(clock.tick());
            assertTrue(clock.tick());
        }
    }

    @Test
    void classicAutoConfigurationRemainsIndependent() {
        ConfigManager.Config config = ConfigManager.Config.defaults();

        assertEquals(3, SmartWaypointClock.WAYPOINT_TICKS);
        assertEquals(5, config.autoDragPixelTicks());
    }
}
