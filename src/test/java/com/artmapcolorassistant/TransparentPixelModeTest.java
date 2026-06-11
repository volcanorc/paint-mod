package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransparentPixelModeTest {
    @Test
    void parsesKnownModesAndDefaultsToSkip() {
        assertEquals(TransparentPixelMode.MATCH_WHITE, TransparentPixelMode.fromString("match_white"));
        assertEquals(TransparentPixelMode.IGNORE_ALPHA, TransparentPixelMode.fromString("IGNORE_ALPHA"));
        assertEquals(TransparentPixelMode.SKIP, TransparentPixelMode.fromString("bad"));
        assertEquals(TransparentPixelMode.SKIP, TransparentPixelMode.fromString(null));
    }
}
