package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoPaintSpeedTest {
    @Test
    void defaultIsTwentyTicksAndMinimumIsFiveTicks() {
        ConfigManager.Config config = ConfigManager.Config.defaults();
        assertEquals(20, config.autoPaintDefaultDelayTicks());
        assertEquals(5, config.autoPaintMinDelayTicks());
        assertEquals(20, AutoPaintSpeed.normalizeDefault(config.autoPaintDefaultDelayTicks(), config.autoPaintMinDelayTicks()));
    }

    @Test
    void rejectsSpeedsBelowMinimumWithRequiredMessage() {
        AutoPaintSpeed.Validation validation = AutoPaintSpeed.validate(4, 5);
        assertFalse(validation.accepted());
        assertEquals("Auto paint speed too fast. Minimum is 5 ticks / 0.25 seconds per click.", validation.message());
    }

    @Test
    void acceptsQuarterOneAndTwoSecondSpeeds() {
        assertTrue(AutoPaintSpeed.validate(5, 5).accepted());
        assertEquals("Auto paint speed set to 5 ticks (0.25s).", AutoPaintSpeed.validate(5, 5).message());
        assertTrue(AutoPaintSpeed.validate(20, 5).accepted());
        assertEquals("Auto paint speed set to 20 ticks (1s).", AutoPaintSpeed.validate(20, 5).message());
        assertTrue(AutoPaintSpeed.validate(40, 5).accepted());
        assertEquals("Auto paint speed set to 40 ticks (2s).", AutoPaintSpeed.validate(40, 5).message());
    }
}
