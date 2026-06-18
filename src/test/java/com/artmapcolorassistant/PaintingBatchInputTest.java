package com.artmapcolorassistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaintingBatchInputTest {
    @Test
    void createsExistingBatchCommandForAutoAndSmartModes() {
        PaintingBatchInput.Validation auto = PaintingBatchInput.validate("1", "4", "Dragon", PaintingMode.AUTO);
        PaintingBatchInput.Validation smart = PaintingBatchInput.validate(" 2 ", "5", "Blue   Dragon", PaintingMode.SMART);

        assertTrue(auto.valid());
        assertEquals("#painting batch start 1 4 Dragon", auto.command());
        assertTrue(smart.valid());
        assertEquals("#painting batch start 2 5 Blue Dragon", smart.command());
    }

    @Test
    void blocksManualModeWithoutChangingIt() {
        PaintingBatchInput.Validation result = PaintingBatchInput.validate("1", "4", "Dragon", PaintingMode.MANUAL);

        assertFalse(result.valid());
        assertNull(result.command());
        assertTrue(result.error().contains("Auto or Smart"));
    }

    @Test
    void rejectsInvalidBoundsAndMissingSuffix() {
        assertFalse(PaintingBatchInput.validate("0", "4", "Dragon", PaintingMode.AUTO).valid());
        assertFalse(PaintingBatchInput.validate("5", "4", "Dragon", PaintingMode.AUTO).valid());
        assertFalse(PaintingBatchInput.validate("one", "4", "Dragon", PaintingMode.AUTO).valid());
        assertFalse(PaintingBatchInput.validate("1", "4", "   ", PaintingMode.AUTO).valid());
    }

    @Test
    void acceptsSingleImageBoundary() {
        PaintingBatchInput.Validation result = PaintingBatchInput.validate("3", "3", "Solo", PaintingMode.SMART);

        assertTrue(result.valid());
        assertEquals("#painting batch start 3 3 Solo", result.command());
    }
}
