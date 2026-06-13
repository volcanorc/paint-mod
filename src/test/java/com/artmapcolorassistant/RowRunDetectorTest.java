package com.artmapcolorassistant;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RowRunDetectorTest {
    private static final Identifier RED = Identifier.of("minecraft:red_dye");
    private static final Identifier BLUE = Identifier.of("minecraft:blue_dye");

    @Test
    void detectsSameColorRunWithinRow() {
        List<PaintStep> steps = row(32, 23, RED, BLUE);

        assertEquals(23, RowRunDetector.sameItemRowRun(steps, 0, step -> true));
    }

    @Test
    void stopsAtRowBoundary() {
        List<PaintStep> steps = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            steps.add(step(i, i % 32, i / 32, RED, false));
        }

        assertEquals(32, RowRunDetector.sameItemRowRun(steps, 0, step -> true));
    }

    @Test
    void stopsOnColorChange() {
        List<PaintStep> steps = row(32, 5, RED, BLUE);

        assertEquals(5, RowRunDetector.sameItemRowRun(steps, 0, step -> true));
    }

    @Test
    void stopsOnTransparentSkip() {
        List<PaintStep> steps = row(32, 32, RED, RED);
        steps.set(3, step(3, 3, 0, null, true));

        assertEquals(3, RowRunDetector.sameItemRowRun(steps, 0, step -> true));
    }

    @Test
    void stopsWhenCalibrationIsMissing() {
        List<PaintStep> steps = row(32, 10, RED, BLUE);
        Set<Integer> calibrated = Set.of(0, 1, 2, 3);

        assertEquals(4, RowRunDetector.sameItemRowRun(steps, 0, step -> calibrated.contains(step.index())));
    }

    private List<PaintStep> row(int width, int firstColorLength, Identifier first, Identifier second) {
        List<PaintStep> steps = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            steps.add(step(x, x, 0, x < firstColorLength ? first : second, false));
        }
        return steps;
    }

    private PaintStep step(int index, int x, int y, Identifier item, boolean transparent) {
        return new PaintStep(index, x, y, 0xFFFFFFFF, transparent, null, item);
    }
}
