package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RgbUtilTest {
    @Test
    void parsesRgbHex() {
        assertEquals(0xAABBCC, RgbUtil.parseHex("#AABBCC"));
        assertEquals(0x00FF10, RgbUtil.parseHex("00ff10"));
    }

    @Test
    void rejectsInvalidRgbHex() {
        assertThrows(IllegalArgumentException.class, () -> RgbUtil.parseHex("#GGGGGG"));
        assertThrows(IllegalArgumentException.class, () -> RgbUtil.parseHex("#1234"));
    }

    @Test
    void computesSquaredDistance() {
        assertEquals(3, RgbUtil.squaredDistance(0x000000, 0x010101));
    }
}
