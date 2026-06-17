package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalibrationDirectionTest {
    @Test
    void mapsCardinalYawToArtMapDirection() {
        assertEquals(CalibrationDirection.NORTH, CalibrationDirection.nearest(180.0D, 45.0D).orElseThrow());
        assertEquals(CalibrationDirection.NORTH, CalibrationDirection.nearest(-180.0D, 45.0D).orElseThrow());
        assertEquals(CalibrationDirection.SOUTH, CalibrationDirection.nearest(0.0D, 45.0D).orElseThrow());
        assertEquals(CalibrationDirection.WEST, CalibrationDirection.nearest(90.0D, 45.0D).orElseThrow());
        assertEquals(CalibrationDirection.EAST, CalibrationDirection.nearest(-90.0D, 45.0D).orElseThrow());
        assertEquals(CalibrationDirection.EAST, CalibrationDirection.nearest(270.0D, 45.0D).orElseThrow());
    }

    @Test
    void rejectsExactDiagonalsAtDefaultTolerance() {
        assertTrue(CalibrationDirection.nearest(45.0D, 45.0D).isEmpty());
        assertTrue(CalibrationDirection.nearest(-45.0D, 45.0D).isEmpty());
        assertTrue(CalibrationDirection.nearest(135.0D, 45.0D).isEmpty());
        assertTrue(CalibrationDirection.nearest(-135.0D, 45.0D).isEmpty());
    }

    @Test
    void wrapsYawIntoMinecraftRange() {
        assertEquals(-178.0F, CalibrationDirection.wrapYaw(182.0D));
        assertEquals(180.0F, CalibrationDirection.wrapYaw(-180.0D));
        assertEquals(-90.0F, CalibrationDirection.wrapYaw(270.0D));
    }
}
