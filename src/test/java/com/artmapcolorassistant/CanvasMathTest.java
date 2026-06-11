package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanvasMathTest {
    @Test
    void indexToCoordinatesForDefaultCanvas() {
        assertEquals(0, CanvasMath.toX(0, 32));
        assertEquals(0, CanvasMath.toY(0, 32));
        assertEquals(31, CanvasMath.toX(31, 32));
        assertEquals(0, CanvasMath.toY(31, 32));
        assertEquals(0, CanvasMath.toX(32, 32));
        assertEquals(1, CanvasMath.toY(32, 32));
        assertEquals(31, CanvasMath.toX(1023, 32));
        assertEquals(31, CanvasMath.toY(1023, 32));
    }

    @Test
    void coordinatesToIndexForNonDefaultCanvas() {
        assertEquals(0, CanvasMath.toIndex(0, 0, 16));
        assertEquals(15, CanvasMath.toIndex(15, 0, 16));
        assertEquals(16, CanvasMath.toIndex(0, 1, 16));
        assertEquals(255, CanvasMath.toIndex(15, 15, 16));
    }
}
